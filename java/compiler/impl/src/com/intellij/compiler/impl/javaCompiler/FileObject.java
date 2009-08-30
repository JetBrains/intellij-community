package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author cdr
 */
public class FileObject {
  private final static byte[] NOT_LOADED = new byte[0];

  private final File myFile;
  private byte[] myContent;
  private final boolean mySaved;

  public FileObject(File file, byte[] content) {
    myFile = file;
    myContent = content;
    mySaved = false;
  }

  public FileObject(File file) {
    myFile = file;
    myContent = NOT_LOADED;
    mySaved = true;
  }

  public File getFile() {
    return myFile;
  }

  public byte[] getContent() {
    if (myContent == NOT_LOADED) {
      try{
        return FileUtil.loadFileBytes(myFile);
      }
      catch(IOException ignored){
      }
    }
    return myContent;
  }

  public boolean isSaved() {
    return mySaved;
  }
}
