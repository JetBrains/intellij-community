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

package com.intellij.history.integration;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

public class FileFilter {
  public static final long MAX_FILE_SIZE = 100 * 1024;

  private final FileIndex myFileIndex;
  private final FileTypeManager myTypeManager;

  public FileFilter(FileIndex fi, FileTypeManager tm) {
    myFileIndex = fi;
    myTypeManager = tm;
  }

  public boolean isAllowedAndUnderContentRoot(VirtualFile f) {
    return isAllowed(f) && isUnderContentRoot(f);
  }

  public boolean isUnderContentRoot(VirtualFile f) {
    if (!(f.getFileSystem() instanceof LocalFileSystem)) return false;
    return myFileIndex.isInContent(f);
  }

  public boolean isAllowed(VirtualFile f) {
    if (myTypeManager.isFileIgnored(f.getName())) return false;
    if (f.isDirectory()) return true;
    return !myTypeManager.getFileTypeByFile(f).isBinary();
  }
}
