// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.vfs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.storage.api.NoveltyImpl;
import com.intellij.platform.onair.storage.api.Storage;
import com.intellij.platform.onair.tree.BTree;
import com.intellij.platform.onair.tree.ByteUtils;
import com.intellij.util.containers.IntIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class RemoteVFS {

  public static final int VFS_TREE_KEY_SIZE = 8;

  public static class Mapping {
    public final IntIntHashMap localToRemote;
    public final IntIntHashMap remoteToLocal;

    public Mapping(IntIntHashMap localToRemote, IntIntHashMap remoteToLocal) {
      this.localToRemote = localToRemote;
      this.remoteToLocal = remoteToLocal;
    }
  }

  public static Pair<BTree, Novelty> save(final Storage storage, final FSRecords fs) {
    // TODO: apply localToRemote mapping to ensure structural sharing with already published tree
    Novelty novelty;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      novelty = new NoveltyImpl(FileUtil.createTempFile("fs-novelty-", ".here"));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    final BTree tree = BTree.create(novelty, storage, VFS_TREE_KEY_SIZE);
    for (final FSRecords.NameId root : fs.listRootsWithLock()) {
      addChild(tree, novelty, Integer.MIN_VALUE, root.id, root.name);
      addChildren(fs, tree, novelty, root.id);
    }
    return Pair.create(tree, novelty);
  }

  public static Mapping remap(final FSRecords fs, final BTree remoteTree, final Novelty novelty) {
    final LinkedList<NodeMapping> stack = new LinkedList<>();
    processChildren(remoteTree, novelty, stack, Integer.MIN_VALUE, getRootMap(fs));

    final IntIntHashMap localToRemote = new IntIntHashMap();
    final IntIntHashMap remoteToLocal = new IntIntHashMap();

    // fake idempotent mapping for default value
    localToRemote.put(1, 1);
    remoteToLocal.put(1, 1);

    NodeMapping mapping = stack.poll();
    while (mapping != null) {
      localToRemote.put(mapping.localId, mapping.remoteId);
      remoteToLocal.put(mapping.remoteId, mapping.localId);

      final Map<CharSequence, Integer> children = new HashMap<>();
      for (final FSRecords.NameId child : fs.listAll(mapping.localId)) {
        if (children.put(child.name.toString(), child.id) != null) {
          throw new RuntimeException("inconsistent children");
        }
      }
      processChildren(remoteTree, novelty, stack, mapping.remoteId, children);
      mapping = stack.poll();
    }
    return new Mapping(localToRemote, remoteToLocal);
  }

  private static void processChildren(BTree remoteTree,
                                      Novelty novelty,
                                      LinkedList<NodeMapping> queue,
                                      int remoteParentId,
                                      Map<CharSequence, Integer> children) {
    final byte[] rangeKey = new byte[4 + 4];
    ByteUtils.writeUnsignedInt(remoteParentId ^ 0x80000000, rangeKey, 0);
    ByteUtils.writeUnsignedInt(0, rangeKey, 4);
    remoteTree.forEach(novelty, rangeKey, (key, value) -> {
      final int parentId = (int)(ByteUtils.readUnsignedInt(key, 0) ^ 0x80000000);
      final boolean matchingId = parentId == remoteParentId;
      if (matchingId) {
        final String childName = new String(value, StandardCharsets.UTF_8);
        final Integer localId = children.get(childName);
        if (localId != null) {
          final int remoteId = (int)(ByteUtils.readUnsignedInt(key, 4) ^ 0x80000000);
          queue.addFirst(new NodeMapping(localId, remoteId)); // DFS
        }
      }
      return matchingId;
    });
  }

  private static void addChildren(FSRecords fs, BTree tree, Novelty novelty, int rootId) {
    for (final FSRecords.NameId child : fs.listAll(rootId)) {
      addChild(tree, novelty, rootId, child.id, child.name.toString());
      addChildren(fs, tree, novelty, child.id);
    }
  }

  private static void addChild(BTree tree, Novelty novelty, int rootId, int childId, CharSequence childName) {
    final byte[] key = new byte[8];
    ByteUtils.writeUnsignedInt(rootId ^ 0x80000000, key, 0);
    ByteUtils.writeUnsignedInt(childId ^ 0x80000000, key, 4);
    // TODO: better string binding?
    if (!tree.put(novelty, key, childName.toString().getBytes(StandardCharsets.UTF_8))) {
      throw new RuntimeException("inconsistent tree");
    }
  }

  @NotNull
  private static Map<CharSequence, Integer> getRootMap(FSRecords fs) {
    final FSRecords.NameId[] rootsList = fs.listRootsWithLock();
    final Map<CharSequence, Integer> roots = new HashMap<>();
    for (final FSRecords.NameId root : rootsList) {
      if (roots.put(root.name, root.id) != null) {
        throw new RuntimeException("inconsistent roots");
      }
    }
    return roots;
  }

  private static final class NodeMapping {
    public final int localId;
    public final int remoteId;

    public NodeMapping(int localId, int remoteId) {
      this.localId = localId;
      this.remoteId = remoteId;
    }
  }

  public static void publishVFS(BTree vfs, Novelty novelty) {
    vfs.store(novelty);
  }
}
