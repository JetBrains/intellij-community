/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IntInlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

/*
 * @author max
 */
public class StubUpdatingIndex extends CustomImplementationFileBasedIndexExtension<Integer, SerializedStubTree, FileContent> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubUpdatingIndex");

  public static final ID<Integer, SerializedStubTree> INDEX_ID = ID.create("Stubs");

  private static final int VERSION = 20;

  private static final DataExternalizer<SerializedStubTree> KEY_EXTERNALIZER = new DataExternalizer<SerializedStubTree>() {
    @Override
    public void save(final DataOutput out, final SerializedStubTree v) throws IOException {
      v.write(out);
    }

    @Override
    public SerializedStubTree read(final DataInput in) throws IOException {
      return new SerializedStubTree(in);
    }
  };

  private static final FileBasedIndexIndicesManager.InputFilter INPUT_FILTER = new FileBasedIndexIndicesManager.InputFilter() {
    @Override
    public boolean acceptInput(final VirtualFile file) {
      return canHaveStub(file);
    }
  };

  public static boolean canHaveStub(VirtualFile file) {
    final FileType fileType = file.getFileType();
    if (fileType instanceof LanguageFileType) {
      Language l = ((LanguageFileType)fileType).getLanguage();
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
      if (parserDefinition == null) return false;

      final IFileElementType elementType = parserDefinition.getFileNodeType();
      return elementType instanceof IStubFileElementType &&
            (((IStubFileElementType)elementType).shouldBuildStubFor(file) || myIndexingStamp.isFileIndexed(file, INDEX_ID, IndexInfrastructure.getIndexCreationStamp(INDEX_ID)));
    }
    if (fileType.isBinary()) {
      final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
      return builder != null && builder.acceptsFile(file);
    }

    return false;
  }

  private static final KeyDescriptor<Integer> DATA_DESCRIPTOR = new IntInlineKeyDescriptor();

  @Override
  public ID<Integer, SerializedStubTree> getName() {
    return INDEX_ID;
  }

  @Override
  public int getCacheSize() {
    return 5; // no need to cache many serialized trees
  }

  @Override
  public DataIndexer<Integer, SerializedStubTree, FileContent> getIndexer() {
    return new DataIndexer<Integer, SerializedStubTree, FileContent>() {
      @Override
      @NotNull
      public Map<Integer, SerializedStubTree> map(final FileContent inputData) {
        final Map<Integer, SerializedStubTree> result = new HashMap<Integer, SerializedStubTree>();

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            final StubElement rootStub = StubTreeBuilder.buildStubTree(inputData);
            if (rootStub == null) return;

            final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
            SerializationManager.getInstance().serialize(rootStub, bytes);

            final int key = Math.abs(FileBasedIndex.getFileId(inputData.getFile()));
            result.put(key, new SerializedStubTree(bytes.getInternalBuffer(), bytes.size()));
          }
        });

        return result;
      }
    };
  }

  @Override
  public KeyDescriptor<Integer> getKeyDescriptor() {
    return DATA_DESCRIPTOR;
  }

  @Override
  public DataExternalizer<SerializedStubTree> getValueExternalizer() {
    return KEY_EXTERNALIZER;
  }

  @Override
  public FileBasedIndexIndicesManager.InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return getCumulativeVersion();
  }

  private static int getCumulativeVersion() {
    int version = VERSION;
    for (final FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes()) {
      if (fileType instanceof LanguageFileType) {
        Language l = ((LanguageFileType)fileType).getLanguage();
        ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(l);
        if (parserDefinition != null) {
          final IFileElementType type = parserDefinition.getFileNodeType();
          if (type instanceof IStubFileElementType) {
            version += ((IStubFileElementType)type).getStubVersion();
          }
        }
      }
      else if (fileType.isBinary()) {
        final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
        if (builder != null) {
          version += builder.getStubVersion();
        }
      }
    }
    return version;
  }

  @Override
  public UpdatableIndex<Integer, SerializedStubTree, FileContent> createIndexImplementation(final ID<Integer, SerializedStubTree> indexId, final FileBasedIndex owner, IndexStorage<Integer, SerializedStubTree> storage) {
    if (storage instanceof MemoryIndexStorage) {
      final MemoryIndexStorage<Integer, SerializedStubTree> memStorage = (MemoryIndexStorage<Integer, SerializedStubTree>)storage;
      memStorage.addBufferingStateListsner(new MemoryIndexStorage.BufferingStateListener() {
        @Override
        public void bufferingStateChanged(final boolean newState) {
          ((StubIndexImpl)StubIndexImpl.getInstance()).setDataBufferingEnabled(newState);
        }

        @Override
        public void memoryStorageCleared() {
          ((StubIndexImpl)StubIndexImpl.getInstance()).cleanupMemoryStorage();
        }
      });
    }
    return new MyIndex(indexId, storage, getIndexer());
  }

  private static void updateStubIndices(final Collection<StubIndexKey> indexKeys, final int inputId, final Map<StubIndexKey, Map<Object, TIntArrayList>> oldStubTree, final Map<StubIndexKey, Map<Object, TIntArrayList>> newStubTree) {
    final StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();
    for (StubIndexKey key : indexKeys) {
      final Map<Object, TIntArrayList> oldMap = oldStubTree.get(key);
      final Map<Object, TIntArrayList> newMap = newStubTree.get(key);

      final Map<Object, TIntArrayList> _oldMap = oldMap != null ? oldMap : Collections.<Object, TIntArrayList>emptyMap();
      final Map<Object, TIntArrayList> _newMap = newMap != null ? newMap : Collections.<Object, TIntArrayList>emptyMap();

      stubIndex.updateIndex(key, inputId, _oldMap, _newMap);
    }
  }

  private static Collection<StubIndexKey> getAffectedIndices(final Map<StubIndexKey, Map<Object, TIntArrayList>> oldStubTree, final Map<StubIndexKey, Map<Object, TIntArrayList>> newStubTree) {
    Set<StubIndexKey> allIndices = new HashSet<StubIndexKey>();
    allIndices.addAll(oldStubTree.keySet());
    allIndices.addAll(newStubTree.keySet());
    return allIndices;
  }

  private static class MyIndex extends MapReduceIndex<Integer, SerializedStubTree, FileContent> {
    private StubIndexImpl myStubIndex;

    public MyIndex(final ID<Integer, SerializedStubTree> indexId, final IndexStorage<Integer, SerializedStubTree> storage, final DataIndexer<Integer, SerializedStubTree, FileContent> indexer) {
      super(indexId, indexer, storage);
      try {
        checkNameStorage();
      }
      catch (StorageException e) {
        LOG.info(e);
        FileBasedIndex.getInstance().requestRebuild(INDEX_ID);
      }
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
    protected void updateWithMap(final int inputId, final Map<Integer, SerializedStubTree> newData, Callable<Collection<Integer>> oldKeysGetter)
      throws StorageException {

      checkNameStorage();
      final Map<StubIndexKey, Map<Object, TIntArrayList>> newStubTree = getStubTree(newData);

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
          final Map<StubIndexKey, Map<Object, TIntArrayList>> oldStubTree = getStubTree(oldData);

          super.updateWithMap(inputId, newData, oldKeysGetter);

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
      final SerializationManager serializationManager = SerializationManager.getInstance();
      if (serializationManager.isNameStorageCorrupted()) {
        serializationManager.repairNameStorage();
        //noinspection ThrowFromFinallyBlock
        throw new StorageException("NameStorage for stubs serialization has been corrupted");
      }
    }

    private static Map<StubIndexKey, Map<Object, TIntArrayList>> getStubTree(final Map<Integer, SerializedStubTree> data) {
      final Map<StubIndexKey, Map<Object, TIntArrayList>> stubTree;
      if (!data.isEmpty()) {
        final SerializedStubTree stub = data.values().iterator().next();
        stubTree = new StubTree((PsiFileStub)stub.getStub()).indexStubTree();
      }
      else {
        stubTree = Collections.emptyMap();
      }
      return stubTree;
    }

    /*MUST be called under the WriteLock*/
    private Map<Integer, SerializedStubTree> readOldData(final int key) throws StorageException {
      final Map<Integer, SerializedStubTree> result = new HashMap<Integer, SerializedStubTree>();

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
