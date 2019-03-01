// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.util.ObjectUtils;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.indexing.snapshot.*;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 */
public class VfsAwareMapReduceIndex<Key, Value> extends VfsAwareMapReduceIndexBase<Key, Value, FileContent>  {
  private static final Logger LOG = Logger.getInstance(VfsAwareMapReduceIndex.class);

  @Nullable
  private final AbstractSnapshotIndex<Key, Value> mySnapshotInputIndex;
  private final FileContentHasher myHasher;

  public VfsAwareMapReduceIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                @NotNull IndexStorage<Key, Value> storage) throws IOException {
    super(extension, storage, getForwardIndex(extension), getForwardIndexAccessor(extension));
    SharedIndicesData.registerIndex((ID<Key, Value>)myIndexId, extension);
    mySnapshotInputIndex = myForwardIndex == null && hasSnapshotMapping(extension) ?
                           new SnapshotInputMappings<>(extension) :
                           null;
    myHasher = new Sha1FileContentHasher(extension instanceof PsiDependentIndex);
  }

  private static <Key, Value> boolean hasSnapshotMapping(@NotNull FileBasedIndexExtension<Key, Value> indexExtension) {
    return indexExtension.hasSnapshotMapping() && IdIndex.ourSnapshotMappingsEnabled;
  }

  @NotNull
  @Override
  protected Map<Key, Value> mapInput(@Nullable FileContent content) {
    if (mySnapshotInputIndex != null) {
      try {
        if (content == null) return Collections.emptyMap();
        mySnapshotInputIndex.readSnapshot(myHasher.getEnumeratedHash(content));
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return super.mapInput(content);
  }

  @NotNull
  @Override
  public Map<Key, Value> getAssociatedMap(int fileId) throws StorageException {
    Lock lock = getReadLock();
    lock.lock();
    try {
      Map<Key, Value> map = (Map<Key, Value>)((AbstractForwardIndexAccessor<Key, Value, ?, FileContent>)myForwardIndexAccessor).getData(myForwardIndex.getInputData(fileId));
      return ObjectUtils.notNull(map, Collections.emptyMap());
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    finally {
      lock.unlock();
    }
  }

  @Override
  protected void doClear() throws StorageException, IOException {
    super.doClear();
    if (mySnapshotInputIndex != null) {
      try {
        mySnapshotInputIndex.clear();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  protected void doFlush() throws IOException, StorageException {
    super.doFlush();
    if (mySnapshotInputIndex != null) mySnapshotInputIndex.flush();
  }

  @Override
  protected void doDispose() throws StorageException {
    super.doDispose();

    if (mySnapshotInputIndex != null) {
      try {
        mySnapshotInputIndex.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  private static ForwardIndex getForwardIndex(@NotNull IndexExtension<?, ?, ?> indexExtension) throws IOException {
    return SharedIndicesData.ourFileSharedIndicesEnabled
           ? new SharedMapBasedForwardIndex(indexExtension)
           : new MapBasedForwardIndex(IndexInfrastructure.getInputIndexStorageFile((ID<?, ?>)indexExtension.getName()), false);
  }

  @NotNull
  private static <Key, Value> ForwardIndexAccessor<Key, Value, FileContent> getForwardIndexAccessor(@NotNull FileBasedIndexExtension<Key, Value> extension)
    throws IOException {
    if (hasSnapshotMapping(extension)) return new ContentHashForwardIndexAccessor<>(extension);
    if (extension instanceof CustomInputsIndexFileBasedIndexExtension) {
      return new KeyCollectionForwardIndexAccessor<>(((CustomInputsIndexFileBasedIndexExtension<Key>)extension).createExternalizer());
    }
    return new MapForwardIndexAccessorImpl<>(extension);
  }

  protected static <K> DataExternalizer<Collection<K>> createInputsIndexExternalizer(IndexExtension<K, ?, ?> extension) {
    return extension instanceof CustomInputsIndexFileBasedIndexExtension
           ? ((CustomInputsIndexFileBasedIndexExtension<K>)extension).createExternalizer()
           : new InputIndexDataExternalizer<>(extension.getKeyDescriptor(), extension.getName());
  }
}
