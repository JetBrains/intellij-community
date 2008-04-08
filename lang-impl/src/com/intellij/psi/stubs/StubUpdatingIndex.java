/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
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
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

public class StubUpdatingIndex implements CustomImplementationFileBasedIndexExtension<Integer, SerializedStubTree, FileContent> {
  public static final ID<Integer, SerializedStubTree> INDEX_ID = ID.create("StubUpdatingIndex");
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
    final FileType fileType = inputData.getFile().getFileType();
    if (fileType.isBinary()) {
      final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
      assert builder != null;

      return builder.buildStubTree(inputData.getContent());
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
    return 2;
  }

  public UpdatableIndex<Integer, SerializedStubTree, FileContent> createIndexImplementation(final IndexStorage<Integer, SerializedStubTree> storage) {
    return new MyIndex(storage, getIndexer());
  }

  private static class MyIndex extends MapReduceIndex<Integer, SerializedStubTree, FileContent> {
    public MyIndex(final IndexStorage<Integer, SerializedStubTree> storage,
                   final DataIndexer<Integer, SerializedStubTree, FileContent> indexer) {
      super(indexer, storage);
    }

    protected void updateWithMap(final int inputId, final Map<Integer, SerializedStubTree> oldData, final Map<Integer, SerializedStubTree> newData)
        throws StorageException {
      super.updateWithMap(inputId, oldData, newData);

      final Map<StubIndexKey, Map<Object, TIntArrayList>> oldStubTree = getStubTree(oldData);
      final Map<StubIndexKey, Map<Object, TIntArrayList>> newStubTree = getStubTree(newData);

      Set<StubIndexKey> allIndices = new HashSet<StubIndexKey>();
      allIndices.addAll(oldStubTree.keySet());
      allIndices.addAll(newStubTree.keySet());
      for (StubIndexKey key : allIndices) {
        final Map oldMap = oldStubTree.get(key);
        final Map newMap = newStubTree.get(key);

        ((StubIndexImpl)StubIndex.getInstance()).updateIndex(key, inputId, oldMap != null ? oldMap : Collections.emptyMap(),
                                                             newMap != null ? newMap : Collections.emptyMap());
      }
    }

    private Map<StubIndexKey, Map<Object, TIntArrayList>> getStubTree(final Map<Integer, SerializedStubTree> oldData) {
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
  }
}