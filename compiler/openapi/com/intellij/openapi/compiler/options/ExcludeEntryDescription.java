/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jetbrains.annotations.Nullable;

public class ExcludeEntryDescription implements Disposable {

  private boolean myIsFile;
  private boolean myIncludeSubdirectories;
  private String myUrl;
  private VirtualFilePointer myFilePointer;

  public ExcludeEntryDescription() {
  }

  public ExcludeEntryDescription(String url, boolean includeSubdirectories, boolean isFile, Disposable parent) {
    myFilePointer = VirtualFilePointerManager.getInstance().create(url, parent, null);
    myIncludeSubdirectories = includeSubdirectories;
    myIsFile = isFile;
  }

  public ExcludeEntryDescription(VirtualFile virtualFile, boolean includeSubdirectories, boolean isFile, Disposable parent) {
    this(virtualFile.getUrl(), includeSubdirectories, isFile, parent);
  }

  public void init(final Disposable parent) {
    if (myFilePointer == null) {
      myFilePointer = VirtualFilePointerManager.getInstance().create(myUrl, parent, null);
    }
  }

  public void setFile(final boolean isFile) {
    myIsFile = isFile;
  }

  public ExcludeEntryDescription copy(Disposable parent) {
    return new ExcludeEntryDescription(getUrl(), myIncludeSubdirectories, myIsFile,parent);
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

  @Nullable
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
    return Comparing.equal(entryDescription.getUrl(), getUrl());
  }

  public int hashCode() {
    int result = (myIsFile ? 1 : 0);
    result = 31 * result + (myIncludeSubdirectories ? 1 : 0);
    result = 31 * result + getUrl().hashCode();
    return result;
  }

  public void dispose() {
  }

  public void setUrl(final String url) {
    myUrl = url;
  }
}
