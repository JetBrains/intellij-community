// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Novelty;
import org.junit.Assert;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockNovelty implements Novelty {
  private final ConcurrentHashMap<Long, byte[]> novelty = new ConcurrentHashMap<>();
  private final AtomicLong addressGenerator = new AtomicLong(1);
  private final AtomicLong size = new AtomicLong(0);

  @Override
  public long alloc(byte[] bytes) {
    final long result = addressGenerator.getAndIncrement();
    final int length = bytes.length;
    novelty.put(result, Arrays.copyOf(bytes, length));
    size.addAndGet(length);
    return result;
  }

  @Override
  public byte[] lookup(long address) {
    final byte[] result = novelty.get(address);
    if (result == null) {
      throw new IllegalArgumentException("data evicted, address: " + address);
    }
    return Arrays.copyOf(result, result.length); // emulate storage (im)mutability
  }

  @Override
  public void update(long address, byte[] bytes) {
    final byte[] result = novelty.get(address);
    final int length = result.length;
    if (length != bytes.length) {
      throw new IllegalArgumentException("update mismatch, address: " + address);
    }
    System.arraycopy(bytes, 0, result, 0, length);
  }

  @Override
  public void free(long address) {
    final byte[] remove = novelty.get(address);
    if (remove == null) {
      throw new IllegalArgumentException("address not in use: " + address);
    }
    if (novelty.remove(address, remove)) {
      size.addAndGet(-remove.length);
    }
  }

  public long getSize() {
    long realSize = novelty.reduceValuesToLong(1, value -> value.length, 0, Long::sum);

    Assert.assertEquals(realSize, size.get());

    return realSize;
  }
}
