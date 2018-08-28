// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.index;

import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.platform.onair.tree.BTree;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class VFSRemapTest {
  public static final String host = "localhost";
  public static final int port = 11211;

  @Test
  public void testAll() throws IOException {
    final FSRecords fs = FSRecords.getInstance();
    fs.connect();
    try {
      Assert.assertTrue(fs.listRootsWithLock().length > 0);

      final BTreeIndexStorageManager storageManager = new BTreeIndexStorageManager(null, host, String.valueOf(port));

      try {
        long start = System.currentTimeMillis();

        final BTree tree = storageManager.save(fs);

        System.out.println("Tree saved: " + (System.currentTimeMillis() - start) / 1000 + "s");

        start = System.currentTimeMillis();

        storageManager.remap(fs, tree);

        System.out.println("Remap done: " + (System.currentTimeMillis() - start) / 1000 + "s");

        final int maxId = fs.getMaxId();
        for (int i = 1; i < maxId; i++) {
          if (fs.getName(i) != null) {
            Assert.assertEquals(Integer.valueOf(i), storageManager.localToRemote.apply(i));
          } else {
            Assert.assertNull(storageManager.localToRemote.apply(i));
          }
        }
      }
      finally {
        storageManager.close();
      }
    }
    finally {
      fs.dispose();
    }
  }
}
