// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.vfs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.platform.onair.storage.HashMapCache;
import com.intellij.platform.onair.storage.StorageImpl;
import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.tree.BTree;
import gnu.trove.TIntHashSet;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.platform.onair.vfs.RemoteVFS.VFS_TREE_KEY_SIZE;

public class VFSRemapTest {
  public static final String host = "localhost";
  public static final int port = 11211;

  @Test
  public void testAll() throws IOException {
    System.out.println("Pid: " + Util.getPid());

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
        final AtomicLong size = new AtomicLong();
        final AtomicLong values = new AtomicLong();
        pair.first.forEach(novelty.access(), (key, value) -> {
          size.addAndGet(key.length + value.length);
          values.incrementAndGet();
          return true;
        });

        start = System.currentTimeMillis();
        storage = storage.withCache(new HashMapCache<>());
        BTree tree = BTree.load(storage, VFS_TREE_KEY_SIZE, pair.first.store(novelty.access()));
        System.out.println(
          "Tree uploaded: " + (System.currentTimeMillis() - start) / 1000 + "s, size: " + size.get() + ", values: " + values.get());

        start = System.currentTimeMillis();

        size.set(0);
        values.set(0);
        tree.forEachBulk(1000000, (key, value) -> {
          size.addAndGet(key.length + value.length);
          values.incrementAndGet();
          return true;
        });

        System.out.println(
          "Tree warmed up: " + (System.currentTimeMillis() - start) / 1000 + "s, size: " + size.get() + ", values: " + values.get());
        storage.dumpStats(System.out);

        storage.disablePrefetch();

        start = System.currentTimeMillis();

        size.set(0);
        values.set(0);
        tree.forEach(Novelty.VOID_TXN, (key, value) -> {
          size.addAndGet(key.length + value.length);
          values.incrementAndGet();
          return true;
        });

        System.out.println(
          "Tree warmed up again: " + (System.currentTimeMillis() - start) / 1000 + "s, size: " + size.get() + ", values: " + values.get());
        storage.dumpStats(System.out);

        start = System.currentTimeMillis();

        final RemoteVFS.Mapping mapping;
        try {
          mapping = RemoteVFS.remap(fs, tree, Novelty.VOID);
        }
        finally {
          System.out.println("Remap done: " + (System.currentTimeMillis() - start) / 1000 + "s");
        }
        storage.dumpStats(System.out);

        final int maxId = fs.getMaxId();
        System.out.println("max id: " + maxId);
        TIntHashSet nonOrphans = new TIntHashSet();
        TIntHashSet orphans = new TIntHashSet();
        for (final FSRecords.NameId root : fs.listRootsWithLock()) {
          nonOrphans.add(root.id);
        }
        for (int i = 1; i < maxId; i++) {
          if (isNonOrphan(fs, nonOrphans, orphans, i)) {
            String name = fs.getName(i);
            if (name != null) {
              int actual = mapping.localToRemote.get(i);
              if (i != actual) {
                System.out.println("fail: " + i + ", name: " + name);
                continue;
              }

              Assert.assertEquals(i, actual);
            }
            else {
              Assert.assertEquals(-1, mapping.localToRemote.get(i));
            }
          }
        }
        System.out.println("assert done, orphans: " + orphans.size() + ", non-orphans: " + nonOrphans.size());
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

  private static boolean isNonOrphan(final FSRecords fs, final TIntHashSet nonOrphans, final TIntHashSet orphans, final int child) {
    if (nonOrphans.contains(child)) {
      return true;
    }
    final int parent = fs.getParent(child);
    if (parent <= 0) {
      return false;
    }
    else if (orphans.contains(parent)) {
      return false;
    }
    else if (Arrays.stream(fs.listAll(parent)).noneMatch(node -> node.id == child)) {
      orphans.add(child);
      return false;
    }
    if (isNonOrphan(fs, nonOrphans, orphans, parent)) {
      nonOrphans.add(child);
      return true;
    }
    else {
      orphans.add(child);
      return false;
    }
  }
}
