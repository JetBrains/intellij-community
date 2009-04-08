package com.intellij.compiler.impl.javaCompiler;

import com.intellij.util.ArrayUtil;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author cdr
 */
public class FileObject {
  private final File myFile;
  private final byte[] myContent;
  private final boolean mySaved;

  public FileObject(File file, byte[] content) {
    myFile = file;
    myContent = content;
    mySaved = false;
  }

  public FileObject(File file) {
    myFile = file;
    byte[] fileContent = ArrayUtil.EMPTY_BYTE_ARRAY;
    try{
      fileContent = FileUtil.loadFileBytes(file);
    }
    catch(IOException ignored){
    }
    myContent = fileContent;
    mySaved = true;
  }

  public File getFile() {
    return myFile;
  }

  public byte[] getContent() {
    return myContent;
  }

  public boolean isSaved() {
    return mySaved;
  }
}
