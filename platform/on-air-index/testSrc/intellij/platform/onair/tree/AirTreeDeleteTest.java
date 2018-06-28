// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import org.junit.Test;

import static org.junit.Assert.*;

public class AirTreeDeleteTest extends AirTreeTestBase {

  @Test
  public void testDeleteNoDuplicates() {
    BTree tree = createTree();

    tree.put(novelty, key(1), value("1"));
    assertArrayEquals(value("1"), tree.get(novelty, key(1)));
    assertFalse(tree.delete(novelty, key(2)));
    assertTrue(tree.delete(novelty, key(1)));
    assertNull(tree.get(novelty, key(1)));

    tree = reopen(tree);

    assertNull(tree.get(novelty, key(1)));
  }

  @Test
  public void testDeleteNoDuplicates2() {
    BTree tree = createTree();

    tree.put(novelty, key(1), value("1"));
    tree.put(novelty, key(11), value("11"));
    tree.put(novelty, key(111), value("111"));

    tree = reopen(tree);

    assertArrayEquals(value("1"), tree.get(novelty, key(1)));
    assertArrayEquals(value("11"), tree.get(novelty, key(11)));
    assertArrayEquals(value("111"), tree.get(novelty, key(111)));
    assertFalse(tree.delete(novelty, key(2)));
    assertTrue(tree.delete(novelty, key(111)));
    assertTrue(tree.delete(novelty, key(11)));
    assertNull(tree.get(novelty, key(11)));

    tree = reopen(tree);

    assertNull(tree.get(novelty, key(111)));
    assertNull(tree.get(novelty, key(11)));
    assertArrayEquals(value("1"), tree.get(novelty, key(1)));
  }
}
