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
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.*;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
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

  private final DataExternalizer<Value> myValueExternalizer;
  private final DataExternalizer<Collection<Key>> mySnapshotIndexExternalizer;

  private PersistentHashMap<Integer, Collection<Key>> myInputsIndex;
  private PersistentHashMap<Integer, ByteSequence> myContents;
  private PersistentHashMap<Integer, Integer> myInputsSnapshotMapping;

  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();

  private Factory<PersistentHashMap<Integer, Collection<Key>>> myInputsIndexFactory;

  private final LowMemoryWatcher myLowMemoryFlusher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      try {
        flush();
      } catch (StorageException e) {
        LOG.info(e);
        FileBasedIndex.getInstance().requestRebuild(myIndexId);
      }
    }
  });

  public MapReduceIndex(@Nullable final ID<Key, Value> indexId,
                        DataIndexer<Key, Value, Input> indexer,
                        @NotNull IndexStorage<Key, Value> storage) throws IOException {
    this(indexId, indexer, storage, null, null);
  }

  public MapReduceIndex(@Nullable final ID<Key, Value> indexId,
                        DataIndexer<Key, Value, Input> indexer,
                        @NotNull IndexStorage<Key, Value> storage,
                        DataExternalizer<Collection<Key>> snapshotIndexExternalizer,
                        DataExternalizer<Value> valueDataExternalizer) throws IOException {
    myIndexId = indexId;
    myIndexer = indexer;
    myStorage = storage;
    myHasSnapshotMapping = snapshotIndexExternalizer != null;

    mySnapshotIndexExternalizer = snapshotIndexExternalizer;
    myValueExternalizer = valueDataExternalizer;
    myContents = createContentsIndex();
  }

  private PersistentHashMap<Integer, ByteSequence> createContentsIndex() throws IOException {
    final File saved = myHasSnapshotMapping && myIndexId != null ? new File(IndexInfrastructure.getPersistentIndexRootDir(myIndexId), "values") : null;

    if (saved != null) {
      try {
        return new PersistentHashMap<Integer, ByteSequence>(saved, EnumeratorIntegerDescriptor.INSTANCE, ByteSequenceDataExternalizer.INSTANCE);
      } catch (IOException ex) {
        IOUtil.deleteAllFilesStartingWith(saved);
        throw ex;
      }
    } else {
      return null;
    }
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
      if (myContents != null) {
        cleanMapping(myContents);
        myContents = createContentsIndex();
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
    try {
      return new PersistentHashMap<Integer, Integer>(fileIdToHashIdFile, EnumeratorIntegerDescriptor.INSTANCE,
                                                     EnumeratorIntegerDescriptor.INSTANCE, 4096) {
        @Override
        protected boolean wantNonnegativeIntegralValues() {
          return true;
        }
      };
    }
    catch (IOException ex) {
      IOUtil.deleteAllFilesStartingWith(fileIdToHashIdFile);
      throw ex;
    }
  }

  private static void cleanMapping(@NotNull PersistentHashMap<?, ?> index) {
    final File baseFile = index.getBaseFile();
    try {
      index.close();
    }
    catch (IOException ignored) {
    }

    IOUtil.deleteAllFilesStartingWith(baseFile);
  }

  @Override
  public void flush() throws StorageException{
    try {
      getReadLock().lock();
      doForce(myInputsIndex);
      doForce(myInputsSnapshotMapping);
      doForce(myContents);
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
    myLowMemoryFlusher.stop();
    final Lock lock = getWriteLock();
    try {
      lock.lock();
      try {
        myStorage.close();
      }
      finally {
        doClose(myInputsIndex);
        doClose(myInputsSnapshotMapping);
        doClose(myContents);
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
    }
    myInputsIndex = createInputsIndex();
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

  private static final boolean doReadSavedPersistentData = SystemProperties.getBooleanProperty("idea.read.saved.persistent.index", true);
  @NotNull
  @Override
  public final Computable<Boolean> update(final int inputId, @Nullable Input content) {
    final boolean weProcessPhysicalContent = content == null ||
                                             (content instanceof UserDataHolder &&
                                              FileBasedIndexImpl.ourPhysicalContentKey.get((UserDataHolder)content, Boolean.FALSE));

    Map<Key, Value> data = null;
    boolean havePersistentData = false;
    Integer hashId = null;
    boolean skippedReadingPersistentDataButMayHaveIt = false;

    if (myContents != null && weProcessPhysicalContent && content != null) {
      try {
        hashId = getHashOfContent((FileContent)content);
        if (doReadSavedPersistentData) {
          if (!myContents.isBusyReading()) {
            ByteSequence bytes = myContents.get(hashId);
            if (bytes != null) {
              data = deserializeSavedPersistentData(bytes);
              havePersistentData = true;
            }
          } else {
            skippedReadingPersistentDataButMayHaveIt = true;
          }
        } else {
          havePersistentData = myContents.containsMapping(hashId);
        }
      } catch (IOException ex) {
        // todo:
        throw new RuntimeException(ex);
      }
    }

    if (data == null) data = content != null ? myIndexer.map(content) : Collections.<Key, Value>emptyMap();

    if (hashId != null && !havePersistentData) {
      savePersistentData(data, hashId, skippedReadingPersistentDataButMayHaveIt);
    }
    ProgressManager.checkCanceled();

    final NotNullComputable<Collection<Key>> oldKeysGetter;
    final int savedInputId;
    if (myHasSnapshotMapping && weProcessPhysicalContent) {
      try {

        oldKeysGetter = new NotNullComputable<Collection<Key>>() {
          @NotNull
          @Override
          public Collection<Key> compute() {
            try {
              Integer currentHashId = myInputsSnapshotMapping.get(inputId);
              Collection<Key> currentKeys;
              if (currentHashId != null) {
                ByteSequence byteSequence = myContents.get(currentHashId);
                currentKeys = byteSequence != null ? deserializeSavedPersistentData(byteSequence).keySet() : Collections.<Key>emptyList();
              }
              else {
                currentKeys = Collections.emptyList();
              }

              return currentKeys;
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        };

        if (content instanceof FileContent) {
          savedInputId = getHashOfContent((FileContent)content);
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
    final Map<Key, Value> finalData = data;
    return new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        final Ref<StorageException> exRef = new Ref<StorageException>(null);
        ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
          @Override
          public void run() {
            try {
              updateWithMap(inputId, savedInputId, finalData, oldKeysGetter);
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

  private Map<Key, Value> deserializeSavedPersistentData(ByteSequence bytes) throws IOException {
    DataInputStream stream = new DataInputStream(new UnsyncByteArrayInputStream(bytes.getBytes(), bytes.getOffset(), bytes.getLength()));
    int pairs = DataInputOutputUtil.readINT(stream);
    if (pairs == 0) return Collections.emptyMap();
    Map<Key, Value> result = new THashMap<Key, Value>(pairs);
    while (stream.available() > 0) {
      Value value = myValueExternalizer.read(stream);
      Collection<Key> keys = mySnapshotIndexExternalizer.read(stream);
      for(Key k:keys) result.put(k, value);
    }
    return result;
  }

  private static Integer getHashOfContent(FileContent content) throws IOException {
    Integer previouslyCalculatedContentHashId = content.getUserData(ourSavedContentHashIdKey);
    if (previouslyCalculatedContentHashId == null) {
      byte[] hash = content instanceof FileContentImpl ? ((FileContentImpl)content).getHash():null;
      if (hash == null) {
        previouslyCalculatedContentHashId = ContentHashesSupport
          .calcContentHashIdWithFileType(content.getContent(), content.getFileType());
      } else {
        previouslyCalculatedContentHashId =  ContentHashesSupport.enumerateHash(hash);
      }
      content.putUserData(ourSavedContentHashIdKey, previouslyCalculatedContentHashId);
    }
    return previouslyCalculatedContentHashId;
  }

  private static final ThreadLocalCachedByteArray ourSpareByteArray = new ThreadLocalCachedByteArray();

  private void savePersistentData(Map<Key, Value> data, int id, boolean delayedReading) {
    try {
      if (delayedReading && myContents.containsMapping(id)) return;
      BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(ourSpareByteArray.getBuffer(4 * data.size()));
      DataOutputStream stream = new DataOutputStream(out);
      int size = data.size();
      DataInputOutputUtil.writeINT(stream, size);

      if (size > 0) {
        THashMap<Value, List<Key>> values = new THashMap<Value, List<Key>>();
        List<Key> keysForNullValue = null;
        for (Map.Entry<Key, Value> e : data.entrySet()) {
          Value value = e.getValue();

          List<Key> keys = value != null ? values.get(value):keysForNullValue;
          if (keys == null) {
            if (value != null) values.put(value, keys = new SmartList<Key>());
            else keys = keysForNullValue = new SmartList<Key>();
          }
          keys.add(e.getKey());
        }

        if (keysForNullValue != null) {
          myValueExternalizer.save(stream, null);
          mySnapshotIndexExternalizer.save(stream, keysForNullValue);
        }

        for(Value value:values.keySet()) {
          myValueExternalizer.save(stream, value);
          mySnapshotIndexExternalizer.save(stream, values.get(value));
        }
      }

      myContents.put(id, new ByteSequence(out.getInternalBuffer(), 0, out.size()));
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private static final com.intellij.openapi.util.Key<Integer> ourSavedContentHashIdKey = com.intellij.openapi.util.Key.create("saved.content.hash.id");

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
