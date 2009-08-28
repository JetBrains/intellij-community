package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
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
public class PropertyFileIndex extends ScalarIndexExtension<String> {
  public static boolean DEBUG = false;

  @NonNls public static final ID<String, Void> NAME = ID.create("PropertyFileIndex");
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();
  private final MyInputFilter myInputFilter = new MyInputFilter();
  private final KeyDescriptor<String> myKeyDescriptor = new KeyDescriptor<String>() {
    public int getHashCode(String value) {
      return value.hashCode();
    }

    public boolean isEqual(String val1, String val2) {
      return val1.equals(val2);
    }

    public void save(DataOutput out, String value) throws IOException {
      out.writeUTF(value);
    }

    public String read(DataInput in) throws IOException {
      return in.readUTF();
    }
  };

  public ID<String,Void> getName() {
    return NAME;
  }

  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  public KeyDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public boolean dependsOnFileContent() {
    return false;
  }

  public int getVersion() {
    return 1;
  }

  private static class MyDataIndexer implements DataIndexer<String, Void, FileContent> {
    @NotNull
    public Map<String, Void> map(final FileContent inputData) {
      final FileType fileType = inputData.getFileType();
      if (DEBUG) {
        System.out.println("FileTypeIndex$MyDataIndexer.map");
        System.out.println("inputData.getFile() = " + inputData.getFile().getPath());
        System.out.println("fileType = " + fileType);
      }

      return Collections.singletonMap(fileType.getName(), null);
    }
  }

  private static class MyInputFilter implements FileBasedIndex.InputFilter {
    public boolean acceptInput(final VirtualFile file) {
      return !(StdFileTypes.PROPERTIES instanceof PlainTextFileType) &&  file.getFileType() == StdFileTypes.PROPERTIES;
    }
  }
}