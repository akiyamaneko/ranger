package com.flipkart.ranger.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.ranger.ServiceProviderBuilders;
import com.flipkart.ranger.ServiceFinderBuilders;
import com.flipkart.ranger.finder.sharded.SimpleShardedServiceFinder;
import com.flipkart.ranger.healthcheck.Healthcheck;
import com.flipkart.ranger.healthcheck.HealthcheckStatus;
import com.flipkart.ranger.serviceprovider.ServiceProvider;
import org.apache.curator.test.TestingCluster;
import org.junit.*;
import org.junit.Test;

import java.io.IOException;

public class ServiceProviderTest {

    private TestingCluster testingCluster;
    private ObjectMapper objectMapper;

    @Before
    public void startTestCluster() throws Exception {
        objectMapper = new ObjectMapper();
        testingCluster = new TestingCluster(3);
        testingCluster.start();
        registerService("localhost-1", 9000, 1);
        registerService("localhost-2", 9000, 1);
        registerService("localhost-3", 9000, 2);
    }

    @After
    public void stopTestCluster() throws Exception {
        if(null != testingCluster) {
            testingCluster.close();
        }
    }

    private static final class TestShardInfo {
        private int shardId;

        public TestShardInfo(int shardId) {
            this.shardId = shardId;
        }

        public TestShardInfo() {
        }

        public int getShardId() {
            return shardId;
        }

        public void setShardId(int shardId) {
            this.shardId = shardId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TestShardInfo that = (TestShardInfo) o;

            if (shardId != that.shardId) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return shardId;
        }
    }

    @Test
    public void testBasicDiscovery() throws Exception {
        SimpleShardedServiceFinder<TestShardInfo> serviceFinder = ServiceFinderBuilders.<TestShardInfo>shardedFinderBuilder()
                                                                        .withConnectionString(testingCluster.getConnectString())
                                                                        .withNamespace("test")
                                                                        .withServiceName("test-service")
                                                                        .withDeserializer(new Deserializer<TestShardInfo>() {
                                                                            @Override
                                                                            public ServiceNode<TestShardInfo> deserialize(byte[] data) {
                                                                                try {
                                                                                    return objectMapper.readValue(data,
                                                                                            new TypeReference<ServiceNode<TestShardInfo>>() {
                                                                                            });
                                                                                } catch (IOException e) {
                                                                                    e.printStackTrace();
                                                                                }
                                                                                return null;
                                                                            }
                                                                        })
                                                                        .build();
        serviceFinder.start();
        {
            ServiceNode<TestShardInfo> node = serviceFinder.get(new TestShardInfo(1));
            Assert.assertNotNull(node);
            Assert.assertEquals(1, node.getNodeData().getShardId());
            System.out.println(node.getHost());
        }
        {
            ServiceNode<TestShardInfo> node = serviceFinder.get(new TestShardInfo(1));
            Assert.assertNotNull(node);
            Assert.assertEquals(1, node.getNodeData().getShardId());
            System.out.println(node.getHost());
        }
        long startTime = System.currentTimeMillis();
        for(long i = 0; i <1000000; i++)
        {
            ServiceNode<TestShardInfo> node = serviceFinder.get(new TestShardInfo(2));
            Assert.assertNotNull(node);
            Assert.assertEquals(2, node.getNodeData().getShardId());
        }
        System.out.println("Took (ms):" + (System.currentTimeMillis() -startTime));
        //while (true);
    }

    private void registerService(String host, int port, int shardId) throws Exception {
        final ServiceProvider<TestShardInfo> serviceProvider = ServiceProviderBuilders.<TestShardInfo>shardedServiceProviderBuilder()
                .withConnectionString(testingCluster.getConnectString())
                .withNamespace("test")
                .withServiceName("test-service")
                .withSerializer(new Serializer<TestShardInfo>() {
                    @Override
                    public byte[] serialize(ServiceNode<TestShardInfo> data) {
                        try {
                            return objectMapper.writeValueAsBytes(data);
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }
                })
                .withHostname(host)
                .withPort(port)
                .withNodeData(new TestShardInfo(shardId))
                .withHealthcheck(new Healthcheck() {
                    @Override
                    public HealthcheckStatus check() {
                        return HealthcheckStatus.healthy;
                    }
                })
                .buildServiceDiscovery();
        serviceProvider.start();
    }
}