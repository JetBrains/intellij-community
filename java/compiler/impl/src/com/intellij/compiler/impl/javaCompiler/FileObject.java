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
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author cdr
 */
public class FileObject {
  private static final byte[] NOT_LOADED = new byte[0];

  private final File myFile;
  private final byte[] myContent;

  public FileObject(@NotNull File file, @NotNull byte[] content) {
    myFile = file;
    myContent = content;
  }

  public FileObject(File file) {
    myFile = file;
    myContent = NOT_LOADED;
  }

  public File getFile() {
    return myFile;
  }

  public byte[] getContent() throws IOException {
    if (myContent == NOT_LOADED) {
      return FileUtil.loadFileBytes(myFile);
    }
    return myContent;
  }

  public void save() throws IOException {
    if (myContent == NOT_LOADED) {
      return; // already on disk
    }
    try {
      FileUtil.writeToFile(myFile, myContent);
    }
    catch (FileNotFoundException e) {
      FileUtil.createParentDirs(myFile);
      FileUtil.writeToFile(myFile, myContent);
    }
  }

  @Override
  public String toString() {
    return getFile().toString();
  }
}
