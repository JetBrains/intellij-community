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
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentEnumerator;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class StubIndex implements FileBasedIndexExtension<Integer, SerializedStubContent> {
  public static final ID<Integer, SerializedStubContent> INDEX_ID = ID.create("StubIndex");
  private static final DataExternalizer<SerializedStubContent> KEY_EXTERNALIZER = new DataExternalizer<SerializedStubContent>() {
    public void save(final DataOutput out, final SerializedStubContent v) throws IOException {
      byte[] value = v.getBytes();
      out.writeInt(value.length);
      out.write(value);
    }

    public SerializedStubContent read(final DataInput in) throws IOException {
      int len = in.readInt();
      byte[] result = new byte[len];
      in.readFully(result);
      return new SerializedStubContent(result);
    }
  };
  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      final FileType fileType = file.getFileType();
      if (fileType instanceof LanguageFileType) {
        Language l = ((LanguageFileType)fileType).getLanguage();
        return LanguageParserDefinitions.INSTANCE.forLanguage(l).getFileNodeType() instanceof IStubFileElementType;
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

  public ID<Integer, SerializedStubContent> getName() {
    return INDEX_ID;
  }

  public DataIndexer<Integer, SerializedStubContent, FileBasedIndex.FileContent> getIndexer() {
    return new DataIndexer<Integer, SerializedStubContent, FileBasedIndex.FileContent>() {
      public Map<Integer, SerializedStubContent> map(final FileBasedIndex.FileContent inputData) {
        final Map<Integer, SerializedStubContent> result = new HashMap<Integer, SerializedStubContent>();
        if (!(inputData.file instanceof NewVirtualFile)) return result;

        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final int key = Math.abs(FileBasedIndex.getFileId(inputData.file));
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(bytes);
            final LanguageFileType filetype = (LanguageFileType)inputData.file.getFileType();
            Language l = filetype.getLanguage();
            final IFileElementType type = LanguageParserDefinitions.INSTANCE.forLanguage(l).getFileNodeType();

            Project project = ProjectManager.getInstance().getDefaultProject(); // TODO

            PsiFile copy =
              PsiFileFactory.getInstance(project).createFileFromText(inputData.fileName, filetype, inputData.content, 1, false, false);

            final StubElement stub = ((IStubFileElementType)type).getBuilder().buildStubTree(copy);

            SerializationManager.getInstance().serialize(stub, stream);
            result.put(key, new SerializedStubContent(bytes.toByteArray()));
          }
        });

        return result;
      }
    };
  }

  public PersistentEnumerator.DataDescriptor<Integer> getKeyDescriptor() {
    return DATA_DESCRIPTOR;
  }

  public DataExternalizer<SerializedStubContent> getValueExternalizer() {
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
}