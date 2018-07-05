// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.index;

import com.intellij.util.Processor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.PersistentMap;
import com.intellij.platform.onair.storage.api.Address;
import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.storage.api.Storage;
import com.intellij.platform.onair.tree.BTree;
import com.intellij.platform.onair.tree.ByteUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class BTreeIntPersistentMap<V> implements PersistentMap<Integer, V> {

  private final DataExternalizer<V> valueExternalizer;
  private final Novelty novelty;
  public final BTree tree;

  public BTreeIntPersistentMap(DataExternalizer<V> valueExternalizer,
                               @NotNull Storage storage,
                               @NotNull Novelty novelty,
                               @Nullable Address head) {
    this.valueExternalizer = valueExternalizer;
    this.novelty = novelty;
    if (head == null) {
      tree = BTree.create(novelty, storage, 4);
    }
    else {
      tree = BTree.load(storage, 4, head);
    }
  }

  @Override
  public V get(Integer key) throws IOException {
    @Nullable byte[] value = tree.get(novelty, ByteUtils.toBytes(key));
    if (value == null) {
      return null;
    }
    else {
      return valueExternalizer.read(new DataInputStream(new ByteArrayInputStream(value)));
    }
  }

  @Override
  public void put(Integer key, V value) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream s = new DataOutputStream(baos);
    valueExternalizer.save(s, value);
    tree.put(novelty, ByteUtils.toBytes(key), baos.toByteArray(), true);
  }

  @Override
  public void remove(Integer key) throws IOException {
    tree.delete(novelty, ByteUtils.toBytes(key));
  }

  @Override
  public boolean processKeys(Processor<Integer> processor) throws IOException {
    return tree.forEach(novelty, (key, value) -> processor.process((int)(ByteUtils.readUnsignedInt(key, 0) ^ 0x80000000)));
  }

  @Override
  public boolean isClosed() {
    return false;
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public void force() {

  }

  @Override
  public void close() throws IOException {

  }

  @Override
  public void clear() throws IOException {

  }

  @Override
  public void markDirty() throws IOException {

  }
}
