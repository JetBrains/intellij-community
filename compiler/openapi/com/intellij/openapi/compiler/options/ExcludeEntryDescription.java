/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

/**
 * created at Jan 3, 2002
 * @author Jeka
 */
package com.intellij.openapi.compiler.options;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;

public class ExcludeEntryDescription {
  private boolean myIsFile = true;
  private boolean myIncludeSubdirectories;
  private VirtualFilePointer myFilePointer;

  public ExcludeEntryDescription(VirtualFile virtualFile, boolean includeSubdirectories, boolean isFile) {
    myFilePointer = VirtualFilePointerManager.getInstance().create(virtualFile, null);
    myIncludeSubdirectories = includeSubdirectories;
    myIsFile = isFile;
  }

  public ExcludeEntryDescription(String url, boolean includeSubdirectories, boolean isFile) {
    myFilePointer = VirtualFilePointerManager.getInstance().create(url, null);
    myIncludeSubdirectories = includeSubdirectories;
    myIsFile = isFile;
  }

  public ExcludeEntryDescription copy() {
    return new ExcludeEntryDescription(getUrl(), myIncludeSubdirectories, myIsFile);
  }

  public boolean isFile() {
    return myIsFile;
  }

  public String getUrl() {
    return myFilePointer.getUrl();
  }

  public String getPresentableUrl() {
    return myFilePointer.getPresentableUrl();
  }

  public boolean isIncludeSubdirectories() {
    return myIncludeSubdirectories;
  }

  public void setIncludeSubdirectories(boolean includeSubdirectories) {
    myIncludeSubdirectories = includeSubdirectories;
  }

  public VirtualFile getVirtualFile() {
    return myFilePointer.getFile();
  }

  public boolean isValid() {
    return myFilePointer.isValid();
  }

  public boolean equals(Object obj) {
    if(!(obj instanceof ExcludeEntryDescription)) {
      return false;
    }
    ExcludeEntryDescription entryDescription = (ExcludeEntryDescription)obj;
    if(entryDescription.myIsFile != myIsFile) {
      return false;
    }
    if(entryDescription.myIncludeSubdirectories != myIncludeSubdirectories) {
      return false;
    }
    if(!Comparing.equal(entryDescription.getUrl(), getUrl())) {
      return false;
    }
    return true;
  }

  public int hashCode() {
    return getUrl().hashCode();
  }
}
