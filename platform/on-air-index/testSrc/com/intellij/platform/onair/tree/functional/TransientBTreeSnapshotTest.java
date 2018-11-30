// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree.functional;

import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.storage.api.TransientTree;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.platform.onair.storage.api.Novelty.VOID_TXN;
import static com.intellij.platform.onair.tree.TestByteUtils.writeUnsignedInt;

public class TransientBTreeSnapshotTest {
  private static final DecimalFormat FORMATTER;

  static {
    FORMATTER = (DecimalFormat)NumberFormat.getIntegerInstance();
    FORMATTER.applyPattern("00000");
  }

  @Test
  public void testPutSmoke() {
    int total = 1000;
    TransientTree tree = TransientBTree.create(4);

    for (int i = 0; i < total - 10; i++) {
      if (i == 42) {
        // tree.dump(accessor, System.out, ValueDumper.INSTANCE);

        AtomicLong size = new AtomicLong();
        Assert.assertFalse(tree.forEach(VOID_TXN, (key, value) -> size.incrementAndGet() < 10));
        Assert.assertEquals(10, size.get());
        size.set(0);
        Assert.assertTrue(tree.forEach(VOID_TXN, (key, value) -> size.incrementAndGet() < 50));
        Assert.assertEquals(42, size.get());
      }

      tree = assign(tree, tree.put(VOID_TXN, key(i), v(i)));
    }

    // insert in non-linear order
    for (int i = 0; i < 10; i++) {
      tree = assign(tree, tree.put(VOID_TXN, key(total - i - 1), v(total - i - 1)));
    }

    // tree.dump(novelty, System.out, ValueDumper.INSTANCE);
    checkTree(tree, total);
  }

  private static TransientTree assign(TransientTree current, TransientTree updated) {
    Assert.assertNotSame(current, updated);
    return updated;
  }

  private static void checkTree(Novelty.Accessor txn, TransientTree tree, final int total, final int multiplier) {
    for (int i = 0; i < total; i++) {
      Assert.assertArrayEquals(v(multiplier * i), tree.get(txn, key(i)));
    }
  }

  private static void checkTree(/*Novelty.Accessor txn,*/ TransientTree tree, final int total) {
    for (int i = 0; i < total; i++) {
      Assert.assertArrayEquals(v(i), tree.get(VOID_TXN, key(i)));
      if (i != 0) {
        Assert.assertNull(tree.get(VOID_TXN, key(-i)));
      }
    }

    final AtomicInteger size = new AtomicInteger();
    tree.forEach(VOID_TXN, (key, value) -> {
      int i = size.getAndIncrement();
      Assert.assertArrayEquals(key(i), key);
      Assert.assertArrayEquals(v(i), value);
      return true;
    });
    Assert.assertEquals(total, size.get());
  }

  public static byte[] key(int key) {
    byte[] result = new byte[4];
    writeUnsignedInt(key ^ 0x80000000, result);
    return result;
  }

  public static byte[] v(int value) {
    return key("val " + FORMATTER.format(value));
  }

  private static byte[] key(String key) {
    return key == null ? null : key.getBytes(Charset.forName("UTF-8"));
  }

  public static byte[] value(String value) {
    return key(value);
  }
}
