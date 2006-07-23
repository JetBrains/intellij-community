/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.openapi.fileEditor.FileDocumentManager;

import java.util.EventObject;

public class VirtualFileEvent extends EventObject {
  private final VirtualFile myFile;
  private final VirtualFile myParent;
  private final Object myRequestor;
  private final boolean myIsDirectory;
  private final String myFileName;

  private long myOldModificationStamp;
  private long myNewModificationStamp;

  public VirtualFileEvent(Object requestor, VirtualFile file, String fileName, boolean isDirectory, VirtualFile parent){
    super(file);
    myRequestor = requestor != null ? requestor : file.getUserData(VirtualFile.REQUESTOR_MARKER);
    myFile = file;
    myFileName = fileName;
    myIsDirectory = isDirectory;
    myParent = parent;
  }

  public VirtualFileEvent(Object requestor, VirtualFile file, VirtualFile parent, long oldModificationStamp, long newModificationStamp){
    super(file);
    myFile = file;
    myFileName = file.getName();
    myIsDirectory = false;
    myParent = parent;
    myRequestor = requestor != null ? requestor : file.getUserData(VirtualFile.REQUESTOR_MARKER);
    myOldModificationStamp = oldModificationStamp;
    myNewModificationStamp = newModificationStamp;
  }

  public VirtualFile getFile(){
    return myFile;
  }

  public String getFileName() {
    return myFileName;
  }

  public boolean isDirectory(){
    return myIsDirectory;
  }

  public VirtualFile getParent(){
    return myParent;
  }

  public Object getRequestor(){
    return myRequestor;
  }

  public long getOldModificationStamp(){
    return myOldModificationStamp;
  }

  public long getNewModificationStamp(){
    return myNewModificationStamp;
  }

  public boolean isFromRefresh() {
    return myRequestor == null;
  }

  public boolean isFromSave() {
    return myRequestor instanceof FileDocumentManager;
  }
}
