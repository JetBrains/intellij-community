// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.index;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.onair.storage.StorageImpl;
import com.intellij.platform.onair.storage.api.Address;
import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.storage.api.NoveltyImpl;
import com.intellij.platform.onair.storage.api.Storage;
import com.intellij.platform.onair.tree.BTree;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IndexInfrastructure;
import com.intellij.util.indexing.IndexStorageManager;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BTreeIndexStorageManager implements IndexStorageManager {

  public final Storage storage;
  public final ConcurrentHashMap<String, BTreeIndexStorage> indexStorages = new ConcurrentHashMap<>();
  public final ConcurrentHashMap<String, BTreeIntPersistentMap> forwardStorages = new ConcurrentHashMap<>();
  public final Novelty indexNovelty;
  public final Map indexHeads;

  public BTreeIndexStorageManager() {
    try {
      String revision = System.getProperty("onair.index.revision");
      String cacheHost = System.getProperty("onair.index.cache.host");
      String cachePort = System.getProperty("onair.index.cache.port", "11211");
      if (cacheHost != null) {
        storage = new StorageImpl(new InetSocketAddress(cacheHost, Integer.parseInt(cachePort)));
      } else {
        storage = new Storage() {
          @Override
          public @NotNull byte[] lookup(@NotNull Address address) {
            return new byte[0];
          }

          @Override
          public @NotNull Address alloc(@NotNull byte[] what) {
            return null;
          }

          @Override
          public void prefetch(@NotNull Address address, @NotNull byte[] bytes, @NotNull BTree tree, int size, byte type) {

          }

          @Override
          public void store(@NotNull Address address, @NotNull byte[] bytes) {

          }
        };
      }

      if (revision != null && !revision.trim().isEmpty()) {
        indexHeads = downloadIndexData(revision);
      } else {
        indexHeads = null;
      }
      indexNovelty = new NoveltyImpl(FileUtil.createTempFile("novelty-", ".here"));
    }
    catch (IOException e) {
      throw new RuntimeException();
    }
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
        base.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(new File(base, file))) {
          fos.getChannel().transferFrom(source, 0, Long.MAX_VALUE);
        }
      }

      InputStream is = new URL(
        "https://s3." + region + ".amazonaws.com/" + bucket + "/" + revision + "/meta").openStream();

      String str = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));

      return new GsonBuilder().create().fromJson(str, Map.class);
    }
    catch (Exception e) {
      throw new RuntimeException("exception downloading index data for revision " + revision, e);
    }
  }

  @Override
  public <V> PersistentMap<Integer, V> createForwardIndexStorage(ID<?, ?> indexId, DataExternalizer<V> valueExternalizer) {
    Address head = null;
    if (indexHeads != null) {
      List addr = (List)((Map)(indexHeads.get("forward-indices"))).get(indexId.getName());

      head = new Address(Long.parseLong((String)addr.get(1)),
                         Long.parseLong((String)addr.get(0)));
    }
    BTreeIntPersistentMap<V> map = new BTreeIntPersistentMap<>(valueExternalizer, storage, indexNovelty, head);
    forwardStorages.put(indexId.getName(), map);
    return map;
  }

  @Override
  public <K, V> VfsAwareIndexStorage<K, V> createIndexStorage(ID<?, ?> indexId,
                                                              KeyDescriptor<K> keyDescriptor,
                                                              DataExternalizer<V> valueExternalizer,
                                                              int cacheSize,
                                                              boolean keyIsUniqueForIndexedFile,
                                                              boolean buildKeyHashToVirtualFileMapping) {
    BTreeIndexStorage.AddressPair address;
    int newRevision = 17;
    int baseRevision = -1;
    if (indexHeads != null) {
      Map m = (Map)(((Map)indexHeads.get("inverted-indices")).get(indexId.getName()));
      List invertedAddr = (List)m.get("inverted");
      List internaryAddr = (List)m.get("internary");
      Address internary = internaryAddr != null ? new Address(Long.parseLong((String)internaryAddr.get(1)),
                                                              Long.parseLong((String)internaryAddr.get(0))) : null;
      address = new BTreeIndexStorage.AddressPair(internary, new Address(Long.parseLong((String)invertedAddr.get(1)),
                                                                         Long.parseLong((String)invertedAddr.get(0))));
      baseRevision = Integer.parseInt((String)indexHeads.get("revision-int"));
    }
    else {
      address = null;
    }
    BTreeIndexStorage<K, V> indexStorage =
      new BTreeIndexStorage<>(keyDescriptor,
                              valueExternalizer,
                              storage,
                              indexNovelty,
                              address,
                              cacheSize,
                              newRevision,
                              baseRevision);
    indexStorages.put(indexId.getName(), indexStorage);
    return indexStorage;
  }
}
