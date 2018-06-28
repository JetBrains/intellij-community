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
    Novelty novelty = new NoveltyImpl(path.toFile());
    final Random generator = new Random();
    byte[] bytes1 = new byte[100];
    generator.nextBytes(bytes1);
    byte[] bytes2 = new byte[100];
    generator.nextBytes(bytes2);
    long addr1 = novelty.alloc(bytes1);
    Assert.assertTrue(addr1 < 0);
    long addr2 = novelty.alloc(bytes2);
    Assert.assertTrue(addr2 < 0);
    Assert.assertArrayEquals(bytes1, novelty.lookup(addr1));
    Assert.assertArrayEquals(bytes2, novelty.lookup(addr2));
    novelty.update(addr1, bytes2);
    Assert.assertArrayEquals(bytes2, novelty.lookup(addr1));
    ((NoveltyImpl)novelty).close();

    //novelty = new NoveltyImpl(path.toFile());
    //Assert.assertArrayEquals(bytes1, novelty.lookup(addr1));
    //Assert.assertArrayEquals(bytes2, novelty.lookup(addr1));
    //((NoveltyImpl)novelty).close();
  }
}
