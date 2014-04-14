/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.io.*;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public class MapReduceIndex<Key, Value, Input> implements UpdatableIndex<Key,Value, Input> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapReduceIndex");
  private static final int NULL_MAPPING = 0;
  @Nullable private final ID<Key, Value> myIndexId;
  private final DataIndexer<Key, Value, Input> myIndexer;
  @NotNull protected final IndexStorage<Key, Value> myStorage;
  private final boolean myHasSnapshotMapping;
  private final DataExternalizer<Collection<Key>> mySnapshotIndexExternalizer;

  private PersistentHashMap<Integer, Collection<Key>> myInputsIndex;
  private PersistentHashMap<Integer, Collection<Key>> mySnapshotMapping;
  private PersistentHashMap<Integer, Integer> myInputsSnapshotMapping;

  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();

  private Factory<PersistentHashMap<Integer, Collection<Key>>> myInputsIndexFactory;

  public MapReduceIndex(@Nullable final ID<Key, Value> indexId,
                        DataIndexer<Key, Value, Input> indexer,
                        @NotNull IndexStorage<Key, Value> storage) {
    this(indexId, indexer, storage, null);
  }

  public MapReduceIndex(@Nullable final ID<Key, Value> indexId,
                        DataIndexer<Key, Value, Input> indexer,
                        @NotNull IndexStorage<Key, Value> storage,
                        DataExternalizer<Collection<Key>> snapshotIndexExternalizer) {
    myIndexId = indexId;
    myIndexer = indexer;
    myStorage = storage;
    myHasSnapshotMapping = snapshotIndexExternalizer != null;
    mySnapshotIndexExternalizer = snapshotIndexExternalizer;
  }

  @NotNull
  public IndexStorage<Key, Value> getStorage() {
    return myStorage;
  }

  @Override
  public void clear() throws StorageException {
    try {
      getWriteLock().lock();
      myStorage.clear();
      if (myInputsIndex != null) {
        cleanMapping(myInputsIndex);
        myInputsIndex = createInputsIndex();
      }
      if (myInputsSnapshotMapping != null) {
        cleanMapping(myInputsSnapshotMapping);
        myInputsSnapshotMapping = createInputSnapshotMapping();
      }
      if (mySnapshotMapping != null) {
        cleanMapping(mySnapshotMapping);
        mySnapshotMapping = createSnapshotMappingIndex();
      }
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      getWriteLock().unlock();
    }
  }

  private PersistentHashMap<Integer, Integer> createInputSnapshotMapping() throws IOException {
    assert myIndexId != null;
    final File fileIdToHashIdFile = new File(IndexInfrastructure.getIndexRootDir(myIndexId), "fileIdToHashId");
    return IOUtil.openCleanOrResetBroken(new ThrowableComputable<PersistentHashMap<Integer, Integer>, IOException>() {
      @Override
      public PersistentHashMap<Integer, Integer> compute() throws IOException {
        return new PersistentHashMap<Integer, Integer>(fileIdToHashIdFile, EnumeratorIntegerDescriptor.INSTANCE, EnumeratorIntegerDescriptor.INSTANCE, 4096) {
          @Override
          protected boolean wantCompactIntegralValues() {
            return true;
          }
        };
      }
    }, fileIdToHashIdFile);
  }

  private static void cleanMapping(@NotNull PersistentHashMap<?, ?> index) {
    final File baseFile = index.getBaseFile();
    try {
      index.close();
    }
    catch (IOException ignored) {
    }

    FileUtil.delete(baseFile);
  }

  @Override
  public void flush() throws StorageException{
    try {
      getReadLock().lock();
      doForce(myInputsIndex);
      doForce(myInputsSnapshotMapping);
      doForce(mySnapshotMapping);
      myStorage.flush();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        throw new StorageException(cause);
      }
      else {
        throw e;
      }
    }
    finally {
      getReadLock().unlock();
    }
  }

  private static void doForce(PersistentHashMap<?, ?> inputsIndex) {
    if (inputsIndex != null && inputsIndex.isDirty()) {
      inputsIndex.force();
    }
  }

  @Override
  public void dispose() {
    final Lock lock = getWriteLock();
    try {
      lock.lock();
      try {
        myStorage.close();
      }
      finally {
        doClose(myInputsIndex);
        doClose(myInputsSnapshotMapping);
        doClose(mySnapshotMapping);
      }
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    finally {
      lock.unlock();
    }
  }

  private static void doClose(PersistentHashMap<?, ?> index) {
    if (index != null) {
      try {
        index.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  @Override
  public final Lock getReadLock() {
    return myLock.readLock();
  }

  @NotNull
  @Override
  public final Lock getWriteLock() {
    return myLock.writeLock();
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<Key> processor, @NotNull GlobalSearchScope scope, IdFilter idFilter) throws StorageException {
    final Lock lock = getReadLock();
    try {
      lock.lock();
      return myStorage.processKeys(processor, scope, idFilter);
    }
    finally {
      lock.unlock();
    }
  }

  @Override
  @NotNull
  public ValueContainer<Value> getData(@NotNull final Key key) throws StorageException {
    final Lock lock = getReadLock();
    try {
      lock.lock();
      return myStorage.read(key);
    }
    finally {
      lock.unlock();
    }
  }

  public void setInputIdToDataKeysIndex(Factory<PersistentHashMap<Integer, Collection<Key>>> factory) throws IOException {
    myInputsIndexFactory = factory;
    if (myHasSnapshotMapping) {
      myInputsSnapshotMapping = createInputSnapshotMapping();
      mySnapshotMapping = createSnapshotMappingIndex();
    }
    myInputsIndex = createInputsIndex();
  }

  private PersistentHashMap<Integer, Collection<Key>> createSnapshotMappingIndex() throws IOException {
    assert myIndexId != null;
    final File hashIdToKeysFile = new File(IndexInfrastructure.getIndexRootDir(myIndexId), "hashIdToKeys");
    return IOUtil.openCleanOrResetBroken(new ThrowableComputable<PersistentHashMap<Integer, Collection<Key>>, IOException>() {
      @Override
      public PersistentHashMap<Integer, Collection<Key>> compute() throws IOException {
        return new PersistentHashMap<Integer, Collection<Key>>(hashIdToKeysFile, EnumeratorIntegerDescriptor.INSTANCE, mySnapshotIndexExternalizer);
      }
    }, hashIdToKeysFile);
  }

  @Nullable
  private PersistentHashMap<Integer, Collection<Key>> createInputsIndex() throws IOException {
    Factory<PersistentHashMap<Integer, Collection<Key>>> factory = myInputsIndexFactory;
    if (factory != null) {
      try {
        return factory.create();
      }
      catch (RuntimeException e) {
        if (e.getCause() instanceof IOException) {
          throw (IOException)e.getCause();
        }
        throw e;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public final Computable<Boolean> update(final int inputId, @Nullable final Input content) {

    final Map<Key, Value> data = content != null ? myIndexer.map(content) : Collections.<Key, Value>emptyMap();

    ProgressManager.checkCanceled();

    final NotNullComputable<Collection<Key>> oldKeysGetter;
    final int savedInputId;

    if (myHasSnapshotMapping && !((MemoryIndexStorage)getStorage()).isBufferingEnabled()) {
      oldKeysGetter = new NotNullComputable<Collection<Key>>() {
        @NotNull
        @Override
        public Collection<Key> compute() {
          try {
            Integer hashId = myInputsSnapshotMapping.get(inputId);
            Collection<Key> keys = hashId != null ? mySnapshotMapping.get(hashId): null;
            return keys == null ? Collections.<Key>emptyList() : keys;
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };
      try {
        if (content instanceof FileContent) {
          FileContent fileContent = (FileContent)content;
          savedInputId = ContentHashesSupport.calcContentHashIdWithFileType(fileContent.getContent(), fileContent.getFileType());
        } else {
          savedInputId = NULL_MAPPING;
        }
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    } else {
      oldKeysGetter = new NotNullComputable<Collection<Key>>() {
        @NotNull
        @Override
        public Collection<Key> compute() {
          try {
            Collection<Key> oldKeys = myInputsIndex.get(inputId);
            return oldKeys == null? Collections.<Key>emptyList() : oldKeys;
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      };
      savedInputId = inputId;
    }

    // do not depend on content!
    return new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        final Ref<StorageException> exRef = new Ref<StorageException>(null);
        ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
          @Override
          public void run() {
            try {
              updateWithMap(inputId, savedInputId, data, oldKeysGetter);
            }
            catch (StorageException ex) {
              exRef.set(ex);
            }
          }
        });

        //noinspection ThrowableResultOfMethodCallIgnored
        if (exRef.get() != null) {
          LOG.info(exRef.get());
          FileBasedIndex.getInstance().requestRebuild(myIndexId);
          return Boolean.FALSE;
        }
        return Boolean.TRUE;
      }
    };
  }

  protected void updateWithMap(final int inputId,
                               int savedInputId, @NotNull Map<Key, Value> newData,
                               @NotNull NotNullComputable<Collection<Key>> oldKeysGetter) throws StorageException {
    getWriteLock().lock();
    try {
      try {
        for (Key key : oldKeysGetter.compute()) {
          myStorage.removeAllValues(key, inputId);
        }
      }
      catch (Exception e) {
        throw new StorageException(e);
      }
      // add new values
      if (newData instanceof THashMap) {
        // such map often (from IdIndex) contain 100x (avg ~240) of entries, also THashMap have no Entry inside so we optimize for gc too
        final Ref<StorageException> exceptionRef = new Ref<StorageException>();
        final boolean b = ((THashMap<Key, Value>)newData).forEachEntry(new TObjectObjectProcedure<Key, Value>() {
          @Override
          public boolean execute(Key key, Value value) {
            try {
              myStorage.addValue(key, inputId, value);
            }
            catch (StorageException ex) {
              exceptionRef.set(ex);
              return false;
            }
            return true;
          }
        });
        if (!b) throw exceptionRef.get();
      }
      else {
        for (Map.Entry<Key, Value> entry : newData.entrySet()) {
          myStorage.addValue(entry.getKey(), inputId, entry.getValue());
        }
      }

      try {
        if (myHasSnapshotMapping && !((MemoryIndexStorage)getStorage()).isBufferingEnabled()) {
          if (savedInputId != NULL_MAPPING && !mySnapshotMapping.containsMapping(savedInputId)) {
            mySnapshotMapping.put(savedInputId, newData.keySet());
          }
          myInputsSnapshotMapping.put(inputId, savedInputId);
        } else if (myInputsIndex != null) {
          final Set<Key> newKeys = newData.keySet();
          if (newKeys.size() > 0) {
            myInputsIndex.put(inputId, newKeys);
          }
          else {
            myInputsIndex.remove(inputId);
          }
        }
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    }
    finally {
      getWriteLock().unlock();
    }
  }
}
