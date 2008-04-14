/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentEnumerator;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;

public class StubUpdatingIndex implements CustomImplementationFileBasedIndexExtension<Integer, SerializedStubTree, FileContent> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubUpdatingIndex");

  public static final ID<Integer, SerializedStubTree> INDEX_ID = ID.create("StubUpdatingIndex");
  private static final int VERSION = 2;
  private static final DataExternalizer<SerializedStubTree> KEY_EXTERNALIZER = new DataExternalizer<SerializedStubTree>() {
    public void save(final DataOutput out, final SerializedStubTree v) throws IOException {
      byte[] value = v.getBytes();
      out.writeInt(value.length);
      out.write(value);
    }

    public SerializedStubTree read(final DataInput in) throws IOException {
      int len = in.readInt();
      byte[] result = new byte[len];
      in.readFully(result);
      return new SerializedStubTree(result);
    }
  };

  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      final FileType fileType = file.getFileType();
      if (fileType instanceof LanguageFileType) {
        Language l = ((LanguageFileType)fileType).getLanguage();
        return LanguageParserDefinitions.INSTANCE.forLanguage(l).getFileNodeType() instanceof IStubFileElementType;
      }
      else if (fileType.isBinary()) {
        final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
        return builder != null && builder.acceptsFile(file);
      }

      return false;
    }
  };

  private static final PersistentEnumerator.DataDescriptor<Integer> DATA_DESCRIPTOR = new PersistentEnumerator.DataDescriptor<Integer>() {
    public int getHashCode(final Integer value) {
      return value.hashCode();
    }

    public boolean isEqual(final Integer val1, final Integer val2) {
      return val1.equals(val2);
    }

    public void save(final DataOutput out, final Integer value) throws IOException {
      out.writeInt(value.intValue());
    }

    public Integer read(final DataInput in) throws IOException {
      return in.readInt();
    }
  };

  public ID<Integer, SerializedStubTree> getName() {
    return INDEX_ID;
  }

  public DataIndexer<Integer, SerializedStubTree, FileContent> getIndexer() {
    return new DataIndexer<Integer, SerializedStubTree, FileContent>() {
      public Map<Integer, SerializedStubTree> map(final FileContent inputData) {
        final Map<Integer, SerializedStubTree> result = new HashMap<Integer, SerializedStubTree>();
        if (!(inputData.getFile() instanceof NewVirtualFile)) return result;

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final StubElement rootStub = buildStubTree(inputData);
            if (rootStub == null) return;

            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(bytes);
            SerializationManager.getInstance().serialize(rootStub, stream);

            final int key = Math.abs(FileBasedIndex.getFileId(inputData.getFile()));
            result.put(key, new SerializedStubTree(bytes.toByteArray()));
          }
        });

        return result;
      }
    };
  }

  @Nullable
  private static StubElement buildStubTree(final FileContent inputData) {
    final VirtualFile file = inputData.getFile();
    final FileType fileType = file.getFileType();
    if (fileType.isBinary()) {
      final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
      assert builder != null;

      return builder.buildStubTree(file, inputData.getContent());
    }

    final LanguageFileType filetype = (LanguageFileType)fileType;
    Language l = filetype.getLanguage();
    final IFileElementType type = LanguageParserDefinitions.INSTANCE.forLanguage(l).getFileNodeType();

    Project project = ProjectManager.getInstance().getDefaultProject(); // TODO

    PsiFile copy =
      PsiFileFactory.getInstance(project).createFileFromText(inputData.getFileName(), filetype, inputData.getContentAsText(), 1, false, false);

    return ((IStubFileElementType)type).getBuilder().buildStubTree(copy);
  }

  public PersistentEnumerator.DataDescriptor<Integer> getKeyDescriptor() {
    return DATA_DESCRIPTOR;
  }

  public DataExternalizer<SerializedStubTree> getValueExternalizer() {
    return KEY_EXTERNALIZER;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public int getVersion() {
    return VERSION;
  }

  public UpdatableIndex<Integer, SerializedStubTree, FileContent> createIndexImplementation(final FileBasedIndex owner, IndexStorage<Integer, SerializedStubTree> storage) {
    if (storage instanceof MemoryIndexStorage) {
      final MemoryIndexStorage<Integer, SerializedStubTree> memStorage = (MemoryIndexStorage<Integer, SerializedStubTree>)storage;
      memStorage.addBufferingStateListsner(new MemoryIndexStorage.BufferingStateListener() {
        public void bufferingStateChanged(final boolean newState) {
          ((StubIndexImpl)StubIndexImpl.getInstance()).setDataBufferingEnabled(newState);
        }
      });
    }
    return new MyIndex(owner, storage, getIndexer());
  }

  public static void rebuildStubIndices() {
    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      new Task.Modal(null, "Updating index", false) {
        public void run(@NotNull final ProgressIndicator indicator) {
          doRebuild();
        }
      }.queue();
    }
    else {
      doRebuild();
    }
  }

  private static void doRebuild() {
    FileBasedIndex.getInstance().processAllValues(INDEX_ID, new FileBasedIndex.AllValuesProcessor<SerializedStubTree>() {
      public void process(final int inputId, final SerializedStubTree value) {
        final Map<StubIndexKey, Map<Object, TIntArrayList>> stubTree = new StubTree((PsiFileStub)value.getStub()).indexStubTree();
        updateAffectedStubIndices(inputId, Collections.<StubIndexKey, Map<Object, TIntArrayList>>emptyMap(), stubTree);
      }
    });
  }

  private static void updateAffectedStubIndices(final int inputId, final Map<StubIndexKey, Map<Object, TIntArrayList>> oldStubTree,
                                         final Map<StubIndexKey, Map<Object, TIntArrayList>> newStubTree) {
    Set<StubIndexKey> allIndices = new HashSet<StubIndexKey>();
    allIndices.addAll(oldStubTree.keySet());
    allIndices.addAll(newStubTree.keySet());
    for (StubIndexKey key : allIndices) {
        final Map<Object, TIntArrayList> oldMap = oldStubTree.get(key);
        final Map<Object, TIntArrayList> newMap = newStubTree.get(key);
    
        final Map<Object, TIntArrayList> _oldMap = oldMap != null ? oldMap : Collections.<Object, TIntArrayList>emptyMap();
        final Map<Object, TIntArrayList> _newMap = newMap != null ? newMap : Collections.<Object, TIntArrayList>emptyMap();
            
        final StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();
        stubIndex.updateIndex(key, inputId, _oldMap, _newMap);
      }
  }

  private static class MyIndex extends MapReduceIndex<Integer, SerializedStubTree, FileContent> {
    public MyIndex(final FileBasedIndex owner, final IndexStorage<Integer, SerializedStubTree> storage,
                   final DataIndexer<Integer, SerializedStubTree, FileContent> indexer) {
      super(indexer, storage);
      try {
        checkNameStorage();
      }
      catch (StorageException e) {
        owner.requestRebuild(INDEX_ID);
      }
    }

    protected void updateWithMap(final int inputId, final Map<Integer, SerializedStubTree> oldData, final Map<Integer, SerializedStubTree> newData)
        throws StorageException {

      checkNameStorage();
      final Map<StubIndexKey, Map<Object, TIntArrayList>> oldStubTree = getStubTree(oldData);
      final Map<StubIndexKey, Map<Object, TIntArrayList>> newStubTree = getStubTree(newData);
      final Lock lock = getWriteLock();
      lock.lock();
      try {
        super.updateWithMap(inputId, oldData, newData);
        updateAffectedStubIndices(inputId, oldStubTree, newStubTree);
      }
      finally {
        lock.unlock();
      }
    }

    private static void checkNameStorage() throws StorageException {
      final SerializationManager serializationManager = SerializationManager.getInstance();
      if (serializationManager.isNameStorageCorrupted()) {
        serializationManager.repairNameStorage();
        //noinspection ThrowFromFinallyBlock
        throw new StorageException("NameStorage for stubs serialization has been corrupted");
      }
    }

    private static Map<StubIndexKey, Map<Object, TIntArrayList>> getStubTree(final Map<Integer, SerializedStubTree> oldData) {
      final Map<StubIndexKey, Map<Object, TIntArrayList>> oldStubTree;
      if (!oldData.isEmpty()) {
        final SerializedStubTree stub = oldData.values().iterator().next();
        oldStubTree = new StubTree((PsiFileStub)stub.getStub()).indexStubTree();
      }
      else {
        oldStubTree = Collections.emptyMap();
      }
      return oldStubTree;
    }

    protected Map<Integer, SerializedStubTree> mapOld(final FileContent inputData) throws StorageException {
      checkNameStorage();
      if (inputData == null) {
        return Collections.emptyMap();
      }
      final int key = Math.abs(FileBasedIndex.getFileId(inputData.getFile()));

      final Map<Integer, SerializedStubTree> result = new HashMap<Integer, SerializedStubTree>();
      final Lock lock = getReadLock();
      lock.lock();
      try {
        final ValueContainer<SerializedStubTree> valueContainer = getData(key);
        if (valueContainer.size() == 0) {
          return result;
        }

        assert valueContainer.size() == 1;
        result.put(key, valueContainer.getValueIterator().next());
      }
      finally {
        lock.unlock();
      }

      return result;
    }
    
    public void clear() throws StorageException {
      final Lock lock = getWriteLock();
      lock.lock();
      try {
        ((StubIndexImpl)StubIndex.getInstance()).clearAllIndices();
        super.clear();
      }
      finally {
        lock.unlock();
      }
    }
    
  }
}
