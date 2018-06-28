// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Novelty;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockNovelty implements Novelty {
  private final ConcurrentHashMap<Long, byte[]> novelty = new ConcurrentHashMap<>();
  private final AtomicLong addressGenerator = new AtomicLong(-2);

  @Override
  public long alloc(byte[] bytes) {
    final long result = addressGenerator.getAndDecrement();
    novelty.put(result, bytes);
    return result;
  }

  @Override
  public void free(long address) {

  }

  @Override
  public byte[] lookup(long address) {
    return novelty.get(address);
  }

  @Override
  public void update(long id, byte[] bytes) {
    // TODO
  }
}
