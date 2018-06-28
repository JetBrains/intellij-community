// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.index;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.Processor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.PersistentMap;
import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Novelty;
import intellij.platform.onair.storage.api.Storage;
import intellij.platform.onair.tree.BTree;
import intellij.platform.onair.tree.ByteUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class BTreeIntPersistentMap<V> implements PersistentMap<Integer, V> {

  private final DataExternalizer<V> myExternalizer;
  private final Novelty myNovelty;
  private final BTree myTree;

  public BTreeIntPersistentMap(DataExternalizer<V> valueExternalizer,
                               @NotNull Storage storage,
                               @NotNull Novelty novelty,
                               @Nullable Address head) {
    myExternalizer = valueExternalizer;
    myNovelty = novelty;
    if (head == null) {
      myTree = BTree.create(novelty, storage, 4);
    } else {
      myTree = BTree.load(storage, 4, head);
    }
  }

  @Override
  public V get(Integer key) throws IOException {
    @Nullable byte[] value = myTree.get(myNovelty, ByteUtils.toBytes(key));
    if (value == null) {
      return null;
    } else {
      return myExternalizer.read(new DataInputStream(new ByteArrayInputStream(value)));
    }
  }

  @Override
  public void put(Integer key, V value) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream s = new DataOutputStream(baos);
    myExternalizer.save(s, value);
    myTree.put(myNovelty, ByteUtils.toBytes(key), baos.toByteArray(), true);
  }

  @Override
  public void remove(Integer key) throws IOException {
    myTree.delete(myNovelty, ByteUtils.toBytes(key));
  }

  @Override
  public boolean processKeys(Processor<Integer> processor) throws IOException {
    return myTree.forEach(myNovelty, (key, value) -> processor.process((int)ByteUtils.readUnsignedInt(key, 0)));
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
