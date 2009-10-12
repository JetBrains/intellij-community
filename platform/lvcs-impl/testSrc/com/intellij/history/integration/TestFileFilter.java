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

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;

public class TestFileFilter extends FileFilter {
  private boolean myAreAllFilesAllowed = true;
  private VirtualFile[] myFilesNotUnderContentRoot = new VirtualFile[0];
  private VirtualFile[] myUnallowedFiles = new VirtualFile[0];

  public TestFileFilter() {
    super(null, null);
  }

  @Override
  public boolean isUnderContentRoot(VirtualFile f) {
    return !contains(myFilesNotUnderContentRoot, f);
  }

  public void setFilesNotUnderContentRoot(VirtualFile... f) {
    myFilesNotUnderContentRoot = f;
  }

  @Override
  public boolean isAllowed(VirtualFile f) {
    if (!myAreAllFilesAllowed) return false;
    return !contains(myUnallowedFiles, f);
  }

  public void dontAllowAnyFile() {
    myAreAllFilesAllowed = false;
  }

  public void setNotAllowedFiles(VirtualFile... f) {
    myUnallowedFiles = f;
  }

  private boolean contains(VirtualFile[] files, VirtualFile f) {
    return Arrays.asList(files).contains(f);
  }
}
