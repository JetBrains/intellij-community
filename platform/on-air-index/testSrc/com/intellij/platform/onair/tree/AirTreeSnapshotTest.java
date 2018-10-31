// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree;

import com.intellij.platform.onair.storage.api.Novelty;
import org.junit.Assert;
import org.junit.Test;

public class AirTreeSnapshotTest extends AirTreeTestBase {

  @Test
  public void competingWritesTest() {
    BTree tree = createTree();
    int total = 1000;

    Novelty.Accessor accessor = novelty.access();

    for (int i = 0; i < total; i++) {
      Assert.assertTrue(tree.put(accessor, key(i), v(i)));
    }

    BTree clone1 = tree.snapshot();
    BTree clone2 = tree.snapshot();

    clone1.put(accessor, key(10), v(666));
    clone2.put(accessor, key(10), v(777));

    Assert.assertArrayEquals(v(10), tree.get(accessor, key(10)));
    Assert.assertArrayEquals(v(666), clone1.get(accessor, key(10)));
    Assert.assertArrayEquals(v(777), clone2.get(accessor, key(10)));

    clone1 = reopen(clone1);
    clone1 = reopen(clone1);
    clone2 = reopen(clone2);

    accessor = novelty.access();

    Assert.assertArrayEquals(v(10), tree.get(accessor, key(10)));
    Assert.assertArrayEquals(v(666), clone1.get(accessor, key(10)));
    Assert.assertArrayEquals(v(777), clone2.get(accessor, key(10)));
  }
}
