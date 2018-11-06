// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree;

import com.intellij.platform.onair.storage.api.Novelty;
import org.junit.Test;

import static org.junit.Assert.*;

public class AirTreeDeleteTest extends AirTreeTestBase {

  @Test
  public void testDeleteNoDuplicates() {
    BTree tree = createTree();
    Novelty.Accessor accessor = novelty.access();

    tree.put(accessor, key(1), value("1"));
    assertArrayEquals(value("1"), tree.get(accessor, key(1)));
    assertFalse(tree.delete(accessor, key(2)));
    assertTrue(tree.delete(accessor, key(1)));
    assertNull(tree.get(accessor, key(1)));

    tree = reopen(tree);
    resetNovelty();

    accessor = novelty.access();

    assertNull(tree.get(accessor, key(1)));
  }

  @Test
  public void testDeleteNoDuplicates2() {
    BTree tree = createTree();
    Novelty.Accessor accessor = novelty.access();

    tree.put(accessor, key(1), value("1"));
    tree.put(accessor, key(11), value("11"));
    tree.put(accessor, key(111), value("111"));

    tree = reopen(tree);
    resetNovelty();
    accessor = novelty.access();

    assertArrayEquals(value("1"), tree.get(accessor, key(1)));
    assertArrayEquals(value("11"), tree.get(accessor, key(11)));
    assertArrayEquals(value("111"), tree.get(accessor, key(111)));
    assertFalse(tree.delete(accessor, key(2)));
    assertTrue(tree.delete(accessor, key(111)));
    assertTrue(tree.delete(accessor, key(11)));
    assertNull(tree.get(accessor, key(11)));

    tree = reopen(tree);
    resetNovelty();
    accessor = novelty.access();

    assertNull(tree.get(accessor, key(111)));
    assertNull(tree.get(accessor, key(11)));
    assertArrayEquals(value("1"), tree.get(accessor, key(1)));
  }
}
