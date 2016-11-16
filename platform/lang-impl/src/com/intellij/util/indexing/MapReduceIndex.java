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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.*;
import com.intellij.util.io.*;
import com.intellij.util.io.DataOutputStream;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public class MapReduceIndex<Key, Value, Input> extends MapReduceIndexBase<Key,Value, Input> implements UpdatableIndex<Key,Value, Input>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapReduceIndex");
  private static final int NULL_MAPPING = 0;
  private final boolean myHasSnapshotMapping;

  private final DataExternalizer<Collection<Key>> mySnapshotIndexExternalizer;
  private final boolean myIsPsiBackedIndex;
  private final AtomicBoolean myInMemoryMode = new AtomicBoolean();
  private final TIntObjectHashMap<Collection<Key>> myInMemoryKeys = new TIntObjectHashMap<>();

  private PersistentHashMap<Integer, ByteSequence> myContents;
  private PersistentHashMap<Integer, Integer> myInputsSnapshotMapping;
  private PersistentHashMap<Integer, String> myIndexingTrace;

  static {
    if (!DebugAssertions.DEBUG) {
      final Application app = ApplicationManager.getApplication();
      DebugAssertions.DEBUG = app.isEAP() || app.isInternal();
    }
  }

  public MapReduceIndex(IndexExtension<Key, Value, Input> extension,
                        @NotNull IndexStorage<Key, Value> storage) throws IOException {
    super(extension, storage, false);

    SharedIndicesData.registerIndex(myIndexId, extension);
    myHasSnapshotMapping = extension instanceof FileBasedIndexExtension &&
                           ((FileBasedIndexExtension<Key, Value>)extension).hasSnapshotMapping() &&
                           IdIndex.ourSnapshotMappingsEnabled;

    mySnapshotIndexExternalizer = createInputsIndexExternalizer(extension, myIndexId, extension.getKeyDescriptor());
    myIsPsiBackedIndex = extension instanceof PsiDependentIndex;

    myContents = createContentsIndex(); // todo

    if (!SharedIndicesData.ourFileSharedIndicesEnabled || SharedIndicesData.DO_CHECKS) {
      if (myHasSnapshotMapping) {
        myInputsSnapshotMapping = createInputSnapshotMapping();
      }
      else {
        myInputsIndex = createInputsIndex();
      }
    }

    if (DebugAssertions.EXTRA_SANITY_CHECKS && myHasSnapshotMapping) {
      myIndexingTrace = createIndexingTrace();
    }

    if (storage instanceof MemoryIndexStorage) {
      ((MemoryIndexStorage)storage).addBufferingStateListener(new MemoryIndexStorage.BufferingStateListener() {
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

  @NotNull
  private <K> PersistentHashMap<Integer, Collection<K>> createIdToDataKeysIndex(@NotNull IndexExtension <K, ?, ?> extension,
                                                                                @NotNull MemoryIndexStorageBase<K, ?, ?> storage)
    throws IOException {
    ID<K, ?> indexId = extension.getName();
    KeyDescriptor<K> keyDescriptor = extension.getKeyDescriptor();
    final File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(indexId);

    return new PersistentHashMap<>(
      indexStorageFile, EnumeratorIntegerDescriptor.INSTANCE, createInputsIndexExternalizer(extension, indexId, keyDescriptor)
    );
  }

  private PersistentHashMap<Integer, ByteSequence> createContentsIndex() throws IOException {
    final File saved = myHasSnapshotMapping ? new File(IndexInfrastructure.getPersistentIndexRootDir(myIndexId), "values") : null;

    if (saved != null) {
      try {
        return new PersistentHashMap<>(saved, EnumeratorIntegerDescriptor.INSTANCE, ByteSequenceDataExternalizer.INSTANCE);
      } catch (IOException ex) {
        IOUtil.deleteAllFilesStartingWith(saved);
        throw ex;
      }
    } else {
      return null;
    }
  }

  @Override
  protected void cleanMappings() throws IOException {
    if (myInputsSnapshotMapping != null) {
      cleanMapping(myInputsSnapshotMapping);
      myInputsSnapshotMapping = createInputSnapshotMapping();
    }
    if (myIndexingTrace != null) {
      cleanMapping(myIndexingTrace);
      myIndexingTrace = createIndexingTrace();
    }
    if (myContents != null) {
      cleanMapping(myContents);
      myContents = createContentsIndex();
    }
  }

  private PersistentHashMap<Integer, Integer> createInputSnapshotMapping() throws IOException {
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

  private PersistentHashMap<Integer, String> createIndexingTrace() throws IOException {
    final File mapFile = new File(IndexInfrastructure.getIndexRootDir(myIndexId), "indextrace");
    try {
      return new PersistentHashMap<>(mapFile, EnumeratorIntegerDescriptor.INSTANCE,
                                     new DataExternalizer<String>() {
                                       @Override
                                       public void save(@NotNull DataOutput out, String value) throws IOException {
                                         out.write((byte[])CompressionUtil.compressCharSequence(value, Charset.defaultCharset()));
                                       }

                                       @Override
                                       public String read(@NotNull DataInput in) throws IOException {
                                         byte[] b = new byte[((InputStream)in).available()];
                                         in.readFully(b);
                                         return (String)CompressionUtil.uncompressCharSequence(b, Charset.defaultCharset());
                                       }
                                     }, 4096);
    }
    catch (IOException ex) {
      IOUtil.deleteAllFilesStartingWith(mapFile);
      throw ex;
    }
  }

  @Override
  protected void doForce() {
    doForce(myInputsSnapshotMapping);
    doForce(myIndexingTrace);
    doForce(myContents);
  }

  @Override
  protected void doClose() {
    doClose(myInputsSnapshotMapping);
    doClose(myIndexingTrace);
    doClose(myContents);
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
  protected PersistentHashMap<Integer, Collection<Key>> createInputsIndex() throws IOException {
    return createIdToDataKeysIndex(myExtension, (MemoryIndexStorageBase<Key, ?, ?>)myStorage);
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
        FileContent fileContent = (FileContent)content;
        hashId = getHashOfContent(fileContent);
        if (doReadSavedPersistentData) {
          if (!myContents.isBusyReading() || DebugAssertions.EXTRA_SANITY_CHECKS) { // avoid blocking read, we can calculate index value
            ByteSequence bytes = readContents(hashId);

            if (bytes != null) {
              data = deserializeSavedPersistentData(bytes);
              havePersistentData = true;
              if (DebugAssertions.EXTRA_SANITY_CHECKS) {
                Map<Key, Value> contentData = myIndexer.map(content);
                boolean sameValueForSavedIndexedResultAndCurrentOne = contentData.equals(data);
                if (!sameValueForSavedIndexedResultAndCurrentOne) {
                  DebugAssertions.error(
                    "Unexpected difference in indexing of %s by index %s, file type %s, charset %s\ndiff %s\nprevious indexed info %s",
                    fileContent.getFile(),
                    myIndexId,
                    fileContent.getFileType().getName(),
                    ((FileContentImpl)fileContent).getCharset(),
                    buildDiff(data, contentData),
                    myIndexingTrace.get(hashId)
                  );
                }
              }
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

    if (data == null) {
      data = content != null ? myIndexer.map(content) : Collections.emptyMap();
      checkValuesHaveProperEqualsAndHashCode(data);
    }

    if (hashId != null && !havePersistentData) {
      boolean saved = savePersistentData(data, hashId, skippedReadingPersistentDataButMayHaveIt);
      if (DebugAssertions.EXTRA_SANITY_CHECKS) {
        if (saved) {

          FileContent fileContent = (FileContent)content;
          try {
            myIndexingTrace.put(hashId, ((FileContentImpl)fileContent).getCharset() + "," + fileContent.getFileType().getName()+"," + fileContent.getFile().getPath() + "," +
                                        ExceptionUtil.getThrowableText(new Throwable()));
          } catch (IOException ex) {
            LOG.error(ex);
          }
        }
      }
    }
    myStorage.checkCanceled();

    UpdateData<Key, Value> optimizedUpdateData = null;
    final NotNullComputable<Collection<Key>> oldKeysGetter;
    final int savedInputId;
    if (myHasSnapshotMapping) {
      try {
        final NotNullComputable<Collection<Key>> keysForGivenInputId = () -> {
          try {
            Integer currentHashId = readInputHashId(inputId);
            Collection<Key> currentKeys;
            if (currentHashId != null) {
              ByteSequence byteSequence = readContents(currentHashId);
              currentKeys = byteSequence != null ? deserializeSavedPersistentData(byteSequence).keySet() : Collections.<Key>emptyList();
            }
            else {
              currentKeys = Collections.emptyList();
            }

            return currentKeys;
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        };

        if (weProcessPhysicalContent) {
          if (content instanceof FileContent) {
            savedInputId = getHashOfContent((FileContent)content);
          }
          else {
            savedInputId = NULL_MAPPING;
          }
          oldKeysGetter = keysForGivenInputId;

          if (MapDiffUpdateData.ourDiffUpdateEnabled) {
            final Map<Key, Value> newValue = data;
            optimizedUpdateData = new MapDiffUpdateData<Key, Value>(myIndexId) {
              @Override
              protected Map<Key, Value> getNewValue() {
                return newValue;
              }

              @Override
              protected Map<Key, Value> getCurrentValue() throws IOException {
                Integer currentHashId = readInputHashId(inputId);
                Map<Key, Value> currentValue;
                if (currentHashId != null) {
                  ByteSequence byteSequence = readContents(currentHashId);
                  currentValue = byteSequence != null ? deserializeSavedPersistentData(byteSequence) : Collections.<Key, Value>emptyMap();
                }
                else {
                  currentValue = Collections.emptyMap();
                }
                return currentValue;
              }

              @Override
              public void save(int inputId) throws IOException {
                saveInputHashId(inputId, savedInputId);
              }
            };
          }
        } else {
          oldKeysGetter = () -> {
            try {
              Collection<Key> oldKeys = readInputKeys(inputId);
              if (oldKeys == null) {
                return keysForGivenInputId.compute();
              }
              return oldKeys;
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          };
          savedInputId = NULL_MAPPING;
        }
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    } else {
      oldKeysGetter = createOldKeysGetterByInputIndex(inputId);
      savedInputId = inputId;
    }

    // do not depend on content!
    final UpdateData<Key, Value> updateData = optimizedUpdateData != null ? optimizedUpdateData : buildUpdateData(data, oldKeysGetter, savedInputId);
    return () -> {

      try {
        updateWithMap(inputId, updateData);
      }
      catch (StorageException|ProcessCanceledException ex) {
        LOG.info("Exception during updateWithMap:" + ex);
        requestRebuild(ex);
        return Boolean.FALSE;
      }

      return Boolean.TRUE;
    };
  }

  @Override
  public void requestRebuild(@Nullable Exception ex) {
    Runnable action = () -> {
      if (ex == null) {
        FileBasedIndex.getInstance().requestRebuild(myIndexId);
      }
      else {
        FileBasedIndex.getInstance().requestRebuild(myIndexId, ex);
      }
    };
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      // avoid deadlock due to synchronous update in DumbServiceImpl#queueTask
      application.invokeLater(action, ModalityState.any());
    } else {
      action.run();
    }
  }

  @Override
  protected <K> DataExternalizer<Collection<K>> createInputsIndexExternalizer(IndexExtension<K, ?, ?> extension,
                                                                              ID<K, ?> indexId,
                                                                              KeyDescriptor<K> keyDescriptor) {
    return extension instanceof CustomInputsIndexFileBasedIndexExtension ?
           ((CustomInputsIndexFileBasedIndexExtension<K>)extension).createExternalizer() :
           super.createInputsIndexExternalizer(extension, indexId, keyDescriptor);
  }

  @Override
  protected UpdateData<Key, Value> buildUpdateData(Map<Key, Value> data, NotNullComputable<Collection<Key>> oldKeysGetter, int savedInputId) {
    return new SimpleUpdateData(myIndexId, savedInputId, data, oldKeysGetter);
  }

  private ByteSequence readContents(Integer hashId) throws IOException {
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      if (SharedIndicesData.DO_CHECKS) {
        synchronized (myContents) {
          ByteSequence contentBytes = SharedIndicesData.recallContentData(hashId, myIndexId, ByteSequenceDataExternalizer.INSTANCE);
          ByteSequence contentBytesFromContents = myContents.get(hashId);

          if ((contentBytes == null && contentBytesFromContents != null) ||
              !Comparing.equal(contentBytesFromContents, contentBytes)) {
            SharedIndicesData.associateContentData(hashId, myIndexId, contentBytesFromContents, ByteSequenceDataExternalizer.INSTANCE);
            if (contentBytes != null) {
              LOG.error("Unexpected indexing diff with hashid " + myIndexId + "," + hashId);
            }
            contentBytes = contentBytesFromContents;
          }
          return contentBytes;
        }
      } else {
        return SharedIndicesData.recallContentData(hashId, myIndexId, ByteSequenceDataExternalizer.INSTANCE);
      }
    }

    return myContents.get(hashId);
  }

  private void saveContents(int id, BufferExposingByteArrayOutputStream out) throws IOException {
    ByteSequence byteSequence = new ByteSequence(out.getInternalBuffer(), 0, out.size());
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      if (SharedIndicesData.DO_CHECKS) {
        synchronized (myContents) {
          myContents.put(id, byteSequence);
          SharedIndicesData.associateContentData(id, myIndexId, byteSequence, ByteSequenceDataExternalizer.INSTANCE);
        }
      } else {
        SharedIndicesData.associateContentData(id, myIndexId, byteSequence, ByteSequenceDataExternalizer.INSTANCE);
      }
    } else {
      myContents.put(id, byteSequence);
    }
  }

  private Integer readInputHashId(int inputId) throws IOException {
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      Integer hashId = SharedIndicesData.recallFileData(inputId, myIndexId, EnumeratorIntegerDescriptor.INSTANCE);
      if (hashId == null) hashId = 0;
      if (myInputsSnapshotMapping == null) return hashId;

      Integer hashIdFromInputSnapshotMapping = myInputsSnapshotMapping.get(inputId);
      if ((hashId == 0 && hashIdFromInputSnapshotMapping != 0) ||
          !Comparing.equal(hashIdFromInputSnapshotMapping, hashId)) {
        SharedIndicesData.associateFileData(inputId, myIndexId, hashIdFromInputSnapshotMapping,
                                            EnumeratorIntegerDescriptor.INSTANCE);
        if (hashId != 0) {
          LOG.error("Unexpected indexing diff with hashid " + myIndexId + ", file:" + IndexInfrastructure.findFileById(PersistentFS.getInstance(), inputId)
                    + "," + hashIdFromInputSnapshotMapping + "," + hashId);
        }
        hashId = hashIdFromInputSnapshotMapping;
      }
      return hashId;
    }
    return myInputsSnapshotMapping.get(inputId);
  }

  private void saveInputHashId(int inputId, int savedInputId) throws IOException {
    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      SharedIndicesData.associateFileData(inputId, myIndexId, savedInputId, EnumeratorIntegerDescriptor.INSTANCE);
    }

    if (myInputsSnapshotMapping != null) myInputsSnapshotMapping.put(inputId, savedInputId);
  }

  @Override
  protected Collection<Key> readInputKeys(int inputId) throws IOException {
    if (myInMemoryMode.get()) {
      synchronized (myInMemoryKeys) {
        Collection<Key> keys = myInMemoryKeys.get(inputId);
        if (keys != null) {
          return keys;
        }
      }
    }
    if (myHasSnapshotMapping) {
      return null;
    }

    if (SharedIndicesData.ourFileSharedIndicesEnabled) {
      Collection<Key> keys = SharedIndicesData.recallFileData(inputId, myIndexId, mySnapshotIndexExternalizer);
      if (myInputsIndex != null) {
        Collection<Key> keysFromInputsIndex = myInputsIndex.get(inputId);

        if ((keys == null && keysFromInputsIndex != null) ||
            !DebugAssertions.equals(keysFromInputsIndex, keys, myExtension.getKeyDescriptor())
           ) {
          SharedIndicesData.associateFileData(inputId, myIndexId, keysFromInputsIndex, mySnapshotIndexExternalizer);
          if (keys != null) {
            DebugAssertions.error(
              "Unexpected indexing diff " + myIndexId + ", file:" + IndexInfrastructure.findFileById(PersistentFS.getInstance(), inputId)
              + "," + keysFromInputsIndex + "," + keys);
          }
          keys = keysFromInputsIndex;
        }
      }
      return keys;
    }
    return super.readInputKeys(inputId);
  }

  @Override
  protected void saveInputKeys(int inputId, int savedInputId, Map<Key, Value> newData) throws IOException {
    if (myInMemoryMode.get()) {
      synchronized (myInMemoryKeys) {
        myInMemoryKeys.put(inputId, newData.keySet());
      }
    } else {
      if (myHasSnapshotMapping) {
        saveInputHashId(inputId, savedInputId);
      } else {
        super.saveInputKeys(inputId, savedInputId, newData);

        if (SharedIndicesData.ourFileSharedIndicesEnabled) {
          Set<Key> newKeys = newData.keySet();
          if (newKeys.size() == 0) newKeys = null;
          SharedIndicesData.associateFileData(inputId, myIndexId, newKeys, mySnapshotIndexExternalizer);
        }
      }
    }
  }

  private StringBuilder buildDiff(Map<Key, Value> data, Map<Key, Value> contentData) {
    StringBuilder moreInfo = new StringBuilder();
    if (contentData.size() != data.size()) {
      moreInfo.append("Indexer has different number of elements, previously ").append(data.size()).append(" after ")
        .append(contentData.size()).append("\n");
    } else {
      moreInfo.append("total ").append(contentData.size()).append(" entries\n");
    }

    for(Map.Entry<Key, Value> keyValueEntry:contentData.entrySet()) {
      if (!data.containsKey(keyValueEntry.getKey())) {
        moreInfo.append("Previous data doesn't contain:").append(keyValueEntry.getKey()).append( " with value ").append(keyValueEntry.getValue()).append("\n");
      }
      else {
        Value value = data.get(keyValueEntry.getKey());
        if (!Comparing.equal(keyValueEntry.getValue(), value)) {
          moreInfo.append("Previous data has different value for key:").append(keyValueEntry.getKey()).append( ", new value ").append(keyValueEntry.getValue()).append( ", oldValue:").append(value).append("\n");
        }
      }
    }

    for(Map.Entry<Key, Value> keyValueEntry:data.entrySet()) {
      if (!contentData.containsKey(keyValueEntry.getKey())) {
        moreInfo.append("New data doesn't contain:").append(keyValueEntry.getKey()).append( " with value ").append(keyValueEntry.getValue()).append("\n");
      }
      else {
        Value value = contentData.get(keyValueEntry.getKey());
        if (!Comparing.equal(keyValueEntry.getValue(), value)) {
          moreInfo.append("New data has different value for key:").append(keyValueEntry.getKey()).append( " new value ").append(value).append( ", oldValue:").append(keyValueEntry.getValue()).append("\n");
        }
      }
    }
    return moreInfo;
  }

  private Map<Key, Value> deserializeSavedPersistentData(ByteSequence bytes) throws IOException {
    DataInputStream stream = new DataInputStream(new UnsyncByteArrayInputStream(bytes.getBytes(), bytes.getOffset(), bytes.getLength()));
    int pairs = DataInputOutputUtil.readINT(stream);
    if (pairs == 0) return Collections.emptyMap();
    Map<Key, Value> result = new THashMap<>(pairs);
    while (stream.available() > 0) {
      Value value = myValueExternalizer.read(stream);
      Collection<Key> keys = mySnapshotIndexExternalizer.read(stream);
      for(Key k:keys) result.put(k, value);
    }
    return result;
  }

  private Integer getHashOfContent(FileContent content) throws IOException {
    FileType fileType = content.getFileType();
    if (myIsPsiBackedIndex && myHasSnapshotMapping && content instanceof FileContentImpl) {
      // psi backed index should use existing psi to build index value (FileContentImpl.getPsiFileForPsiDependentIndex())
      // so we should use different bytes to calculate hash(Id)
      Integer previouslyCalculatedUncommittedHashId = content.getUserData(ourSavedUncommittedHashIdKey);

      if (previouslyCalculatedUncommittedHashId == null) {
        Document document = FileDocumentManager.getInstance().getCachedDocument(content.getFile());

        if (document != null) {  // if document is not committed
          PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(content.getProject());

          if (psiDocumentManager.isUncommited(document)) {
            PsiFile file = psiDocumentManager.getCachedPsiFile(document);
            Charset charset = ((FileContentImpl)content).getCharset();

            if (file != null) {
              previouslyCalculatedUncommittedHashId = ContentHashesSupport
                .calcContentHashIdWithFileType(file.getText().getBytes(charset), charset,
                                               fileType);
              content.putUserData(ourSavedUncommittedHashIdKey, previouslyCalculatedUncommittedHashId);
            }
          }
        }
      }
      if (previouslyCalculatedUncommittedHashId != null) return previouslyCalculatedUncommittedHashId;
    }

    Integer previouslyCalculatedContentHashId = content.getUserData(ourSavedContentHashIdKey);
    if (previouslyCalculatedContentHashId == null) {
      byte[] hash = content instanceof FileContentImpl ? ((FileContentImpl)content).getHash():null;
      if (hash == null) {
        if (fileType.isBinary()) {
          previouslyCalculatedContentHashId = ContentHashesSupport.calcContentHashId(content.getContent(), fileType);
        } else {
          Charset charset = content instanceof FileContentImpl ? ((FileContentImpl)content).getCharset() : null;
          previouslyCalculatedContentHashId = ContentHashesSupport
            .calcContentHashIdWithFileType(content.getContent(), charset, fileType);
        }
      } else {
        previouslyCalculatedContentHashId =  ContentHashesSupport.enumerateHash(hash);
      }
      content.putUserData(ourSavedContentHashIdKey, previouslyCalculatedContentHashId);
    }
    return previouslyCalculatedContentHashId;
  }

  private static final ThreadLocalCachedByteArray ourSpareByteArray = new ThreadLocalCachedByteArray();

  private boolean savePersistentData(Map<Key, Value> data, int id, boolean delayedReading) {
    try {
      if (delayedReading && myContents.containsMapping(id)) return false;
      BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(ourSpareByteArray.getBuffer(4 * data.size()));
      DataOutputStream stream = new DataOutputStream(out);
      int size = data.size();
      DataInputOutputUtil.writeINT(stream, size);

      if (size > 0) {
        THashMap<Value, List<Key>> values = new THashMap<>();
        List<Key> keysForNullValue = null;
        for (Map.Entry<Key, Value> e : data.entrySet()) {
          Value value = e.getValue();

          List<Key> keys = value != null ? values.get(value):keysForNullValue;
          if (keys == null) {
            if (value != null) values.put(value, keys = new SmartList<>());
            else keys = keysForNullValue = new SmartList<>();
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

      saveContents(id, out);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    return true;
  }

  private static final com.intellij.openapi.util.Key<Integer> ourSavedContentHashIdKey = com.intellij.openapi.util.Key.create("saved.content.hash.id");
  private static final com.intellij.openapi.util.Key<Integer> ourSavedUncommittedHashIdKey = com.intellij.openapi.util.Key.create("saved.uncommitted.hash.id");
}
