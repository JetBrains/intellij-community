// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.indexing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.indexing.snapshot.AbstractSnapshotIndex;
import com.intellij.util.indexing.snapshot.FileContentHasher;
import com.intellij.util.indexing.snapshot.Sha1FileContentHasher;
import com.intellij.util.indexing.snapshot.SnapshotInputMappings;
import com.intellij.util.io.*;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 */
public class VfsAwareMapReduceIndex<Key, Value> extends VfsAwareMapReduceIndexBase<Key, Value, FileContent>  {
  private static final Logger LOG = Logger.getInstance(VfsAwareMapReduceIndex.class);

  @Nullable
  private final AbstractSnapshotIndex<Key, Value> mySnapshotInputIndex;
  private final FileContentHasher myHasher;

  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, FileContent> extension,
                                @NotNull IndexStorage<Key, Value> storage) throws IOException {
    this(extension, storage, getForwardIndex(extension), getForwardIndexAccessor(extension));
    if (!(myIndexId instanceof ID<?, ?>)) {
      throw new IllegalArgumentException("myIndexId should be instance of com.intellij.util.indexing.ID");
    }
  }

  @Nullable
  private static <Key, Value, Input> ForwardIndexAccessor<Key, Value, Input> getForwardIndexAccessor(@NotNull IndexExtension<Key, Value, Input> extension) {
    if (hasSnapshotMapping(extension)) return null;
    if (extension instanceof CustomInputsIndexFileBasedIndexExtension) {
      return new KeyCollectionForwardIndexAccessor<>(((CustomInputsIndexFileBasedIndexExtension<Key>)extension).createExternalizer());
    }
    return new MapForwardIndexAccessorImpl<>(extension);
  }

  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, FileContent> extension,
                                @NotNull IndexStorage<Key, Value> storage,
                                @Nullable ForwardIndex forwardIndex,
                                @Nullable ForwardIndexAccessor<Key, Value, FileContent> forwardIndexAccessor) throws IOException {
    super(extension, storage, forwardIndex, forwardIndexAccessor);
    SharedIndicesData.registerIndex((ID<Key, Value>)myIndexId, extension);
    mySnapshotInputIndex = myForwardIndex == null && hasSnapshotMapping(extension) ?
                           new SnapshotInputMappings<>(extension) :
                           null;
    myHasher = new Sha1FileContentHasher(extension instanceof PsiDependentIndex);
  }

  private static <Key, Value> boolean hasSnapshotMapping(@NotNull IndexExtension<Key, Value, ?> indexExtension) {
    return indexExtension instanceof FileBasedIndexExtension &&
           ((FileBasedIndexExtension<Key, Value>)indexExtension).hasSnapshotMapping() &&
           IdIndex.ourSnapshotMappingsEnabled;
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
      Map<Key, Value> map;
      if (mySnapshotInputIndex != null) {
        map = mySnapshotInputIndex.readSnapshot(fileId);
      } else {
        map = ((AbstractForwardIndexAccessor<Key, Value, Map<Key, Value>, FileContent>)myForwardIndexAccessor).getData(myForwardIndex.getInputData(fileId));
      }
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

  @Nullable
  private static ForwardIndex getForwardIndex(@NotNull IndexExtension<?, ?, ?> indexExtension) throws IOException {
    if (hasSnapshotMapping(indexExtension)) return null;
    return SharedIndicesData.ourFileSharedIndicesEnabled
           ? new SharedMapBasedForwardIndex(indexExtension)
           : new MapBasedForwardIndex(IndexInfrastructure.getInputIndexStorageFile((ID<?, ?>)indexExtension.getName()), false);
  }


  protected static <K> DataExternalizer<Collection<K>> createInputsIndexExternalizer(IndexExtension<K, ?, ?> extension) {
    return extension instanceof CustomInputsIndexFileBasedIndexExtension
           ? ((CustomInputsIndexFileBasedIndexExtension<K>)extension).createExternalizer()
           : new InputIndexDataExternalizer<>(extension.getKeyDescriptor(), extension.getName());
  }
}
