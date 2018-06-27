// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

public class StorageTest {

  @Test
  public void simpleTest() throws IOException {
    final Storage storage = new StorageImpl(new InetSocketAddress("localhost", 11211));
    final byte[] bytes = "Hello".getBytes();
    final long hash = storage.store(bytes);
    Assert.assertArrayEquals(bytes, storage.lookup(hash));
  }

}
