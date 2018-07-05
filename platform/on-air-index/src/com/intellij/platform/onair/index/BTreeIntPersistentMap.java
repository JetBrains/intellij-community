// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.index;

import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.tree.BTree;
import com.intellij.platform.onair.tree.ByteUtils;
import com.intellij.util.Processor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.PersistentMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class BTreeIntPersistentMap<V> implements PersistentMap<Integer, V> {

  private final short id;
  private final DataExternalizer<V> valueExternalizer;
  private final Novelty novelty;
  public final BTree tree;

  public BTreeIntPersistentMap(short id,
                               DataExternalizer<V> valueExternalizer,
                               @NotNull Novelty novelty,
                               @NotNull BTree tree) {
    this.id = id;
    this.valueExternalizer = valueExternalizer;
    this.novelty = novelty;
    this.tree = tree;
  }

  @Override
  public V get(Integer key) throws IOException {
    @Nullable byte[] value = tree.get(novelty, serializeKey(key));
    if (value == null) {
      return null;
    }
    else {
      return valueExternalizer.read(new DataInputStream(new ByteArrayInputStream(value)));
    }
  }

  @Override
  public void put(Integer key, V value) throws IOException {
    final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    final DataOutputStream output = new DataOutputStream(stream);
    valueExternalizer.save(output, value);
    tree.put(novelty, serializeKey(key), stream.toByteArray(), true);
  }

  @Override
  public void remove(Integer key) {
    tree.delete(novelty, serializeKey(key));
  }

  @Override
  public boolean processKeys(Processor<Integer> processor) {
    // TODO: navigate to starting key first?
    return tree.forEach(novelty, (key, value) -> {
      short currentId = (short)(ByteUtils.readUnsignedShort(key, 0) ^ 0x8000);
      if (id == currentId) {
        return processor.process((int)(ByteUtils.readUnsignedInt(key, 2) ^ 0x80000000));
      }
      return id < currentId; // exit when id is greater than ours
    });
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

  private byte[] serializeKey(Integer key) {
    final byte[] bytes = new byte[6];
    ByteUtils.writeUnsignedShort(id ^ 0x8000, bytes, 0);
    ByteUtils.writeUnsignedInt(key ^ 0x80000000, bytes, 2);
    return bytes;
  }

  @Override
  public void close() {
  }

  @Override
  public void clear() {
  }

  @Override
  public void markDirty() {
  }
}
