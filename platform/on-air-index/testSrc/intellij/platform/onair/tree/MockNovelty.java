// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Novelty;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockNovelty implements Novelty {
  private final ConcurrentHashMap<Long, byte[]> novelty = new ConcurrentHashMap<>();
  private final AtomicLong addressGenerator = new AtomicLong(-2);

  @Override
  public long alloc(byte[] bytes) {
    final long result = addressGenerator.getAndDecrement();
    novelty.put(result, Arrays.copyOf(bytes, bytes.length));
    return result;
  }

  @Override
  public byte[] lookup(long address) {
    final byte[] result = novelty.get(address);
    return Arrays.copyOf(result, result.length); // emulate storage (im)mutability
  }

  @Override
  public void update(long address, byte[] bytes) {
    final byte[] result = novelty.get(address);
    final int length = result.length;
    if (length != bytes.length) {
      throw new IllegalArgumentException("Update mismatch");
    }
    System.arraycopy(bytes, 0, result, 0, length);
  }
}
