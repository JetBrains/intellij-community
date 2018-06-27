// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage;

import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Storage;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StorageTest {
  // setup:
  // memcached --memory-limit=4096 --max-item-size=5242880
  @Test
  public void simpleTest() throws IOException {
    final Storage storage = new StorageImpl(new InetSocketAddress("localhost", 11211));
    final byte[] bytes = "Hello".getBytes();
    final Address hash = storage.store(bytes);
    Assert.assertArrayEquals(bytes, storage.lookup(hash));
    Assert.assertArrayEquals(bytes, storage.lookup(hash));
  }

  @Test
  public void performanceTest() throws IOException {
    final Storage storage = new StorageImpl(new InetSocketAddress("localhost", 11211));
    long start = System.currentTimeMillis();
    final int insertionCount = 1024 * 128; // 2.5GB total
    final int valueSize = 1024 * 20; // 20KB
    Address[] addresses = new Address[insertionCount];
    for (int i = 0; i < insertionCount; i ++) {
      final byte[] bytes = new byte[valueSize];
      bytes[(i % bytes.length)] = 42;
      addresses[i] = storage.store(bytes);
    }
    System.out.println(String.format("Write time: %d ms", System.currentTimeMillis() - start)); // 5916 ms
    start = System.currentTimeMillis();
    for (int i = 0; i < insertionCount; i ++) {
      final byte[] bytes = new byte[valueSize];
      bytes[(i % bytes.length)] = 42;
      final Address address = addresses[i];
      Assert.assertArrayEquals(bytes, storage.lookup(address));
    }
    System.out.println(String.format("Read time: %d ms", System.currentTimeMillis() - start)); // 10663 ms
  }

  @Test
  public void latencyTest() throws IOException {

    final Storage storage = new StorageImpl(new InetSocketAddress("localhost", 11211));
    int valueSize = 1024 * 20;

    final byte[] bytes = new byte[valueSize];

    long total = 0;
    for (int i = 0; i < 10000; i++) {
      new Random().nextBytes(bytes);
      long start = System.currentTimeMillis();
      final Address address = storage.store(bytes);
      byte[] result = storage.lookup(address);
      Assert.assertArrayEquals(result, bytes);
      long end = System.currentTimeMillis();
      if (i > 9000) {
        total += end - start;
      }
    }
    System.out.println(String.format("Average write time: %f ms", total / 1000.0)); // 0.12ms
  }

  @Test
  public void cacheTest() throws IOException {
    final Storage storage = new StorageImpl(new InetSocketAddress("localhost", 11211));

    final List<byte[]> entries = new ArrayList<>();
    final List<Address> keys = new ArrayList<>();
    final int uniqueEntires = 10000;
    final int entrySize = 1024 * 24;

    for (int i = 0; i < uniqueEntires; i++) {
      final byte[] bytes = new byte[entrySize];
      new Random().nextBytes(bytes);
      entries.add(bytes);
      final Address result = storage.store(bytes);
      keys.add(result);
    }

    final int pollsCount = 10000;
    int total = 0;

    for (int i = 0; i < pollsCount; i++) {

      int randomIndex = new Random().nextInt(uniqueEntires);
      Address randomAddress = keys.get(randomIndex);

      long start = System.currentTimeMillis();
      byte[] result = storage.lookup(randomAddress);
      long end = System.currentTimeMillis();

      total += end - start;

      Assert.assertArrayEquals(result, entries.get(randomIndex));
    }

    System.out.println(String.format("Average write time: %f ms", total / 10000.0));

  }

}
