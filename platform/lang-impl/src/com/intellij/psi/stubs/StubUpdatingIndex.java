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
import com.intellij.util.io.*;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.*;
import java.util.*;

/*
 * @author max
 * Key used by this index is the same as key of SingleEntryFileBasedIndexExtension
 */
public class StubUpdatingIndex extends CustomImplementationFileBasedIndexExtension<Integer, SerializedStubTree, FileContent>
  implements PsiDependentIndex, CustomInputsIndexFileBasedIndexExtension<Integer> {
  static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubUpdatingIndex");
  private static final int VERSION = 37 + (PersistentHashMapValueStorage.COMPRESSION_ENABLED ? 1 : 0);

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

  private static final KeyDescriptor<Integer> DATA_DESCRIPTOR = new IntInlineKeyDescriptor();

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

  @NotNull
  @Override
  public DataExternalizer<Collection<Integer>> createExternalizer() {
    return new DataExternalizer<Collection<Integer>>() {
      private volatile boolean myEnsuredStubElementTypesLoaded;

      @Override
      public void save(@NotNull DataOutput out, Collection<Integer> value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.iterator().next());
        Map<StubIndexKey, Map<Object, StubIdList>> stubIndicesValueMap = ((StubUpdatingIndexKeys)value).myStubIndicesValueMap;
        DataInputOutputUtil.writeINT(out, stubIndicesValueMap != null ? stubIndicesValueMap.size() : 0);

        if (stubIndicesValueMap != null && !stubIndicesValueMap.isEmpty()) {
          StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();

          for (StubIndexKey stubIndexKey : stubIndicesValueMap.keySet()) {
            DataInputOutputUtil.writeINT(out, stubIndexKey.getUniqueId());
            Map<Object, StubIdList> map = stubIndicesValueMap.get(stubIndexKey);

            stubIndex.serializeIndexValue(out, stubIndexKey, map);
          }
        }
      }

      @Override
      public Collection<Integer> read(@NotNull DataInput in) throws IOException {
        if (!myEnsuredStubElementTypesLoaded) {
          SerializationManager.getInstance().initSerializers();
          StubIndexImpl.initExtensions();
          myEnsuredStubElementTypesLoaded = true;
        }
        int fileId = DataInputOutputUtil.readINT(in);
        StubUpdatingIndexKeys integers = new StubUpdatingIndexKeys(ContainerUtil.set(fileId));

        int stubIndicesValueMapSize = DataInputOutputUtil.readINT(in);
        if (stubIndicesValueMapSize > 0) {
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
          integers.myStubIndicesValueMap = stubIndicesValueMap;
        }
        return integers;
      }
    };
  }

  static class StubUpdatingIndexKeys extends AbstractSet<Integer> {
    @NotNull
    private final Set<Integer> myBackingMap;
    @NotNull
    private Map<StubIndexKey, Map<Object, StubIdList>> myStubIndicesValueMap = Collections.emptyMap();

    StubUpdatingIndexKeys(@NotNull Set<Integer> backingMap) {
      myBackingMap = backingMap;
    }

    @NotNull
    @Override
    public Iterator<Integer> iterator() {
      return myBackingMap.iterator();
    }

    @Override
    public int size() {
      return myBackingMap.size();
    }
  }

  @NotNull
  @Override
  public DataIndexer<Integer, SerializedStubTree, FileContent> getIndexer() {
    return new DataIndexer<Integer, SerializedStubTree, FileContent>() {
      @Override
      @NotNull
      public Map<Integer, SerializedStubTree> map(@NotNull final FileContent inputData) {
        final Map<Integer, SerializedStubTree> result = new THashMap<Integer, SerializedStubTree>() {
          StubUpdatingIndexKeys myKeySet;

          @NotNull
          @Override
          public Set<Integer> keySet() {
            if (myKeySet == null) {
              myKeySet = new StubUpdatingIndexKeys(super.keySet());
            }

            return myKeySet;
          }
        };

        ApplicationManager.getApplication().runReadAction(() -> {
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

          if (rootStub == null) return;

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
          final int key = SingleEntryFileBasedIndexExtension.getFileKey(file);
          SerializedStubTree serializedStubTree =
            new SerializedStubTree(bytes.getInternalBuffer(), bytes.size(), rootStub, file.getLength(), contentLength);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Indexing " + file + "; lengths=" + serializedStubTree.dumpLengths());
          }
          result.put(key, serializedStubTree);
          try {
            ((StubUpdatingIndexKeys)result.keySet()).myStubIndicesValueMap = calcStubIndicesValueMap(serializedStubTree);
          }
          catch (StorageException ex) {
            throw new RuntimeException(ex);
          }
        });

        return result;
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
  public KeyDescriptor<Integer> getKeyDescriptor() {
    return DATA_DESCRIPTOR;
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
  Map<StubIndexKey, Map<Object, StubIdList>> calcStubIndicesValueMap(@NotNull SerializedStubTree stub) throws StorageException {
    try {
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
      throw new StorageException(e);
    }
  }

  private static class MyIndex extends VfsAwareMapReduceIndex<Integer, SerializedStubTree, FileContent> {
    private StubIndexImpl myStubIndex;
    private final StubVersionMap myStubVersionMap = new StubVersionMap();

    MyIndex(@NotNull FileBasedIndexExtension<Integer, SerializedStubTree> extension, @NotNull IndexStorage<Integer, SerializedStubTree> storage)
      throws StorageException, IOException {
      super(extension, storage);
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
      private Collection<Integer> oldStubIndexKeys;

      StubUpdatingData(@NotNull Map<Integer, SerializedStubTree> newData,
                       @NotNull ThrowableComputable<InputDataDiffBuilder<Integer, SerializedStubTree>, IOException> iterator,
                       @Nullable ThrowableRunnable<IOException> forwardIndexUpdate) {
        super(newData, iterator, INDEX_ID, forwardIndexUpdate);
      }

      @NotNull
      @Override
      protected ThrowableComputable<InputDataDiffBuilder<Integer, SerializedStubTree>, IOException> getCurrentDataEvaluator() {
        return () -> {
          final InputDataDiffBuilder<Integer, SerializedStubTree> diffBuilder = super.getCurrentDataEvaluator().compute();
          if (diffBuilder instanceof CollectionInputDataDiffBuilder) {
            oldStubIndexKeys = ((CollectionInputDataDiffBuilder<Integer, SerializedStubTree>)diffBuilder).getSeq();
          } else if (diffBuilder instanceof MapInputDataDiffBuilder) {
            oldStubIndexKeys = ((MapInputDataDiffBuilder<Integer, SerializedStubTree>)diffBuilder).getMap().keySet();
          }
          return diffBuilder;
        };
      }

      @NotNull
      Map<StubIndexKey, Map<Object, StubIdList>> getOldStubIndicesValueMap() {
        return getStubIndicesValuesMap(oldStubIndexKeys);
      }

      @NotNull
      Map<StubIndexKey, Map<Object, StubIdList>> getNewStubIndicesValueMap() {
        return getStubIndicesValuesMap(getNewData().keySet());
      }

      @NotNull
      private static Map<StubIndexKey, Map<Object, StubIdList>> getStubIndicesValuesMap(@NotNull Collection<Integer> keys) {
        if (keys instanceof StubUpdatingIndexKeys) {
          return ((StubUpdatingIndexKeys)keys).myStubIndicesValueMap;
        }
        return Collections.emptyMap();
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
      final Map<StubIndexKey, Map<Object, StubIdList>> newStubIndicesValueMap = stubUpdatingData.getNewStubIndicesValueMap();

      try {
        getWriteLock().lock();

        super.updateWithMap(inputId, updateData);

        final Map<StubIndexKey, Map<Object, StubIdList>> previousStubIndicesValueMap = stubUpdatingData.getOldStubIndicesValueMap();

        updateStubIndices(
          getAffectedIndices(previousStubIndicesValueMap, newStubIndicesValueMap),
          inputId,
          previousStubIndicesValueMap,
          newStubIndicesValueMap
        );
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
    public void removeTransientDataForKeys(int inputId, @NotNull Collection<? extends Integer> keys) {
      super.removeTransientDataForKeys(inputId, keys);
      if (keys instanceof StubUpdatingIndexKeys) {
        Map<StubIndexKey, Map<Object, StubIdList>> stubIndicesValueMap = ((StubUpdatingIndexKeys)keys).myStubIndicesValueMap;
        final StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();
        for (StubIndexKey key : stubIndicesValueMap.keySet()) {
          stubIndex.removeTransientDataForFile(key, inputId, stubIndicesValueMap.get(key).keySet());
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
  }
}
