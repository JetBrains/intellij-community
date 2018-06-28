// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage;

import intellij.platform.onair.storage.api.Novelty;
import intellij.platform.onair.storage.api.NoveltyImpl;
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
    final Random generator = new Random();
    byte[] bytes1 = new byte[100];
    generator.nextBytes(bytes1);
    byte[] bytes2 = new byte[100];
    generator.nextBytes(bytes2);
    long addr1 = novelty.alloc(bytes1);
    Assert.assertTrue(addr1 >= 0);
    long addr2 = novelty.alloc(bytes2);
    Assert.assertTrue(addr2 >= 0);
    Assert.assertArrayEquals(bytes1, novelty.lookup(addr1));
    Assert.assertArrayEquals(bytes2, novelty.lookup(addr2));
    novelty.update(addr1, bytes2);
    Assert.assertArrayEquals(bytes2, novelty.lookup(addr1));
    novelty.close();

    novelty = new NoveltyImpl(path.toFile());

    byte[] bytes1_ = novelty.lookup(addr1);
    byte[] bytes2_ = novelty.lookup(addr2);

    Assert.assertArrayEquals(bytes2, bytes1_);
    Assert.assertArrayEquals(bytes2, bytes2_);
    novelty.close();
  }

  @Test
  public void freeTest() throws IOException {
    // [ ] -alloc-> [ | ] -free-> [ ] -alloc-> [ | | | ] -free-> [ } -alloc-> [ | | | | | | | ] -etc-> ...
    final Random generator = new Random();

    final Path path = Paths.get("./resources/index.tmp");
    Novelty novelty = new NoveltyImpl(path.toFile());

    final int times = 4;
    final int size = ((NoveltyImpl) novelty).getFreeSpace();

    for (int i = 1; i < times; i++) {
      final int partitions = (int) Math.pow(8, i);
      final int partitionLength = (size / partitions) - 5;

      byte[] randomData = new byte[partitionLength];
      long[] addrs = new long[partitions];

      for (int j = 0; j < partitions; j++) {
        generator.nextBytes(randomData);
        addrs[j] = novelty.alloc(randomData);
      }

      for (int j = 0; j < partitions; j++) {
        novelty.free(addrs[j]);
      }
      Assert.assertEquals(((NoveltyImpl)novelty).getFreeSpace(), size);
    }
  }
}
