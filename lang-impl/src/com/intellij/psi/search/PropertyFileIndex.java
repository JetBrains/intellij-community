package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * @author peter
 */
public class PropertyFileIndex extends ScalarIndexExtension<FileType> {
  public static boolean DEBUG = false;

  @NonNls public static final ID<FileType, Void> NAME = ID.create("PropertyFileIndex");
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();
  private final MyInputFilter myInputFilter = new MyInputFilter();
  private final KeyDescriptor<FileType> myKeyDescriptor = new KeyDescriptor<FileType>() {
    public int getHashCode(FileType value) {
      return value.getName().hashCode();
    }

    public boolean isEqual(FileType val1, FileType val2) {
      return val1.equals(val2);
    }

    public void save(DataOutput out, FileType value) throws IOException {
      out.writeUTF(value.getName());
    }

    public FileType read(DataInput in) throws IOException {
      final String s = in.readUTF();
      for (FileType type : FileTypeManager.getInstance().getRegisteredFileTypes()) {
        if (type.getName().equals(s)) {
          return type;
        }
      }
      throw new AssertionError(s);
    }
  };

  public ID<FileType,Void> getName() {
    return NAME;
  }

  public DataIndexer<FileType, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  public KeyDescriptor<FileType> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public boolean dependsOnFileContent() {
    return false;
  }

  public int getVersion() {
    return 0;
  }

  private static class MyDataIndexer implements DataIndexer<FileType, Void, FileContent> {
    @NotNull
    public Map<FileType, Void> map(final FileContent inputData) {
      final FileType fileType = inputData.getFileType();
      if (DEBUG) {
        System.out.println("FileTypeIndex$MyDataIndexer.map");
        System.out.println("inputData.getFile() = " + inputData.getFile().getPath());
        System.out.println("fileType = " + fileType);
      }

      return Collections.singletonMap(fileType, null);
    }
  }

  private static class MyInputFilter implements FileBasedIndex.InputFilter {
    public boolean acceptInput(final VirtualFile file) {
      return !(StdFileTypes.PROPERTIES instanceof PlainTextFileType) &&  file.getFileType() == StdFileTypes.PROPERTIES;
    }
  }
}