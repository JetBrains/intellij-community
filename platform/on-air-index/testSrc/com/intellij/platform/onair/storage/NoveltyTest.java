// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.storage;

import com.intellij.platform.onair.storage.api.NoveltyImpl;
import com.intellij.platform.onair.storage.api.Novelty;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

public class NoveltyTest {
  @Test
  public void simpleTest() throws IOException {
    final Path path = Paths.get("./resources/index.tmp");
    if (Files.exists(path)) {
      Files.delete(path);
    }
    NoveltyImpl novelty = new NoveltyImpl(path.toFile());
    long addr1;
    long addr2;
    byte[] bytes2;
    try {
      Novelty.Accessor txn = novelty.access();

      final Random generator = new Random();
      byte[] bytes1 = new byte[100];
      generator.nextBytes(bytes1);
      bytes2 = new byte[100];
      generator.nextBytes(bytes2);
      addr1 = txn.alloc(bytes1);
      Assert.assertTrue(addr1 >= 0);
      addr2 = txn.alloc(bytes2);
      Assert.assertTrue(addr2 >= 0);
      Assert.assertArrayEquals(bytes1, txn.lookup(addr1));
      Assert.assertArrayEquals(bytes2, txn.lookup(addr2));
      txn.update(addr1, bytes2);
      Assert.assertArrayEquals(bytes2, txn.lookup(addr1));
    }
    finally {
      novelty.close();
    }

    novelty = new NoveltyImpl(path.toFile());
    try {
      Novelty.Accessor accessor = novelty.access();

      byte[] bytes1_ = accessor.lookup(addr1);
      byte[] bytes2_ = accessor.lookup(addr2);

      Assert.assertArrayEquals(bytes2, bytes1_);
      Assert.assertArrayEquals(bytes2, bytes2_);
    }
    finally {
      novelty.close();
      path.toFile().delete();
    }
  }

  @Test
  public void freeTest() throws IOException {
    // [ ] -alloc-> [ | ] -free-> [ ] -alloc-> [ | | | ] -free-> [ } -alloc-> [ | | | | | | | ] -etc-> ...
    final Random generator = new Random();

    final Path path = Paths.get("./resources/index.tmp");
    Novelty novelty = new NoveltyImpl(path.toFile());

    try {
      Novelty.Accessor accessor = novelty.access();

      final int times = 4;
      final int size = ((NoveltyImpl)novelty).getFreeSpace();

      for (int i = 1; i < times; i++) {
        final int partitions = (int)Math.pow(8, i);
        final int partitionLength = (size / partitions) - 5;

        byte[] randomData = new byte[partitionLength];
        long[] addrs = new long[partitions];

        for (int j = 0; j < partitions; j++) {
          generator.nextBytes(randomData);
          addrs[j] = accessor.alloc(randomData);
        }

        for (int j = 0; j < partitions; j++) {
          accessor.free(addrs[j]);
        }
        Assert.assertEquals(((NoveltyImpl)novelty).getFreeSpace(), size);
      }
    }
    finally {
      novelty.close();
      path.toFile().delete();
    }
  }
}
