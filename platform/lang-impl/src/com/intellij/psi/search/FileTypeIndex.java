package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
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


  public static Collection<VirtualFile> getFiles(FileType fileType, GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, fileType, scope);
  }

  public static final ID<FileType, Void> NAME = ID.create("filetypes");

  private final FileTypeManager myFileTypeManager;

  public FileTypeIndex(FileTypeManager fileTypeManager) {
    myFileTypeManager = fileTypeManager;
  }

  @Override
  public ID<FileType, Void> getName() {
    return NAME;
  }

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
    return 0;
  }

  @Override
  public boolean acceptInput(VirtualFile file) {
    return !file.isDirectory();
  }

  @Override
  public void save(DataOutput out, FileType value) throws IOException {
    out.writeUTF(value.getName());
  }

  @Override
  public FileType read(DataInput in) throws IOException {
    String name = in.readUTF();
    return myFileTypeManager.getStdFileType(name);
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
}
