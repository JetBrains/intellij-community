// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Storage;
import intellij.platform.onair.storage.StorageImpl;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class AirTreeTest {
  private static final DecimalFormat FORMATTER;

  static {
    FORMATTER = (DecimalFormat)NumberFormat.getIntegerInstance();
    FORMATTER.applyPattern("00000");
  }

  @Test
  public void testSplitRight2() throws IOException {
    final Storage storage = new MockStorage();
    //final Storage storage = new StorageImpl(new InetSocketAddress("localhost", 11211));

    BTree tree = BTree.createEmpty(storage, 4, 8);

    tree.put(k(1), v(1));

    Assert.assertArrayEquals(v(1), tree.get(k(1)));
    Assert.assertNull(tree.get(k(2)));
  }

  public static byte[] k(int key) {
    byte[] result = new byte[4];
    result[0] = (byte)(key >>> 24);
    result[1] = (byte)(key >>> 16);
    result[2] = (byte)(key >>> 8);
    result[3] = (byte)key;
    return result;
  }

  public static byte[] v(int value) {
    return key("val " + FORMATTER.format(value));
  }

  public static byte[] key(String key) {
    return key == null ? null : key.getBytes(Charset.forName("UTF-8"));
  }
}
