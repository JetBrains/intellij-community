/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
