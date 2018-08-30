// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.vfs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.platform.onair.storage.StorageImpl;
import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.tree.BTree;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static com.intellij.platform.onair.vfs.RemoteVFS.VFS_TREE_KEY_SIZE;

public class VFSRemapTest {
  public static final String host = "localhost";
  public static final int port = 11211;

  @Test
  public void testAll() throws IOException, InterruptedException {
    final FSRecords fs = FSRecords.getInstance();
    fs.connect();
    try {
      Assert.assertTrue(fs.listRootsWithLock().length > 0);

      StorageImpl storage = new StorageImpl(new InetSocketAddress(host, port));

      Novelty novelty = null;
      try {
        long start = System.currentTimeMillis();

        final Pair<BTree, Novelty> pair = RemoteVFS.save(storage, fs);
        System.out.println("Tree saved: " + (System.currentTimeMillis() - start) / 1000 + "s");
        novelty = pair.second;

        start = System.currentTimeMillis();
        BTree tree = BTree.load(storage, VFS_TREE_KEY_SIZE, pair.first.store(novelty.access()));
        System.out.println("Tree uploaded: " + (System.currentTimeMillis() - start) / 1000 + "s");

        start = System.currentTimeMillis();
        final RemoteVFS.Mapping mapping;
        try {
          mapping = RemoteVFS.remap(fs, tree, Novelty.VOID);
        } finally {
          System.out.println("Remap done: " + (System.currentTimeMillis() - start) / 1000 + "s");
        }

        final int maxId = fs.getMaxId();
        for (int i = 1; i < maxId; i++) {
          if (fs.getName(i) != null) {
            Assert.assertEquals(i, mapping.localToRemote.get(i));
          }
          else {
            Assert.assertEquals(-1, mapping.localToRemote.get(i));
          }
        }
      }
      finally {
        if (novelty != null) {
          novelty.close();
        }
      }
    }
    finally {
      fs.dispose();
    }
  }
}
