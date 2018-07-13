// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.index;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.platform.onair.storage.api.Address;
import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.storage.api.Storage;
import com.intellij.platform.onair.tree.BTree;
import com.intellij.platform.onair.tree.ByteUtils;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.IdFilter;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class BTreeIndexStorage<Key, Value> implements VfsAwareIndexStorage<Key, Value> {

  private final KeyDescriptor<Key> myKeyDescriptor;
  private final boolean myInlineKeys;

  private final DataExternalizer<Value> myValueExternalizer;
  private final Novelty myNovelty;
  public final LoadingCache<Key, CompositeValueContainer<Value>> myCache;
  public final BTree myTree;
  // public final BTree myHashToVirtualFile;
  public final BTree myKeysInternary;
  private final Object lockObject = new Object();

  public BTreeIndexStorage(@NotNull KeyDescriptor<Key> keyDescriptor,
                           @NotNull DataExternalizer<Value> valueExternalizer,
                           @NotNull Storage storage,
                           @NotNull Novelty novelty,
                           @Nullable BTreeIndexStorage.AddressDescriptor head,
                           int cacheSize,
                           int R,
                           int baseR) {
    myNovelty = novelty.unsynchronizedCopy();

    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
    myInlineKeys = keyDescriptor instanceof InlineKeyDescriptor;

    synchronized (lockObject) {
      if (head != null) {
        myTree = BTree.load(storage, 8, head.data);
        // myHashToVirtualFile = BTree.load(storage, 4, head.hashToVirtualFile);
      }
      else {
        myTree = BTree.create(myNovelty, storage, 8);
        // myHashToVirtualFile = BTree.create(novelty, storage, 4);
      }
      if (!myInlineKeys) {
        if (head != null) {
          myKeysInternary = BTree.load(storage, 4, head.internary);
        }
        else {
          myKeysInternary = BTree.create(myNovelty, storage, 4);
        }
      }
      else {
        myKeysInternary = null;
      }
    }

    myCache = CacheBuilder
      .newBuilder()
      .maximumSize(cacheSize * 10)
      .<Key, CompositeValueContainer<Value>>removalListener(notification -> {
        final Key key = notification.getKey();
        final CompositeValueContainer<Value> valueContainer = notification.getValue();
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

            if (myInlineKeys) {
              int keyInt = ((InlineKeyDescriptor<Key>)myKeyDescriptor).toInt(key);
              byte[] serializedKey = new byte[4 + 4];
              ByteUtils.writeUnsignedInt(keyInt ^ 0x80000000, serializedKey, 4);
              setR(serializedKey, R);
              myTree.put(myNovelty, serializedKey, valueBytes.toByteArray(), true);
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

              final byte[] serializedKey = new byte[4 + 4];
              int keyInt = myKeyDescriptor.getHashCode(key);
              ByteUtils.writeUnsignedInt(keyInt ^ 0x80000000, serializedKey, 4);
              setR(serializedKey, R);

              myTree.put(myNovelty, serializedKey, valueBytes.toByteArray(), true);

              final byte[] hashKey = Arrays.copyOfRange(serializedKey, 4, 8);
              // intern based on 128 bit hash happens on BTree value level
              // TODO: consider incorporating it into the novelty (to save space on indexing)
              myKeysInternary.put(myNovelty, hashKey, keyBytes, false);
            }
          }
        }
      })
      .build(new CacheLoader<Key, CompositeValueContainer<Value>>() {
        @Override
        public CompositeValueContainer<Value> load(@NotNull Key key) {
          final byte[] keyBytes;
          if (myInlineKeys) {
            keyBytes = toIntKey(key);
          }
          else {
            keyBytes = toHashIntKey(key);
          }
          DeltaValueContainer<Value> delta = new DeltaValueContainer<>();
          synchronized (lockObject) {
            setR(keyBytes, R);
            final byte[] valueBytes = myTree.get(myNovelty, keyBytes);
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
            synchronized (lockObject) {
              setR(keyBytes, baseR);
              final byte[] baseValueBytes = myTree.get(myNovelty, keyBytes);
              if (baseValueBytes != null) {
                try {
                  container.readFrom(new DataInputStream(new ByteArrayInputStream(baseValueBytes)), myValueExternalizer);
                }
                catch (IOException e) {
                  throw new RuntimeException(e);
                }
              }
            }
            return container;
          }, delta);
        }
      });
  }

  @Override
  public boolean processKeys(@NotNull Processor<Key> processor, GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    synchronized (lockObject) {
      myCache.invalidateAll(); // force all data from cache to the BTree like in VfsAwareMapIndexStorage
      // TODO: pass by StorageException instead of assert
      if (myInlineKeys) {
        return myTree.forEach(myNovelty, (key, value) -> processor.process(extractIntKey(key)));
      }
      else {
        // TODO: merge this in a more efficient way
        return myTree.forEach(myNovelty, (key, value) -> processor.process(extractKey(key)));
      }
    }
  }

  @Override
  public void addValue(Key key, int inputId, Value value) {
    /* synchronized (lockObject) {
      myHashToVirtualFile.put(myNovelty, myKeyDescriptor.getHashCode(key), inputId);
    }*/
    CompositeValueContainer<Value> container;
    try {
      container = myCache.get(key);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
    container.addValue(inputId, value);
  }

  @Override
  public void removeAllValues(@NotNull Key key, int inputId) {
    CompositeValueContainer<Value> container;
    try {
      container = myCache.get(key);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
    container.removeAssociatedValue(inputId);
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException("clear");
  }

  @NotNull
  @Override
  public ValueContainer<Value> read(Key key) {
    try {
      return myCache.get(key);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void clearCaches() {
    myCache.invalidateAll();
  }

  @Override
  public void close() {

  }

  @Override
  public void flush() {

  }

  private Key extractIntKey(byte[] keyHash) {
    return ((InlineKeyDescriptor<Key>)myKeyDescriptor).fromInt((int)(ByteUtils.readUnsignedInt(keyHash, 4) ^ 0x80000000));
  }

  private Key extractKey(byte[] keyHash) {
    byte[] keyBytes = myKeysInternary.get(myNovelty, Arrays.copyOfRange(keyHash, 4, 8));
    try {
      assert keyBytes != null;
      return myKeyDescriptor.read(new DataInputStream(new ByteArrayInputStream(keyBytes)));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] toIntKey(Key key) {
    int intKey = ((InlineKeyDescriptor<Key>)myKeyDescriptor).toInt(key);
    byte[] res = new byte[4 + 4];
    ByteUtils.writeUnsignedInt(intKey ^ 0x80000000, res, 4);
    return res;
  }

  private byte[] toHashIntKey(Key key) {
    int intKey = myKeyDescriptor.getHashCode(key);
    byte[] res = new byte[4 + 4];
    ByteUtils.writeUnsignedInt(intKey ^ 0x80000000, res, 4);
    return res;
  }

  private static void setR(byte[] key, int R) {
    ByteUtils.writeUnsignedInt(R ^ 0x80000000, key, 0);
  }

  public static final class AddressDescriptor {
    public final Address internary;
    @NotNull
    public final Address data;
    /*@NotNull
    public final Address hashToVirtualFile;*/

    public AddressDescriptor(@Nullable Address internary, @NotNull Address data/*, @NotNull Address hashToVirtualFile*/) {
      this.internary = internary;
      this.data = data;
      // this.hashToVirtualFile = hashToVirtualFile;
    }
  }

  public static final class CompositeValueContainer<V> extends UpdatableValueContainer<V> {

    final Computable<ValueContainerImpl<V>> myBaseLoader;
    final DeltaValueContainer<V> myDelta;
    ValueContainerImpl<V> myMerged;

    public ValueContainerImpl<V> getMerged() {
      if (myMerged != null) {
        return myMerged;
      }
      ValueContainerImpl<V> base = myBaseLoader.compute();
      InvertedIndexValueIterator<V> valueIterator = myDelta.getValueIterator();
      while (valueIterator.hasNext()) {
        V value = valueIterator.next();
        IntIterator inputIdsIterator = valueIterator.getInputIdsIterator();
        while (inputIdsIterator.hasNext()) {
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
      if (myMerged != null) {
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
    public void saveTo(DataOutput out, DataExternalizer<V> externalizer) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ValueContainerImpl<V> getModifiableCopy() {
      return getMerged().getModifiableCopy();
    }

    @NotNull
    @Override
    public ValueIterator<V> getValueIterator() {
      return getMerged().getValueIterator();
    }

    @Override
    public int size() {
      return getMerged().size();
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
}
