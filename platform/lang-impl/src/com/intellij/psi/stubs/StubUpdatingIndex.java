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
package com.intellij.psi.stubs;

import com.intellij.index.PrebuiltIndexProviderBase;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.indexing.impl.forward.AbstractForwardIndexAccessor;
import com.intellij.util.io.*;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.*;
import java.util.*;

public class StubUpdatingIndex
  extends SingleEntryFileBasedIndexExtension<SerializedStubTree>
  implements PsiDependentIndex, CustomImplementationFileBasedIndexExtension<Integer, SerializedStubTree, FileContent> {
  static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubUpdatingIndex");
  private static final int VERSION = 38 + (PersistentHashMapValueStorage.COMPRESSION_ENABLED ? 1 : 0);

  // todo remove once we don't need this for stub-ast mismatch debug info
  private static final FileAttribute INDEXED_STAMP = new FileAttribute("stubIndexStamp", 2, true);

  public static final ID<Integer, SerializedStubTree> INDEX_ID = ID.create("Stubs");

  private static final DataExternalizer<SerializedStubTree> KEY_EXTERNALIZER = new DataExternalizer<SerializedStubTree>() {
    @Override
    public void save(@NotNull final DataOutput out, @NotNull final SerializedStubTree v) throws IOException {
      v.write(out);
    }

    @NotNull
    @Override
    public SerializedStubTree read(@NotNull final DataInput in) throws IOException {
      return new SerializedStubTree(in);
    }
  };

  protected static final FileBasedIndex.InputFilter INPUT_FILTER = file -> canHaveStub(file);

  public static boolean canHaveStub(@NotNull VirtualFile file) {
    FileType fileType = SubstitutedFileType.substituteFileType(file, file.getFileType(), ProjectUtil.guessProjectForFile(file));
    if (fileType instanceof LanguageFileType) {
      final Language l = ((LanguageFileType)fileType).getLanguage();
      final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
      if (parserDefinition == null) {
        return false;
      }

      final IFileElementType elementType = parserDefinition.getFileNodeType();
      if (elementType instanceof IStubFileElementType) {
        if (((IStubFileElementType)elementType).shouldBuildStubFor(file)) {
          return true;
        }
        FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
        if (file instanceof NewVirtualFile &&
            fileBasedIndex instanceof FileBasedIndexImpl &&
            ((FileBasedIndexImpl)fileBasedIndex).getIndex(INDEX_ID).isIndexedStateForFile(((NewVirtualFile)file).getId(), file)) {
          return true;
        }
      }
    }
    final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
    return builder != null && builder.acceptsFile(file);
  }

  @NotNull
  @Override
  public ID<Integer, SerializedStubTree> getName() {
    return INDEX_ID;
  }

  @Override
  public int getCacheSize() {
    return 5; // no need to cache many serialized trees
  }

  @Override
  public boolean keyIsUniqueForIndexedFile() {
    return true;
  }

  private static DataExternalizer<Map<StubIndexKey, Map<Object, StubIdList>>> createStubIndexMapsExternalizer() {
    return new DataExternalizer<Map<StubIndexKey, Map<Object, StubIdList>>>() {
      private volatile boolean myEnsuredStubElementTypesLoaded;

      @Override
      public void save(@NotNull DataOutput out, Map<StubIndexKey, Map<Object, StubIdList>> value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.size());

        if (!value.isEmpty()) {
          StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();

          for (StubIndexKey stubIndexKey : value.keySet()) {
            DataInputOutputUtil.writeINT(out, stubIndexKey.getUniqueId());
            Map<Object, StubIdList> map = value.get(stubIndexKey);

            stubIndex.serializeIndexValue(out, stubIndexKey, map);
          }
        }
      }

      @Override
      public Map<StubIndexKey, Map<Object, StubIdList>> read(@NotNull DataInput in) throws IOException {
        if (!myEnsuredStubElementTypesLoaded) {
          SerializationManager.getInstance().initSerializers();
          StubIndexImpl.initExtensions();
          myEnsuredStubElementTypesLoaded = true;
        }

        int stubIndicesValueMapSize = DataInputOutputUtil.readINT(in);
        if (stubIndicesValueMapSize <= 0) return Collections.emptyMap();
        THashMap<StubIndexKey, Map<Object, StubIdList>> stubIndicesValueMap = new THashMap<>(stubIndicesValueMapSize);
        StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();

        for (int i = 0; i < stubIndicesValueMapSize; ++i) {
          int stubIndexId = DataInputOutputUtil.readINT(in);
          ID<Object, ?> indexKey = (ID<Object, ?>)ID.findById(stubIndexId);
          if (indexKey instanceof StubIndexKey) { // indexKey can be ID in case of removed index
            StubIndexKey<Object, ?> stubIndexKey = (StubIndexKey<Object, ?>)indexKey;
            stubIndicesValueMap.put(stubIndexKey, stubIndex.deserializeIndexValue(in, stubIndexKey));
          }
        }
        return stubIndicesValueMap;
      }
    };
  }

  @NotNull
  @Override
  public SingleEntryIndexer<SerializedStubTree> getIndexer() {
    return new SingleEntryIndexer<SerializedStubTree>(false) {
      @Override
      @Nullable
      public SerializedStubTree computeValue(@NotNull final FileContent inputData) {
        return ReadAction.compute(() -> {
          Stub rootStub = null;

          if (Registry.is("use.prebuilt.indices")) {
            final PrebuiltStubsProvider prebuiltStubsProvider =
              PrebuiltStubsProviders.INSTANCE.forFileType(inputData.getFileType());
            if (prebuiltStubsProvider != null) {
              rootStub = prebuiltStubsProvider.findStub(inputData);
              if (PrebuiltIndexProviderBase.DEBUG_PREBUILT_INDICES) {
                Stub stub = StubTreeBuilder.buildStubTree(inputData);
                if (rootStub != null && stub != null) {
                  check(rootStub, stub);
                }
              }
            }
          }

          if (rootStub == null) {
            rootStub = StubTreeBuilder.buildStubTree(inputData);
          }

          if (rootStub == null) return null;

          VirtualFile file = inputData.getFile();
          int contentLength;
          if (file.getFileType().isBinary()) {
            contentLength = -1;
          }
          else {
            contentLength = ((FileContentImpl)inputData).getPsiFileForPsiDependentIndex().getTextLength();
          }
          rememberIndexingStamp(file, contentLength);

          final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
          SerializationManagerEx.getInstanceEx().serialize(rootStub, bytes);

          if (DebugAssertions.DEBUG) {
            try {
              Stub deserialized =
                SerializationManagerEx.getInstanceEx().deserialize(bytes.toInputStream());
              check(deserialized, rootStub);
            }
            catch (ProcessCanceledException pce) {
              throw pce;
            }
            catch (Throwable t) {
              LOG.error("Error indexing:" + file, t);
            }
          }
          SerializedStubTree serializedStubTree =
            new SerializedStubTree(bytes.getInternalBuffer(), bytes.size(), rootStub, file.getLength(), contentLength);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Indexing " + file + "; lengths=" + serializedStubTree.dumpLengths());
          }
          return serializedStubTree;
        });
      }
    };
  }

  private static void check(@NotNull Stub stub, @NotNull Stub stub2) {
    assert stub.getStubType() == stub2.getStubType();
    List<? extends Stub> stubs = stub.getChildrenStubs();
    List<? extends Stub> stubs2 = stub2.getChildrenStubs();
    assert stubs.size() == stubs2.size();
    for (int i = 0, len = stubs.size(); i < len; ++i) {
      check(stubs.get(i), stubs2.get(i));
    }
  }

  private static void rememberIndexingStamp(@NotNull VirtualFile file, long contentLength) {
    try (DataOutputStream stream = INDEXED_STAMP.writeAttribute(file)) {
      DataInputOutputUtil.writeTIME(stream, file.getTimeStamp());
      DataInputOutputUtil.writeLONG(stream, contentLength);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Nullable
  static IndexingStampInfo getIndexingStampInfo(@NotNull VirtualFile file) {
    try (DataInputStream stream = INDEXED_STAMP.readAttribute(file)) {
      if (stream == null) {
        return null;
      }

      long stamp = DataInputOutputUtil.readTIME(stream);
      long size = DataInputOutputUtil.readLONG(stream);
      return new IndexingStampInfo(stamp, size);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  @Override
  public DataExternalizer<SerializedStubTree> getValueExternalizer() {
    return KEY_EXTERNALIZER;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @NotNull
  @Override
  public UpdatableIndex<Integer, SerializedStubTree, FileContent> createIndexImplementation(@NotNull final FileBasedIndexExtension<Integer, SerializedStubTree> extension,
                                                                                            @NotNull IndexStorage<Integer, SerializedStubTree> storage)
    throws StorageException, IOException {
    if (storage instanceof MemoryIndexStorage) {
      final MemoryIndexStorage<Integer, SerializedStubTree> memStorage = (MemoryIndexStorage<Integer, SerializedStubTree>)storage;
      memStorage.addBufferingStateListener(new MemoryIndexStorage.BufferingStateListener() {
        @Override
        public void bufferingStateChanged(final boolean newState) {
          ((StubIndexImpl)StubIndex.getInstance()).setDataBufferingEnabled(newState);
        }

        @Override
        public void memoryStorageCleared() {
          ((StubIndexImpl)StubIndex.getInstance()).cleanupMemoryStorage();
        }
      });
    }
    return new MyIndex(extension, storage);
  }

  private static void updateStubIndices(@NotNull final Collection<StubIndexKey> indexKeys,
                                        final int inputId,
                                        @NotNull final Map<StubIndexKey, Map<Object, StubIdList>> oldStubTree,
                                        @NotNull final Map<StubIndexKey, Map<Object, StubIdList>> newStubTree) {
    final StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();
    for (StubIndexKey key : indexKeys) {
      final Map<Object, StubIdList> oldMap = oldStubTree.get(key);
      final Map<Object, StubIdList> newMap = newStubTree.get(key);

      final Map<Object, StubIdList> _oldMap = oldMap != null ? oldMap : Collections.emptyMap();
      final Map<Object, StubIdList> _newMap = newMap != null ? newMap : Collections.emptyMap();

      stubIndex.updateIndex(key, inputId, _oldMap, _newMap);
    }
  }

  @NotNull
  private static Collection<StubIndexKey> getAffectedIndices(@NotNull final Map<StubIndexKey, Map<Object, StubIdList>> oldStubTree,
                                                             @NotNull final Map<StubIndexKey, Map<Object, StubIdList>> newStubTree) {
    Set<StubIndexKey> allIndices = new HashSet<>();
    allIndices.addAll(oldStubTree.keySet());
    allIndices.addAll(newStubTree.keySet());
    return allIndices;
  }

  @NotNull
  private static
  Map<StubIndexKey, Map<Object, StubIdList>> calcStubIndicesValueMap(@Nullable SerializedStubTree stub) {
    try {
      if (stub == null) return Collections.emptyMap();
      ObjectStubBase root = (ObjectStubBase)stub.getStub(true);
      ObjectStubTree objectStubTree = root instanceof PsiFileStub ? new StubTree((PsiFileStub)root, false) :
                                      new ObjectStubTree(root, false);
      Map<StubIndexKey, Map<Object, int[]>> map = objectStubTree.indexStubTree();

      // xxx:fix refs inplace
      Map<StubIndexKey, Map<Object, StubIdList>> stubIndicesValueMap = (Map)map;
      for (StubIndexKey key : map.keySet()) {
        Map<Object, int[]> value = map.get(key);
        for (Object k : value.keySet()) {
          int[] ints = value.get(k);
          StubIdList stubList = ints.length == 1 ? new StubIdList(ints[0]) : new StubIdList(ints, ints.length);
          ((Map<Object, StubIdList>)(Map)value).put(k, stubList);
        }
      }

      return stubIndicesValueMap;
    }
    catch (SerializerNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private static class MyIndex extends VfsAwareMapReduceIndex<Integer, SerializedStubTree, FileContent> {
    private StubIndexImpl myStubIndex;
    private final StubVersionMap myStubVersionMap = new StubVersionMap();

    MyIndex(@NotNull FileBasedIndexExtension<Integer, SerializedStubTree> extension, @NotNull IndexStorage<Integer, SerializedStubTree> storage)
      throws StorageException, IOException {
      super(extension, storage, VfsAwareMapReduceIndex.createForwardIndex(extension), new MyForwardIndexAccessor());
      checkNameStorage();
    }

    @NotNull
    @Override
    protected UpdateData<Integer, SerializedStubTree> createUpdateData(@NotNull Map<Integer, SerializedStubTree> data,
                                                                       @NotNull ThrowableComputable<InputDataDiffBuilder<Integer, SerializedStubTree>, IOException> oldKeys,
                                                                       @NotNull ThrowableRunnable<IOException> forwardIndexUpdate) {
      return new StubUpdatingData(data, oldKeys, forwardIndexUpdate);
    }

    static class StubUpdatingData extends UpdateData<Integer, SerializedStubTree> {

      StubUpdatingData(@NotNull Map<Integer, SerializedStubTree> newData,
                       @NotNull ThrowableComputable<InputDataDiffBuilder<Integer, SerializedStubTree>, IOException> iterator,
                       @Nullable ThrowableRunnable<IOException> forwardIndexUpdate) {
        super(newData, iterator, INDEX_ID, forwardIndexUpdate);
      }
    }

    @Override
    protected void doFlush() throws IOException, StorageException {
      final StubIndexImpl stubIndex = getStubIndex();
      try {
        stubIndex.flush();
      }
      finally {
        super.doFlush();
      }
    }

    @Override
    protected void updateWithMap(int inputId,
                                 @NotNull UpdateData<Integer, SerializedStubTree> updateData) throws StorageException {
      checkNameStorage();
      StubUpdatingData stubUpdatingData = (StubUpdatingData)updateData;
      final Map<StubIndexKey, Map<Object, StubIdList>> newStubIndicesValueMap = calcStubIndicesValueMap(getValue(stubUpdatingData.getNewData()));

      try {
        getWriteLock().lock();

        Map<StubIndexKey, Map<Object, StubIdList>> previousStubIndicesValueMap =
          ((AbstractForwardIndexAccessor<Integer, SerializedStubTree, Map<StubIndexKey, Map<Object, StubIdList>>, ?>)getForwardIndexAccessor()).deserializeData(getForwardIndexMap().get(inputId));
        previousStubIndicesValueMap = ContainerUtil.notNullize(previousStubIndicesValueMap);

        super.updateWithMap(inputId, updateData);

        updateStubIndices(
          getAffectedIndices(previousStubIndicesValueMap, newStubIndicesValueMap),
          inputId,
          previousStubIndicesValueMap,
          newStubIndicesValueMap
        );
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      finally {
        getWriteLock().unlock();
      }
    }

    @NotNull
    private StubIndexImpl getStubIndex() {
      StubIndexImpl index = myStubIndex;
      if (index == null) {
        myStubIndex = index = (StubIndexImpl)StubIndex.getInstance();
      }
      return index;
    }

    private static void checkNameStorage() throws StorageException {
      final SerializationManagerEx serializationManager = SerializationManagerEx.getInstanceEx();
      if (serializationManager.isNameStorageCorrupted()) {
        serializationManager.repairNameStorage();
        throw new StorageException("NameStorage for stubs serialization has been corrupted");
      }
    }

    @Override
    public void removeTransientDataForKeys(int inputId, @NotNull Collection<? extends Integer> keys) throws IOException {
      super.removeTransientDataForKeys(inputId, keys);
      MyForwardIndexAccessor forwardIndexAccessor = (MyForwardIndexAccessor)getForwardIndexAccessor();
      for (int key : keys) {
        Map<StubIndexKey, Map<Object, StubIdList>> map = forwardIndexAccessor.deserializeData(getForwardIndexMap().get(key));
        if (map != null) {
          final StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();
          for (StubIndexKey stubIndexKey : map.keySet()) {
            stubIndex.removeTransientDataForFile(stubIndexKey, inputId, map.get(stubIndexKey).keySet());
          }
        }
      }
    }

    @Override
    protected void doClear() throws StorageException, IOException {
      final StubIndexImpl stubIndex = StubIndexImpl.getInstanceOrInvalidate();
      if (stubIndex != null) {
        stubIndex.clearAllIndices();
      }
      myStubVersionMap.clear();
      super.doClear();
    }

    @Override
    protected void doDispose() throws StorageException {
      try {
        super.doDispose();
      }
      finally {
        getStubIndex().dispose();
      }
    }

    private static final FileAttribute VERSION_STAMP = new FileAttribute("stubIndex.versionStamp", 2, true);

    @Override
    public void setIndexedStateForFile(int fileId, @NotNull VirtualFile file) {
      super.setIndexedStateForFile(fileId, file);

      try (DataOutputStream stream = FSRecords.writeAttribute(fileId, VERSION_STAMP)) {
        DataInputOutputUtil.writeINT(stream, myStubVersionMap.getIndexingTimestampDiffForFileType(file.getFileType()));
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    @Override
    public boolean isIndexedStateForFile(int fileId, @NotNull VirtualFile file) {
      boolean indexedStateForFile = super.isIndexedStateForFile(fileId, file);
      if (!indexedStateForFile) return false;

      try {
        DataInputStream stream = FSRecords.readAttributeWithLock(fileId, VERSION_STAMP);
        int diff = stream != null ? DataInputOutputUtil.readINT(stream) : 0;
        if (diff == 0) return false;
        FileType fileType = myStubVersionMap.getFileTypeByIndexingTimestampDiff(diff);
        return fileType != null && myStubVersionMap.getStamp(file.getFileType()) == myStubVersionMap.getStamp(fileType);
      }
      catch (IOException e) {
        LOG.error(e);
        return false;
      }
    }

    private static class MyForwardIndexAccessor extends AbstractForwardIndexAccessor<Integer, SerializedStubTree, Map<StubIndexKey, Map<Object, StubIdList>>, FileContent> {
      public MyForwardIndexAccessor() {super(createStubIndexMapsExternalizer());}

      @Override
      public Map<StubIndexKey, Map<Object, StubIdList>> convertToDataType(@Nullable Map<Integer, SerializedStubTree> map,
                                                                          @Nullable FileContent content) {
        SerializedStubTree stubTree = getValue(map);
        if (stubTree == null) return Collections.emptyMap();
        return calcStubIndicesValueMap(stubTree);
      }

      @Override
      protected InputDataDiffBuilder<Integer, SerializedStubTree> createDiffBuilder(int inputId, @Nullable Map<StubIndexKey, Map<Object, StubIdList>> inputData) {
        return new InputDataDiffBuilder<Integer, SerializedStubTree>(inputId) {
          @Override
          public boolean differentiate(@NotNull Map<Integer, SerializedStubTree> newData,
                                       @NotNull KeyValueUpdateProcessor<? super Integer, ? super SerializedStubTree> addProcessor,
                                       @NotNull KeyValueUpdateProcessor<? super Integer, ? super SerializedStubTree> updateProcessor,
                                       @NotNull RemovedKeyProcessor<? super Integer> removeProcessor) throws StorageException {
            removeProcessor.process(myInputId, myInputId);
            SerializedStubTree newStub = getValue(newData);
            if (newStub != null) {
              addProcessor.process(myInputId, newStub, myInputId);
              return true;
            }
            return false;
          }
        };
      }
    }
  }

  @Nullable
  private static SerializedStubTree getValue(@Nullable Map<Integer, SerializedStubTree> map) {
    return map == null ? null : ContainerUtil.getFirstItem(map.values());
  }
}
