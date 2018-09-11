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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Stream;

public class RemoteVFS {

  public static final int VFS_TREE_KEY_SIZE = 8;

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

    final Novelty.Accessor txn = novelty.access();
    final BTree tree = BTree.create(txn, storage, VFS_TREE_KEY_SIZE);
    for (final FSRecords.NameId root : fs.listRootsWithLock()) {
      addChild(tree, txn, Integer.MIN_VALUE, root.id, root.name);
      addChildren(fs, tree, txn, root.id);
    }
    return Pair.create(tree, novelty);
  }

  public static Mapping remap(final FSRecords fs, final BTree remoteTree, final Novelty novelty) {
    final LinkedList<NodeMapping> stack = new LinkedList<>();
    final Novelty.Accessor accessor = novelty.access();
    final Map<CharSequence, Integer> roots = getRootMap(fs);
    processChildren(remoteTree, accessor, stack, Integer.MIN_VALUE, roots);

    final IntIntHashMap localToRemote = new IntIntHashMap();
    final IntIntHashMap remoteToLocal = new IntIntHashMap();

    addIdentityMapping(fs, roots.values().stream(), localToRemote, remoteToLocal);

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
      processChildren(remoteTree, accessor, stack, mapping.remoteId, children);
      addIdentityMapping(fs, children.values().stream(), localToRemote, remoteToLocal);
      mapping = stack.poll();
    }
    return new Mapping(localToRemote, remoteToLocal);
  }

  private static void addIdentityMapping(FSRecords fs, Stream<Integer> folders, IntIntHashMap localToRemote, IntIntHashMap remoteToLocal) {
    // TODO: instead of identity mapping allocate an unique id (increment max id) against remote vfs
    folders.forEach(localId -> { // remaining local folders
      localToRemote.put(localId, localId);
      remoteToLocal.put(localId, localId);
      addIdentityMapping(fs, Arrays.stream(fs.listAll(localId)).map(node -> node.id), localToRemote, remoteToLocal);
    });
  }

  private static void processChildren(BTree remoteTree,
                                      Novelty.Accessor txn,
                                      LinkedList<NodeMapping> stack,
                                      int remoteParentId,
                                      Map<CharSequence, Integer> children) {
    final byte[] rangeKey = new byte[4 + 4];
    ByteUtils.writeUnsignedInt(remoteParentId ^ 0x80000000, rangeKey, 0);
    ByteUtils.writeUnsignedInt(0, rangeKey, 4);
    remoteTree.forEach(txn, rangeKey, (key, value) -> {
      final int parentId = (int)(ByteUtils.readUnsignedInt(key, 0) ^ 0x80000000);
      final boolean matchingId = parentId == remoteParentId;
      if (matchingId) {
        final String childName = new String(value, StandardCharsets.UTF_8);
        final Integer localId = children.remove(childName);
        if (localId != null) {
          final int remoteId = (int)(ByteUtils.readUnsignedInt(key, 4) ^ 0x80000000);
          stack.addFirst(new NodeMapping(localId, remoteId)); // DFS
        }
      }
      return matchingId;
    });
  }

  private static void addChildren(FSRecords fs, BTree tree, Novelty.Accessor txn, int parentId) {
    for (final FSRecords.NameId child : fs.listAll(parentId)) {
      addChild(tree, txn, parentId, child.id, child.name.toString());
      addChildren(fs, tree, txn, child.id);
    }
  }

  private static void addChild(BTree tree, Novelty.Accessor txn, int rootId, int childId, CharSequence childName) {
    final byte[] key = new byte[8];
    ByteUtils.writeUnsignedInt(rootId ^ 0x80000000, key, 0);
    ByteUtils.writeUnsignedInt(childId ^ 0x80000000, key, 4);
    // TODO: better string binding?
    if (!tree.put(txn, key, childName.toString().getBytes(StandardCharsets.UTF_8))) {
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
    vfs.store(novelty.access());
  }

  public static class Mapping {
    public final IntIntHashMap localToRemote;
    public final IntIntHashMap remoteToLocal;

    public Mapping(IntIntHashMap localToRemote, IntIntHashMap remoteToLocal) {
      this.localToRemote = localToRemote;
      this.remoteToLocal = remoteToLocal;
    }
  }
}
