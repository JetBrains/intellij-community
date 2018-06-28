// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.index;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.indexing.impl.InvertedIndexValueIterator;
import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.InlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.TIntHashSet;
import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Novelty;
import intellij.platform.onair.storage.api.Storage;
import intellij.platform.onair.tree.BTree;
import intellij.platform.onair.tree.ByteUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Arrays;

public class BTreeIndexStorage<Key, Value> implements VfsAwareIndexStorage<Key, Value> {

  private final static HashFunction HASH = Hashing.goodFastHash(128);

  private final KeyDescriptor<Key> myKeyDescriptor;
  private final DataExternalizer<Value> myValueExternalizer;
  private final Novelty myNovelty;
  private BTree myTree;
  private BTree myKeysInternary;

  protected SLRUCache<Key, CompositeValueContainer<Value>> myCache;

  public static class AddressPair {
    public final Address internary;
    public final @NotNull Address data;

    public AddressPair(@Nullable Address internary, @NotNull Address data) {
      this.internary = internary;
      this.data = data;
    }
  }

  static class CompositeValueContainer<V> extends UpdatableValueContainer<V> {

    final Computable<ValueContainerImpl<V>> myBaseLoader;
    final DeltaValueContainer<V> myDelta;
    ValueContainerImpl<V> myMerged;

    private ValueContainer<V> getBase() {
      if (myMerged != null) {
        return myMerged;
      }
      ValueContainerImpl<V> base = myBaseLoader.compute();
      InvertedIndexValueIterator<V> valueIterator = myDelta.getValueIterator();
      while(valueIterator.hasNext()){
        V value = valueIterator.next();
        IntIterator inputIdsIterator = valueIterator.getInputIdsIterator();
        while (inputIdsIterator.hasNext()){
          int input = inputIdsIterator.next();
          base.addValue(input, value);
        }
      }
      myDelta.removed.forEach(input -> {
         base.removeAssociatedValue(input);
         return true;
      });
      myMerged = base;
      return myMerged;
    }

    CompositeValueContainer(Computable<ValueContainerImpl<V>> base, DeltaValueContainer<V> delta) {
      myBaseLoader = base;
      myDelta = delta;
    }

    @Override
    public void addValue(int inputId, V value) {
      if (myMerged != null){
        myMerged.addValue(inputId, value);
      }
      myDelta.addValue(inputId, value);
    }

    @Override
    public void removeAssociatedValue(int inputId) {
      if (myMerged != null) {
        myMerged.removeAssociatedValue(inputId);
      }
      myDelta.removeAssociatedValue(inputId);
    }

    @Override
    public void saveTo(DataOutput out, DataExternalizer<V> externalizer) throws IOException {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public ValueIterator<V> getValueIterator() {
      return getBase().getValueIterator();
    }

    @Override
    public int size() {
      return getBase().size();
    }
  }

  static class DeltaValueContainer<V> extends ValueContainerImpl<V> {
    final TIntHashSet removed = new TIntHashSet();
    boolean dirty = false;

    @Override
    public void addValue(int inputId, V value) {
      super.addValue(inputId, value);
      removed.remove(inputId);
      dirty = true;
    }

    @Override
    public void removeAssociatedValue(int inputId) {
      super.removeAssociatedValue(inputId);
      removed.add(inputId);
      dirty = true;
    }

    @Override
    public void saveTo(DataOutput out, DataExternalizer<V> externalizer) throws IOException {
      removed.forEach(value -> {
        try {
          DataInputOutputUtil.writeINT(out, -value);
          return true;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
      super.saveTo(out, externalizer);
    }
  }

  byte[] toTreeKey(Key k) {
    if (myKeyDescriptor instanceof InlineKeyDescriptor<?>) {
      int key = ((InlineKeyDescriptor<Key>)myKeyDescriptor).toInt(k);
      byte[] res = new byte[4 + 4];
      ByteUtils.writeUnsignedInt(key, res, 4);
      return res;
    }
    else {
      BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream();
      try {
        myKeyDescriptor.save(new DataOutputStream(stream), k);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      byte[] res = new byte[16 + 4];
      HASH.hashBytes(stream.getInternalBuffer(), 0, stream.size()).writeBytesTo(res, 4, 16);
      return res;
    }
  }

  void setR(byte[] key, int R) {
    ByteUtils.writeUnsignedInt(R, key, 0);
  }

  public BTreeIndexStorage(@NotNull KeyDescriptor<Key> keyDescriptor,
                           @NotNull DataExternalizer<Value> valueExternalizer,
                           @NotNull Storage storage,
                           @NotNull Novelty novelty,
                           @Nullable AddressPair head,
                           int cacheSize,
                           int R,
                           int baseR) {
    myNovelty = novelty;
    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
    int keySize = (keyDescriptor instanceof InlineKeyDescriptor ? 4 : 16) + 4;
    if (head != null) {
      myTree = BTree.load(storage, keySize, head.data);
    }
    else {
      myTree = BTree.create(novelty, storage, keySize);
    }
    if (!(keyDescriptor instanceof InlineKeyDescriptor)) {
      if (head != null) {
        myKeysInternary = BTree.load(storage, 16, head.internary);
      }
      else {
        myKeysInternary = BTree.create(novelty, storage, 16);
      }
    }
    Object lockObject = new Object();

    myCache = new SLRUCache<Key, CompositeValueContainer<Value>>(cacheSize, (int)(Math.ceil(
      cacheSize * 0.25)) /* 25% from the main cache size*/) {
      @Override
      @NotNull
      public CompositeValueContainer<Value> createValue(final Key key) {
        final byte[] keyBytes = toTreeKey(key);
        DeltaValueContainer<Value> delta = new DeltaValueContainer<>();
        synchronized (lockObject) {
          setR(keyBytes, R);
          byte[] valueBytes = myTree.get(novelty, keyBytes);
          if (valueBytes != null) {
            try {
              delta.readFrom(new DataInputStream(new ByteArrayInputStream(valueBytes)), myValueExternalizer);
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }

        return new CompositeValueContainer<>(() -> {
          ValueContainerImpl<Value> container = new ValueContainerImpl<>();
          setR(keyBytes, baseR);
          byte[] baseValueBytes = myTree.get(novelty, keyBytes);
          if (baseValueBytes != null) {
            try {
              container.readFrom(new DataInputStream(new ByteArrayInputStream(baseValueBytes)), myValueExternalizer);
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
          return container;
        }, delta);
      }

      @Override
      protected void onDropFromCache(final Key key, @NotNull final CompositeValueContainer<Value> valueContainer) {
        if (valueContainer.myDelta.dirty) {
          synchronized (lockObject) {
            BufferExposingByteArrayOutputStream valueBytes = new BufferExposingByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(valueBytes);
            try {
              valueContainer.myDelta.saveTo(dos, myValueExternalizer);
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }

            if (myKeyDescriptor instanceof InlineKeyDescriptor<?>) {
              int keyInt = ((InlineKeyDescriptor<Key>)myKeyDescriptor).toInt(key);
              byte[] resKey = new byte[4 + 4];
              ByteUtils.writeUnsignedInt(keyInt, resKey, 4);
              setR(resKey, R);
              myTree.put(novelty, resKey, valueBytes.toByteArray(), true);
            }
            else {
              BufferExposingByteArrayOutputStream stream = new BufferExposingByteArrayOutputStream();
              try {
                myKeyDescriptor.save(new DataOutputStream(stream), key);
              }
              catch (IOException e) {
                throw new RuntimeException(e);
              }
              byte[] keyBytes = stream.toByteArray();
              byte[] res = new byte[16 + 4];
              byte[] keyHashBytes = HASH.hashBytes(keyBytes).asBytes();
              System.arraycopy(keyHashBytes, 0, res, 4, 16);
              setR(res, R);

              myTree.put(novelty, res, valueBytes.toByteArray(), true);
              myKeysInternary.put(novelty, keyHashBytes, keyBytes, false);
            }
          }
        }
      }
    };
  }

  @Override
  public boolean processKeys(@NotNull Processor<Key> processor, GlobalSearchScope scope, @Nullable IdFilter idFilter)
    throws StorageException {
    return myTree.forEach(myNovelty, (key, value) -> processor.process(extractKey(key)));
  }

  private Key extractKey(byte[] key) {
    if (myKeyDescriptor instanceof InlineKeyDescriptor) {
      return ((InlineKeyDescriptor<Key>)myKeyDescriptor).fromInt((int)ByteUtils.readUnsignedInt(key, 4));
    } else {
      byte[] keyBytes = myKeysInternary.get(myNovelty, Arrays.copyOfRange(key, 4, 20));
      try {
        assert keyBytes != null;
        return myKeyDescriptor.read(new DataInputStream(new ByteArrayInputStream(keyBytes)));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void addValue(Key key, int inputId, Value value) throws StorageException {
    CompositeValueContainer<Value> container = myCache.get(key);
    container.addValue(inputId, value);
  }

  @Override
  public void removeAllValues(@NotNull Key key, int inputId) throws StorageException {
    CompositeValueContainer<Value> container = myCache.get(key);
    container.removeAssociatedValue(inputId);
  }

  @Override
  public void clear() throws StorageException {
    throw new UnsupportedOperationException("clear");
  }

  @NotNull
  @Override
  public ValueContainer<Value> read(Key key) throws StorageException {
    return myCache.get(key);
  }

  @Override
  public void clearCaches() {

  }

  @Override
  public void close() throws StorageException {

  }

  @Override
  public void flush() throws IOException {

  }
}
