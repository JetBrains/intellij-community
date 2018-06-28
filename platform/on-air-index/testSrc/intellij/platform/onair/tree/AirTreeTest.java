// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Tree;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AirTreeTest extends AirTreeTestBase {
  @Test
  public void testSplitRight2() {
    int total = 1000;
    BTree tree = createTree();

    for (int i = 0; i < total; i++) {
      if (i == 42) {
        tree.dump(novelty, System.out, ValueDumper.INSTANCE);

        AtomicLong size = new AtomicLong();
        Assert.assertFalse(tree.forEach(novelty, (key, value) -> size.incrementAndGet() < 10));
        Assert.assertEquals(10, size.get());
        size.set(0);
        Assert.assertTrue(tree.forEach(novelty, (key, value) -> size.incrementAndGet() < 50));
        Assert.assertEquals(42, size.get());
      }

      Assert.assertTrue(tree.put(novelty, key(i), v(i)));
    }

    // tree.dump(novelty, System.out, ValueDumper.INSTANCE);
    checkTree(tree, total);

    tree = reopen(tree);

    checkTree(tree, total);

    for (int i = 0; i < total; i++) {
      Assert.assertTrue(tree.put(novelty, key(i), v(2 * i)));
    }

    long size = novelty.getSize();

    checkTree(tree, total, 2);

    // overwrite some novelty leafs, check for memory leaks

    for (int i = 0; i < total; i++) {
      Assert.assertTrue(tree.put(novelty, key(i), v(3 * i)));
    }

    checkTree(tree, total, 3);

    Assert.assertEquals(size, novelty.getSize());
  }

  private void checkTree(Tree tree, final int total) {
    for (int i = 0; i < total; i++) {
      Assert.assertArrayEquals(v(i), tree.get(novelty, key(i)));
      if (i != 0) {
        Assert.assertNull(tree.get(novelty, key(-i)));
      }
    }

    final AtomicInteger size = new AtomicInteger();
    tree.forEach(novelty, (key, value) -> {
      int i = size.getAndIncrement();
      Assert.assertArrayEquals(key(i), key);
      Assert.assertArrayEquals(v(i), value);
      return true;
    });
    Assert.assertEquals(size.get(), total);
  }

  private void checkTree(Tree tree, final int total, final int multiplier) {
    for (int i = 0; i < total; i++) {
      Assert.assertArrayEquals(v(multiplier * i), tree.get(novelty, key(i)));
    }
  }
}
