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

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IntInlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

/*
 * @author max
 */
public class StubUpdatingIndex extends CustomImplementationFileBasedIndexExtension<Integer, SerializedStubTree, FileContent> implements PsiDependentIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubUpdatingIndex");

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

  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    @Override
    public boolean acceptInput(@NotNull final VirtualFile file) {
      return canHaveStub(file);
    }
  };

  public static boolean canHaveStub(@NotNull VirtualFile file) {
    final FileType fileType = file.getFileType();
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
        if (IndexingStamp.isFileIndexedStateCurrent(file, INDEX_ID)) {
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
  public DataIndexer<Integer, SerializedStubTree, FileContent> getIndexer() {
    return new DataIndexer<Integer, SerializedStubTree, FileContent>() {
      @Override
      @NotNull
      public Map<Integer, SerializedStubTree> map(@NotNull final FileContent inputData) {
        final Map<Integer, SerializedStubTree> result = new HashMap<Integer, SerializedStubTree>();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            final Stub rootStub = StubTreeBuilder.buildStubTree(inputData);
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
                  SerializationManagerEx.getInstanceEx().deserialize(new ByteArrayInputStream(bytes.getInternalBuffer(), 0, bytes.size()));
                check(deserialized, rootStub);
              } catch(ProcessCanceledException pce) {
                throw pce;
              } catch (Throwable t) {
                LOG.error("Error indexing:" + file, t);
              }
            }
            final int key = Math.abs(FileBasedIndex.getFileId(file));
            result.put(key, new SerializedStubTree(bytes.getInternalBuffer(), bytes.size(), rootStub, file.getLength(), contentLength));
          }
        });

        return result;
      }
    };
  }

  private static void check(Stub stub, Stub stub2) {
    assert stub.getStubType() == stub2.getStubType();
    List<? extends Stub> stubs = stub.getChildrenStubs();
    List<? extends Stub> stubs2 = stub2.getChildrenStubs();
    assert stubs.size() == stubs2.size();
    for(int i = 0, len = stubs.size(); i < len; ++i) {
      check(stubs.get(i), stubs2.get(i));
    }
  }

  private static void rememberIndexingStamp(final VirtualFile file, long contentLength) {
    try {
      DataOutputStream stream = INDEXED_STAMP.writeAttribute(file);
      DataInputOutputUtil.writeTIME(stream, file.getTimeStamp());
      DataInputOutputUtil.writeLONG(stream, contentLength);
      stream.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static String getIndexingStampInfo(VirtualFile file) {
    try {
      DataInputStream stream = INDEXED_STAMP.readAttribute(file);
      if (stream == null) {
        return "no data";
      }

      long stamp = DataInputOutputUtil.readTIME(stream);
      long size = DataInputOutputUtil.readLONG(stream);
      stream.close();
      return "indexed at " + stamp + " with size " + size;
    }
    catch (IOException e) {
      return ExceptionUtil.getThrowableText(e);
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
    return CumulativeStubVersion.getCumulativeVersion();
  }

  @NotNull
  @Override
  public UpdatableIndex<Integer, SerializedStubTree, FileContent> createIndexImplementation(@NotNull final ID<Integer, SerializedStubTree> indexId, @NotNull final FileBasedIndex owner, @NotNull IndexStorage<Integer, SerializedStubTree> storage)
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
    return new MyIndex(indexId, storage, getIndexer());
  }

  private static void updateStubIndices(@NotNull final Collection<StubIndexKey> indexKeys,
                                        final int inputId,
                                        @NotNull final Map<StubIndexKey, Map<Object, StubIdList>> oldStubTree,
                                        @NotNull final Map<StubIndexKey, Map<Object, StubIdList>> newStubTree) {
    final StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();
    for (StubIndexKey key : indexKeys) {
      final Map<Object, StubIdList> oldMap = oldStubTree.get(key);
      final Map<Object, StubIdList> newMap = newStubTree.get(key);

      final Map<Object, StubIdList> _oldMap = oldMap != null ? oldMap : Collections.<Object, StubIdList>emptyMap();
      final Map<Object, StubIdList> _newMap = newMap != null ? newMap : Collections.<Object, StubIdList>emptyMap();

      stubIndex.updateIndex(key, inputId, _oldMap, _newMap);
    }
  }

  @NotNull
  private static Collection<StubIndexKey> getAffectedIndices(@NotNull final Map<StubIndexKey, Map<Object, StubIdList>> oldStubTree,
                                                             @NotNull final Map<StubIndexKey, Map<Object, StubIdList>> newStubTree) {
    Set<StubIndexKey> allIndices = new HashSet<StubIndexKey>();
    allIndices.addAll(oldStubTree.keySet());
    allIndices.addAll(newStubTree.keySet());
    return allIndices;
  }

  private static class MyIndex extends MapReduceIndex<Integer, SerializedStubTree, FileContent> {
    private StubIndexImpl myStubIndex;

    public MyIndex(final ID<Integer, SerializedStubTree> indexId, final IndexStorage<Integer, SerializedStubTree> storage, final DataIndexer<Integer, SerializedStubTree, FileContent> indexer)
      throws StorageException, IOException {
      super(indexId, indexer, storage);
      checkNameStorage();
    }

    @Override
    public void flush() throws StorageException {
      final StubIndexImpl stubIndex = getStubIndex();
      try {
        for (StubIndexKey key : stubIndex.getAllStubIndexKeys()) {
          stubIndex.flush(key);
        }
      }
      finally {
        super.flush();
      }
    }

    @Override
    protected void updateWithMap(final int inputId,
                                 @NotNull UpdateData<Integer, SerializedStubTree> updateData)
      throws StorageException {

      checkNameStorage();
      final Map<StubIndexKey, Map<Object, StubIdList>> newStubTree;
      try {
        newStubTree = getStubTree(((SimpleUpdateData)updateData).getNewData());
      }
      catch (SerializerNotFoundException e) {
        throw new StorageException(e);
      }

      final StubIndexImpl stubIndex = getStubIndex();
      final Collection<StubIndexKey> allStubIndices = stubIndex.getAllStubIndexKeys();
      try {
        // first write-lock affected stub indices to avoid deadlocks
        for (StubIndexKey key : allStubIndices) {
          stubIndex.getWriteLock(key).lock();
        }

        try {
          getWriteLock().lock();

          final Map<Integer, SerializedStubTree> oldData = readOldData(inputId);

          final Map<StubIndexKey, Map<Object, StubIdList>> oldStubTree;
          try {
            oldStubTree = getStubTree(oldData);
          }
          catch (SerializerNotFoundException e) {
            throw new StorageException(e);
          }

          super.updateWithMap(inputId, updateData);

          updateStubIndices(getAffectedIndices(oldStubTree, newStubTree), inputId, oldStubTree, newStubTree);
        }
        finally {
          getWriteLock().unlock();
        }
      }
      finally {
        for (StubIndexKey key : allStubIndices) {
          stubIndex.getWriteLock(key).unlock();
        }
      }
    }

    private StubIndexImpl getStubIndex() {
      StubIndexImpl index = myStubIndex;
      if (index == null) {
        index = myStubIndex = (StubIndexImpl)StubIndex.getInstance();
      }
      return index;
    }

    private static void checkNameStorage() throws StorageException {
      final SerializationManagerEx serializationManager = SerializationManagerEx.getInstanceEx();
      if (serializationManager.isNameStorageCorrupted()) {
        serializationManager.repairNameStorage();
        //noinspection ThrowFromFinallyBlock
        throw new StorageException("NameStorage for stubs serialization has been corrupted");
      }
    }

    private static Map<StubIndexKey, Map<Object, StubIdList>> getStubTree(@NotNull final Map<Integer, SerializedStubTree> data)
      throws SerializerNotFoundException {
      final Map<StubIndexKey, Map<Object, StubIdList>> stubTree;
      if (!data.isEmpty()) {
        Map.Entry<Integer, SerializedStubTree> entry = data.entrySet().iterator().next();
        final SerializedStubTree stub = entry.getValue();
        ObjectStubBase root = (ObjectStubBase)stub.getStub(true);
        if (root instanceof PsiFileStub) {
          Integer fileId = entry.getKey();
          root.putUserData(IndexingDataKeys.VIRTUAL_FILE_ID, fileId);
        }
        ObjectStubTree objectStubTree = root instanceof PsiFileStub ? new StubTree((PsiFileStub)root, false) :
                                        new ObjectStubTree(root, false);
        Map<StubIndexKey, Map<Object, int[]>> map = objectStubTree.indexStubTree();

        // xxx:fix refs inplace
        stubTree = (Map)map;
        for(StubIndexKey key:map.keySet()) {
          Map<Object, int[]> value = map.get(key);
          for(Object k: value.keySet()) {
            int[] ints = value.get(k);
            StubIdList stubList = ints.length == 1 ? new StubIdList(ints[0]) : new StubIdList(ints, ints.length);
            ((Map<Object, StubIdList>)(Map)value).put(k, stubList);
          }
        }
      }
      else {
        stubTree = Collections.emptyMap();
      }
      return stubTree;
    }

    /*MUST be called under the WriteLock*/
    @NotNull
    private Map<Integer, SerializedStubTree> readOldData(final int key) throws StorageException {
      final Map<Integer, SerializedStubTree> result = new THashMap<Integer, SerializedStubTree>();

      IndexStorage<Integer, SerializedStubTree> indexStorage = myStorage;
      if (indexStorage instanceof MemoryIndexStorage) {
        final MemoryIndexStorage<Integer, SerializedStubTree> memIndexStorage = (MemoryIndexStorage<Integer, SerializedStubTree>)indexStorage;
        if (!memIndexStorage.isBufferingEnabled()) {
          // if buffering is not enabled, use backend storage to make sure
          // the returned stub tree contains no data corresponding to unsaved documents.
          // This will ensure that correct set of old keys is used for update
          indexStorage = memIndexStorage.getBackendStorage();
        }
      }
      try {
        final ValueContainer<SerializedStubTree> valueContainer = indexStorage.read(key);
        if (valueContainer.size() != 1) {
          LOG.assertTrue(valueContainer.size() == 0);
          return result;
        }

        result.put(key, valueContainer.getValueIterator().next());
        return result;
      }
      catch (RuntimeException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof IOException) {
          throw new StorageException(cause);
        }
        throw e;
      }
    }

    @Override
    public void clear() throws StorageException {
      final StubIndexImpl stubIndex = StubIndexImpl.getInstanceOrInvalidate();
      final Collection<StubIndexKey> allStubIndexKeys = stubIndex != null? stubIndex.getAllStubIndexKeys() : Collections.<StubIndexKey>emptyList();
      try {
        for (StubIndexKey key : allStubIndexKeys) {
          //noinspection ConstantConditions
          stubIndex.getWriteLock(key).lock();
        }
        getWriteLock().lock();
        if (stubIndex != null) {
          stubIndex.clearAllIndices();
        }
        super.clear();
      }
      finally {
        getWriteLock().unlock();
        for (StubIndexKey key : allStubIndexKeys) {
          //noinspection ConstantConditions
          stubIndex.getWriteLock(key).unlock();
        }
      }
    }

    @Override
    public void dispose() {
      try {
        super.dispose();
      }
      finally {
        getStubIndex().dispose();
      }
    }
  }
}
