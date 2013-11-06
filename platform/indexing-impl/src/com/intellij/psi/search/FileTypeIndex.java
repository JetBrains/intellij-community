package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class FileTypeIndex extends ScalarIndexExtension<FileType>
  implements FileBasedIndex.InputFilter, KeyDescriptor<FileType>, DataIndexer<FileType, Void, FileContent> {
  private final EnumeratorStringDescriptor myEnumeratorStringDescriptor = new EnumeratorStringDescriptor();

  public static Collection<VirtualFile> getFiles(FileType fileType, GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, fileType, scope);
  }

  public static final ID<FileType, Void> NAME = ID.create("filetypes");

  private final FileTypeRegistry myFileTypeManager;

  public FileTypeIndex(FileTypeRegistry fileTypeRegistry) {
    myFileTypeManager = fileTypeRegistry;
  }

  @NotNull
  @Override
  public ID<FileType, Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<FileType, Void, FileContent> getIndexer() {
    return this;
  }

  @Override
  public KeyDescriptor<FileType> getKeyDescriptor() {
    return this;
  }

  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return this;
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public int getVersion() {
    FileType[] types = myFileTypeManager.getRegisteredFileTypes();
    int version = 1;
    for (FileType type : types) {
      version += type.getName().hashCode();
    }
    return version;
  }

  @Override
  public boolean acceptInput(VirtualFile file) {
    return !file.isDirectory();
  }

  @Override
  public void save(DataOutput out, FileType value) throws IOException {
    myEnumeratorStringDescriptor.save(out, value.getName());
  }

  @Override
  public FileType read(DataInput in) throws IOException {
    String read = myEnumeratorStringDescriptor.read(in);
    return myFileTypeManager.findFileTypeByName(read);
  }

  @Override
  public int getHashCode(FileType value) {
    return value.getName().hashCode();
  }

  @Override
  public boolean isEqual(FileType val1, FileType val2) {
    return Comparing.equal(val1, val2);
  }

  @NotNull
  @Override
  public Map<FileType, Void> map(FileContent inputData) {
    return Collections.singletonMap(inputData.getFileType(), null);
  }

  public static boolean containsFileOfType(@NotNull FileType type, @NotNull GlobalSearchScope scope) {
    return !FileBasedIndex.getInstance().processValues(NAME, type, null, new FileBasedIndex.ValueProcessor<Void>() {
      @Override
      public boolean process(VirtualFile file, Void value) {
        return false;
      }
    }, scope);
  }
}
