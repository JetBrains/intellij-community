/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.indexing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.*;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public class VfsAwareMapReduceIndex<Key, Value, Input> extends MapReduceIndex<Key, Value, Input> implements UpdatableIndex<Key, Value, Input>{
  private static final Logger LOG = Logger.getInstance(VfsAwareMapReduceIndex.class);

  static {
    if (!DebugAssertions.DEBUG) {
      final Application app = ApplicationManager.getApplication();
      DebugAssertions.DEBUG = app.isEAP() || app.isInternal();
    }
  }

  private final AtomicBoolean myInMemoryMode = new AtomicBoolean();
  private final TIntObjectHashMap<Collection<Key>> myInMemoryKeys = new TIntObjectHashMap<Collection<Key>>();
  private final SnapshotInputMappings<Key, Value, Input> mySnapshotInputMappings;

  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                                @NotNull IndexStorage<Key, Value> storage) throws IOException {
    super(extension, storage, getForwardIndex(extension));
    SharedIndicesData.registerIndex(myIndexId, extension);
    mySnapshotInputMappings = myForwardIndex == null ?
                              new SnapshotInputMappings<>(extension) :
                              null;
    installMemoryModeListener();
  }

  public void dumpToCassandra() {
    ((IndexerIndexStorage)myStorage).dumpToCassandra();
    ((IndexerForwardIndex)myForwardIndex).dumpToCassandra();
  }

  @TestOnly
  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                                @NotNull IndexStorage<Key, Value> storage,
                                @NotNull ForwardIndex<Key, Value> forwardIndex) throws IOException {
    super(extension, storage, forwardIndex);
    SharedIndicesData.registerIndex(myIndexId, extension);
    mySnapshotInputMappings = myForwardIndex == null ?
                              new SnapshotInputMappings<>(extension) :
                              null;
    installMemoryModeListener();
  }

  @NotNull
  @Override
  protected UpdateData<Key, Value> calculateUpdateData(int inputId, @Nullable Input content) {
    Map<Key, Value> data;
    int hashId;
    final boolean isContentPhysical = isContentPhysical(content);
    if (mySnapshotInputMappings != null && content != null && isContentPhysical) {
      final SnapshotInputMappings.Snapshot<Key, Value> snapshot = mySnapshotInputMappings.readPersistentDataOrMap(content);
      data = snapshot.getData();
      hashId = snapshot.getHashId();
    } else {
      data = mapInput(content);
      hashId = 0;
    }
    return createUpdateData(data, () -> {
      if (mySnapshotInputMappings != null && isContentPhysical) {
        return new MapInputDataDiffBuilder<>(inputId, mySnapshotInputMappings.readInputKeys(inputId));
      }
      if (myInMemoryMode.get()) {
        synchronized (myInMemoryKeys) {
          Collection<Key> keys = myInMemoryKeys.get(inputId);
          if (keys != null) {
            return new CollectionInputDataDiffBuilder<>(inputId, keys);
          }
        }

        if (mySnapshotInputMappings != null) {
          return new MapInputDataDiffBuilder<>(inputId, mySnapshotInputMappings.readInputKeys(inputId));
        }
      }
      if (myForwardIndex != null) {
        return getKeysDiffBuilder(inputId);
      }
      return new EmptyInputDataDiffBuilder(inputId);
    }, () -> {
      if (myInMemoryMode.get()) {
        synchronized (myInMemoryKeys) {
          myInMemoryKeys.put(inputId, data.keySet());
        }
      } else {
        if (mySnapshotInputMappings != null ) {
          mySnapshotInputMappings.putInputHash(inputId, hashId);
        } else {
          myForwardIndex.putInputData(inputId, data);
        }
      }
    });
  }

  @Override
  public void setIndexedStateForFile(int fileId, @NotNull VirtualFile file) {
    IndexingStamp.setFileIndexedStateCurrent(fileId, myIndexId);
  }

  @Override
  public void resetIndexedStateForFile(int fileId) {
    IndexingStamp.setFileIndexedStateOutdated(fileId, myIndexId);
  }

  @Override
  public boolean isIndexedStateForFile(int fileId, @NotNull VirtualFile file) {
    return IndexingStamp.isFileIndexedStateCurrent(fileId, myIndexId);
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<Key> processor, @NotNull GlobalSearchScope scope, IdFilter idFilter) throws StorageException {
    final Lock lock = getReadLock();
    try {
      lock.lock();
      return ((VfsAwareIndexStorage<Key, Value>)myStorage).processKeys(processor, scope, idFilter);
    }
    finally {
      lock.unlock();
    }
  }

  @Override
  public void checkCanceled() {
    ProgressManager.checkCanceled();
  }

  @Override
  protected void requestRebuild(@NotNull Exception ex) {
    Runnable action = () -> FileBasedIndex.getInstance().requestRebuild(myIndexId, ex);
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      // avoid deadlock due to synchronous update in DumbServiceImpl#queueTask
      application.invokeLater(action, ModalityState.any());
    } else {
      action.run();
    }
  }

  @Override
  public void clear() throws StorageException {
    super.clear();
    if (mySnapshotInputMappings != null) try {
      mySnapshotInputMappings.clear();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public void flush() throws StorageException {
    super.flush();
    if (mySnapshotInputMappings != null) mySnapshotInputMappings.flush();
  }

  @Override
  public void dispose() {
    super.dispose();
    if (mySnapshotInputMappings != null) try {
      mySnapshotInputMappings.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static <Key, Value> ForwardIndex<Key, Value> getForwardIndex(@NotNull IndexExtension<Key, Value, ?> indexExtension)
    throws IOException {

    if (PersistentFSImpl.indexer) {
      return new IndexerForwardIndex<>(new MyForwardIndex<>(indexExtension),
                                       createInputsIndexExternalizer(indexExtension),
                                       indexExtension.getName());
    } else {
      return new ClientForwardIndex<>(new MyForwardIndex<>(indexExtension),
                                      createInputsIndexExternalizer(indexExtension),
                                      indexExtension.getName());
    }
/*
    final boolean hasSnapshotMapping = indexExtension instanceof FileBasedIndexExtension &&
                                       ((FileBasedIndexExtension<Key, Value>)indexExtension).hasSnapshotMapping() &&
                                       IdIndex.ourSnapshotMappingsEnabled;
    if (hasSnapshotMapping) return null;

    MapBasedForwardIndex<Key, Value> backgroundIndex =
      !SharedIndicesData.ourFileSharedIndicesEnabled || SharedIndicesData.DO_CHECKS ? new MyForwardIndex<>(indexExtension) : null;
    return new SharedMapBasedForwardIndex<>(indexExtension, backgroundIndex);*/
  }

  private static class MyForwardIndex<Key, Value> extends MapBasedForwardIndex<Key, Value> {
    protected MyForwardIndex(IndexExtension<Key, Value, ?> indexExtension) throws IOException {
      super(indexExtension);
    }

    @NotNull
    @Override
    public PersistentMap<Integer, Collection<Key>> createMap() throws IOException {
      return createIdToDataKeysIndex(myIndexExtension);
    }

    @NotNull
    private static <K> PersistentMap<Integer, Collection<K>> createIdToDataKeysIndex(@NotNull IndexExtension<K, ?, ?> extension) throws IOException {
      final File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(extension.getName());
      if (PersistentMap.useIndexServer) {
        return new TCPPersistentMap<>(indexStorageFile, EnumeratorIntegerDescriptor.INSTANCE, createInputsIndexExternalizer(extension));
      }
      return new PersistentHashMap<>(indexStorageFile, EnumeratorIntegerDescriptor.INSTANCE, createInputsIndexExternalizer(extension));
    }
  }

  private boolean isContentPhysical(Input content) {
    return content == null ||
           (content instanceof UserDataHolder &&
            FileBasedIndexImpl.ourPhysicalContentKey.get((UserDataHolder)content, Boolean.FALSE));
  }

  private void installMemoryModeListener() {
    IndexStorage<Key, Value> storage = getStorage();
    if (storage instanceof BufferingIndexStorage) {
      ((BufferingIndexStorage)storage).addBufferingStateListener(new BufferingIndexStorage.BufferingStateListener() {
        @Override
        public void bufferingStateChanged(boolean newState) {
          myInMemoryMode.set(newState);
        }

        @Override
        public void memoryStorageCleared() {
          synchronized (myInMemoryKeys) {
            myInMemoryKeys.clear();
          }
        }
      });
    }
  }

  protected static <K> DataExternalizer<Collection<K>> createInputsIndexExternalizer(IndexExtension<K, ?, ?> extension) {
    return extension instanceof CustomInputsIndexFileBasedIndexExtension
           ? ((CustomInputsIndexFileBasedIndexExtension<K>)extension).createExternalizer()
           : new InputIndexDataExternalizer<>(extension.getKeyDescriptor(), extension.getName());
  }
}
