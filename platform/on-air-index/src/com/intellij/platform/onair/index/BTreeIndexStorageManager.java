// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.index;

import clojure.lang.PersistentHashMap;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.platform.onair.storage.StorageImpl;
import com.intellij.platform.onair.storage.api.NoveltyImpl;
import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.storage.api.Storage;
import com.intellij.platform.onair.tree.BTree;
import com.intellij.platform.onair.vfs.RemoteVFS;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IndexInfrastructure;
import com.intellij.util.indexing.IndexStorageManager;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentMap;
import org.jdom.Element;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BTreeIndexStorageManager implements IndexStorageManager {
  private static final int FORWARD_STORAGE_KEY_SIZE = 6;
  private final Function<Integer, Integer> localToRemote;
  private final Function<Integer, Integer> remoteToLocal;

  public static class IndexState {
    public final Storage storage;

    public final PersistentHashMap indexStorages;
    public final PersistentHashMap forwardIndices;
    public final Novelty novelty;
    public final RevisionDescriptor revisionDescriptor;
    public final BTree forwardIndexTree;
    public final RemoteVFS.Mapping vfsMapping;

    public IndexState(Storage storage,
                      Novelty novelty,
                      RevisionDescriptor revisionDescriptor,
                      BTree forwardIndexTree,
                      RemoteVFS.Mapping vfsMapping,
                      PersistentHashMap indexStorages,
                      PersistentHashMap forwardIndices) {
      this.storage = storage;
      this.novelty = novelty;
      this.revisionDescriptor = revisionDescriptor;
      this.forwardIndexTree = forwardIndexTree;
      this.vfsMapping = vfsMapping;
      this.indexStorages = indexStorages;
      this.forwardIndices = forwardIndices;
    }

    public IndexState withNewForwardIndexStorage(String indexId, BTreeForwardIndexStorage forwardIndexStorage) {
      return new IndexState(storage, novelty, revisionDescriptor, forwardIndexTree, vfsMapping, indexStorages,
                            (PersistentHashMap)forwardIndices.assoc(indexId, forwardIndexStorage));
    }

    public IndexState withNewInvertedIndexStorage(String indexId, BTreeIndexStorage indexStorage) {
      return new IndexState(storage, novelty, revisionDescriptor, forwardIndexTree, vfsMapping,
                            (PersistentHashMap)indexStorages.assoc(indexId, indexStorage), forwardIndices);
    }
  }

  public final AtomicReference<IndexState> stateRef;


  public BTreeIndexStorageManager() {
    this(
      System.getProperty("onair.index.revision"),
      System.getProperty("onair.index.cache.host"),
      System.getProperty("onair.index.cache.port", "11211")
    );
  }

  public BTreeIndexStorageManager(String revision, String cacheHost, String cachePort) {
    Storage storage;
    try {
      if (cacheHost != null) {
        storage = new StorageImpl(new InetSocketAddress(cacheHost, Integer.parseInt(cachePort)));
      }
      else {
        storage = Storage.VOID;
      }
    }
    catch (IOException e) {
      throw new RuntimeException();
    }
    stateRef = new AtomicReference<>(checkoutRevision(PersistentHashMap.EMPTY, PersistentHashMap.EMPTY, storage, revision));
    localToRemote = localId -> {
      RemoteVFS.Mapping mapping = stateRef.get().vfsMapping;
      if (mapping != null) {
        return mapping.localToRemote.get(localId);
      }
      else {
        return localId;
      }
    };
    remoteToLocal = remoteId -> {
      RemoteVFS.Mapping mapping = stateRef.get().vfsMapping;
      if (mapping != null) {
        return mapping.remoteToLocal.get(remoteId);
      }
      else {
        return remoteId;
      }
    };
  }

  public static <T> T swap(AtomicReference<T> atom, Function<T, T> update) {
    synchronized (atom) {
      T t = atom.get();
      T newT = update.apply(t);
      if (!atom.compareAndSet(t, newT)) {
        throw new ConcurrentModificationException();
      }
      return newT;
    }
  }

  @SuppressWarnings("unchecked")
  public static IndexState checkoutRevision(PersistentHashMap oldInvertedIndexStorages,
                                            PersistentHashMap oldForwardIndexStorages,
                                            Storage storage,
                                            String revision) {
    final RevisionDescriptor revisionDescriptor = RevisionDescriptor.fromRevision(revision);
    final NoveltyImpl novelty = NoveltyImpl.createNovelty();

    RevisionDescriptor.Heads heads = revisionDescriptor.heads;
    RemoteVFS.Mapping mapping = null;
    if (heads != null) {
      BTree remoteVFS = BTree.load(storage, RemoteVFS.VFS_TREE_KEY_SIZE, heads.vfsHead);
      mapping = RemoteVFS.remap(FSRecords.getInstance(), remoteVFS, Novelty.VOID);
    }

    BTree forwardTree;
    if (heads != null) {
      forwardTree = BTree.load(storage, FORWARD_STORAGE_KEY_SIZE, heads.forwardIndexHead);
    } else {
      forwardTree = BTree.create(novelty.access(), storage, FORWARD_STORAGE_KEY_SIZE);
    }

    PersistentHashMap newInvertedIndexStorages = PersistentHashMap.EMPTY;
    if (heads != null) {
      for (Map.Entry<String, BTreeIndexStorage.AddressDescriptor> entry : heads.invertedIndicesHeads.entrySet()) {
        String indexId = entry.getKey();
        BTreeIndexStorage.AddressDescriptor addr = entry.getValue();
        BTreeIndexStorage old = (BTreeIndexStorage)oldInvertedIndexStorages.get(indexId);
        if (old != null) {
          newInvertedIndexStorages =
            (PersistentHashMap)newInvertedIndexStorages.assoc(indexId, old.withNewHead(novelty, addr, revisionDescriptor.R, revisionDescriptor.baseR));
        }
      }
    }

    PersistentHashMap newForwardIndexStorages = PersistentHashMap.EMPTY;
    for (Map.Entry kv : (Set<Map.Entry>)(oldForwardIndexStorages.entrySet())) {
      BTreeForwardIndexStorage indexStorage = (BTreeForwardIndexStorage)kv.getValue();
      Object indexId = kv.getKey();
      newForwardIndexStorages = (PersistentHashMap)newForwardIndexStorages.assoc(indexId, indexStorage.withTree(novelty, forwardTree));
    }

    return new IndexState(storage,
                          novelty,
                          revisionDescriptor,
                          forwardTree,
                          mapping,
                          newInvertedIndexStorages,
                          newForwardIndexStorages);
  }

  public static Map downloadIndexData(String revision) {
    String bucket = "onair-index-data";
    String region = "eu-central-1";
    try {
      InputStream stream =
        new URL("https://s3." + region + ".amazonaws.com/" + bucket + "?prefix=" + revision + "/index_meta").openStream();
      Element element = JDOMUtil.load(stream);

      List<String> files = element.getChildren().stream()
                                  .filter(e -> e.getName().equals("Contents"))
                                  .flatMap(e -> e.getChildren().stream())
                                  .filter(o -> o.getName().equals("Key"))
                                  .map(e -> e.getText())
                                  .map(s -> s.split("/")[2])
                                  .collect(Collectors.toList());

      for (String file : files) {
        String s3url = "https://s3." + region + ".amazonaws.com/" + bucket + "/" + revision + "/index_meta/" + file;
        ReadableByteChannel source = Channels.newChannel(new URL(s3url).openStream());
        File base = IndexInfrastructure.getIndexMeta();
        if (!base.exists()) {
          if (!base.mkdirs()) {
            throw new RuntimeException("can't mkdir " + base.getCanonicalPath());
          }
        }

        try (FileOutputStream fos = new FileOutputStream(new File(base, file))) {
          fos.getChannel().transferFrom(source, 0, Long.MAX_VALUE);
        }
      }

      InputStream is = new URL("https://s3." + region + ".amazonaws.com/" + bucket + "/" + revision + "/meta").openStream();

      String str = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));

      return new GsonBuilder().create().fromJson(str, Map.class);
    }
    catch (Exception e) {
      throw new RuntimeException("exception downloading index data for revision " + revision, e);
    }
  }

  @Override
  public <V> PersistentMap<Integer, V> createForwardIndexStorage(ID<?, ?> indexId, DataExternalizer<V> valueExternalizer) {
    swap(stateRef, state -> state.withNewForwardIndexStorage(indexId.getName(),
                                                             new BTreeForwardIndexStorage<V>(indexId.getUniqueId(),
                                                                                             valueExternalizer,
                                                                                             state.novelty,
                                                                                             state.forwardIndexTree)));
    return new BTreeIndexStorageManagerDelegatingPersistentMap<>(this, indexId, localToRemote, remoteToLocal);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <K, V> VfsAwareIndexStorage<K, V> createIndexStorage(ID<?, ?> indexId,
                                                              KeyDescriptor<K> keyDescriptor,
                                                              DataExternalizer<V> valueExternalizer,
                                                              int cacheSize,
                                                              boolean keyIsUniqueForIndexedFile,
                                                              boolean buildKeyHashToVirtualFileMapping) {
    swap(stateRef, state ->
      state.withNewInvertedIndexStorage(indexId.getName(),
                                        new BTreeIndexStorage<>(keyDescriptor,
                                                                valueExternalizer,
                                                                state.storage,
                                                                state.novelty,
                                                                state.revisionDescriptor.heads != null
                                                                ? state.revisionDescriptor.heads.invertedIndicesHeads.get(indexId.getName())
                                                                : null,
                                                                cacheSize,
                                                                state.revisionDescriptor.R,
                                                                state.revisionDescriptor.baseR)));

    return new BTreeIndexStorageManagerDelegatingIndexStorage<>(this, indexId, localToRemote, remoteToLocal);
  }

  public void close() throws IOException {
    stateRef.get().novelty.close();
  }
}
