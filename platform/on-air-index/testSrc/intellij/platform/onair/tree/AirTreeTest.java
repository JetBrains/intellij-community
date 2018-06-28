// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Storage;
import intellij.platform.onair.storage.api.Tree;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import static intellij.platform.onair.tree.TestByteUtils.writeUnsignedInt;

public class AirTreeTest {
  private static final DecimalFormat FORMATTER;

  static {
    FORMATTER = (DecimalFormat)NumberFormat.getIntegerInstance();
    FORMATTER.applyPattern("00000");
  }

  Storage storage = null;
  MockNovelty novelty = null;

  @Before
  public void setUp() {
    storage = new MockStorage();
    novelty = new MockNovelty();
  }

  @Test
  public void testSplitRight2() {
    int total = 1000;
    BTree tree = BTree.create(novelty, storage, 4);

    for (int i = 0; i < total; i++) {
      if (i == 42) {
        tree.dump(novelty, System.out, ValueDumper.INSTANCE);
      }

      Assert.assertTrue(tree.put(novelty, k(i), v(i)));
    }

    // tree.dump(novelty, System.out, ValueDumper.INSTANCE);
    checkTree(tree, total);

    tree = reopen(tree);

    checkTree(tree, total);

    for (int i = 0; i < total; i++) {
      Assert.assertTrue(tree.put(novelty, k(i), v(2 * i)));
    }

    long size = novelty.getSize();

    checkTree(tree, total, 2);

    // overwrite some novelty leafs, check for memory leaks

    for (int i = 0; i < total; i++) {
      Assert.assertTrue(tree.put(novelty, k(i), v(3 * i)));
    }

    checkTree(tree, total, 3);

    Assert.assertEquals(size, novelty.getSize());
  }

  @After
  public void tearDown() {
    storage = null;
    novelty = null;
  }

  @NotNull
  private BTree reopen(BTree tree) {
    Address address = tree.store(novelty);
    novelty = new MockNovelty(); // cleanup
    tree = BTree.load(storage, 4, address);
    return tree;
  }

  private void checkTree(Tree tree, final int total) {
    for (int i = 0; i < total; i++) {
      Assert.assertArrayEquals(v(i), tree.get(novelty, k(i)));
      if (i != 0) {
        Assert.assertNull(tree.get(novelty, k(-i)));
      }
    }
  }

  private void checkTree(Tree tree, final int total, final int multiplier) {
    for (int i = 0; i < total; i++) {
      Assert.assertArrayEquals(v(multiplier * i), tree.get(novelty, k(i)));
    }
  }

  public static byte[] k(int key) {
    byte[] result = new byte[4];
    writeUnsignedInt(key ^ 0x80000000, result);
    return result;
  }

  public static byte[] v(int value) {
    return key("val " + FORMATTER.format(value));
  }

  public static byte[] key(String key) {
    return key == null ? null : key.getBytes(Charset.forName("UTF-8"));
  }
}
