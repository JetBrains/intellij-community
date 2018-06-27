// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage;

import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Storage;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;

public class StorageTest {

  @Test
  public void simpleTest() throws IOException {
    final Storage storage = new StorageImpl(new InetSocketAddress("localhost", 11211));
    final byte[] bytes = "Hello".getBytes();
    final Address hash = storage.store(bytes);
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
    System.out.println(String.format("Write time: %d ms", System.currentTimeMillis() - start));
    start = System.currentTimeMillis();
    for (int i = 0; i < insertionCount; i ++) {
      final byte[] bytes = new byte[valueSize];
      bytes[(i % bytes.length)] = 42;
      final Address address = addresses[i];
      Assert.assertArrayEquals(bytes, storage.lookup(address));
    }
    System.out.println(String.format("Read time: %d ms", System.currentTimeMillis() - start));
  }

  @Test
  public void latencyTest() throws IOException {
    final Storage storage = new StorageImpl(new InetSocketAddress("localhost", 11211));
    int valueSize = 1024 * 20;
    final byte[] bytes = new byte[valueSize];
    new Random().nextBytes(bytes);

    long start = System.currentTimeMillis();
    final Address address = storage.store(bytes);
    System.out.println(String.format("Write time: %d ms", System.currentTimeMillis() - start));

    start = System.currentTimeMillis();
    storage.lookup(address);
    System.out.println(String.format("Read time: %d ms", System.currentTimeMillis() - start));
  }
}
