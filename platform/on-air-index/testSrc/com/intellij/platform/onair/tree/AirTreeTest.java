// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree;

import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.storage.api.Tree;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AirTreeTest extends AirTreeTestBase {
  @Test
  public void testPutSmoke() {
    int total = 1000;
    BTree tree = createTree();

    Novelty.Accessor accessor = novelty.access();
    for (int i = 0; i < total - 10; i++) {
      if (i == 42) {
        tree.dump(accessor, System.out, ValueDumper.INSTANCE);

        AtomicLong size = new AtomicLong();
        Assert.assertFalse(tree.forEach(accessor, (key, value) -> size.incrementAndGet() < 10));
        Assert.assertEquals(10, size.get());
        size.set(0);
        Assert.assertTrue(tree.forEach(accessor, (key, value) -> size.incrementAndGet() < 50));
        Assert.assertEquals(42, size.get());
      }

      Assert.assertTrue(tree.put(accessor, key(i), v(i)));
    }

    // insert in non-linear order
    for (int i = 0; i < 10; i++) {
      Assert.assertTrue(tree.put(accessor, key(total - i - 1), v(total - i - 1)));
    }

    // tree.dump(novelty, System.out, ValueDumper.INSTANCE);
    checkTree(accessor, tree, total);

    tree = reopen(tree);
    resetNovelty();
    accessor = novelty.access();

    checkTree(accessor, tree, total);

    final AtomicInteger size = new AtomicInteger();
    tree.forEachBulk(200, (key, value) -> {
      int i = size.getAndIncrement();
      Assert.assertArrayEquals(key(i), key);
      Assert.assertArrayEquals(v(i), value);
      return true;
    });
    Assert.assertEquals(total, size.get());

    for (int i = 0; i < total; i++) {
      Assert.assertTrue(tree.put(accessor, key(i), v(2 * i)));
    }

    long noveltySize = novelty.getSize();

    checkTree(accessor, tree, total, 2);

    // overwrite some novelty leafs, check for memory leaks

    for (int i = 0; i < total; i++) {
      Assert.assertTrue(tree.put(accessor, key(i), v(3 * i)));
    }

    checkTree(accessor, tree, total, 3);

    Assert.assertEquals(noveltySize, novelty.getSize());
  }

  private static void checkTree(Novelty.Accessor txn, Tree tree, final int total, final int multiplier) {
    for (int i = 0; i < total; i++) {
      Assert.assertArrayEquals(v(multiplier * i), tree.get(txn, key(i)));
    }
  }

  private static void checkTree(Novelty.Accessor txn, Tree tree, final int total) {
    for (int i = 0; i < total; i++) {
      Assert.assertArrayEquals(v(i), tree.get(txn, key(i)));
      if (i != 0) {
        Assert.assertNull(tree.get(txn, key(-i)));
      }
    }

    final AtomicInteger size = new AtomicInteger();
    tree.forEach(txn, (key, value) -> {
      int i = size.getAndIncrement();
      Assert.assertArrayEquals(key(i), key);
      Assert.assertArrayEquals(v(i), value);
      return true;
    });
    Assert.assertEquals(total, size.get());
  }
}
