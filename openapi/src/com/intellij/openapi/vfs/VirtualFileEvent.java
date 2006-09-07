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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.EventObject;

/**
 * Provides data for a virtual file system change event.
 *
 * @see com.intellij.openapi.vfs.VirtualFileListener
 */
public class VirtualFileEvent extends EventObject {
  private final VirtualFile myFile;
  private final VirtualFile myParent;
  private final Object myRequestor;
  private final boolean myIsDirectory;
  private final String myFileName;

  private long myOldModificationStamp;
  private long myNewModificationStamp;

  public VirtualFileEvent(@Nullable Object requestor, @NotNull VirtualFile file, String fileName, boolean isDirectory, VirtualFile parent){
    super(file);
    myRequestor = requestor != null ? requestor : file.getUserData(VirtualFile.REQUESTOR_MARKER);
    myFile = file;
    myFileName = fileName;
    myIsDirectory = isDirectory;
    myParent = parent;
  }

  public VirtualFileEvent(@Nullable Object requestor, @NotNull VirtualFile file, VirtualFile parent, long oldModificationStamp, long newModificationStamp){
    super(file);
    myFile = file;
    myFileName = file.getName();
    myIsDirectory = false;
    myParent = parent;
    myRequestor = requestor != null ? requestor : file.getUserData(VirtualFile.REQUESTOR_MARKER);
    myOldModificationStamp = oldModificationStamp;
    myNewModificationStamp = newModificationStamp;
  }

  /**
   * Returns the file to which the change happened.
   *
   * @return the changed file.
   */
  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  /**
   * Returns the name of the changed file.
   *
   * @return the name of the changed file.
   */
  @NotNull
  public String getFileName() {
    return myFileName;
  }

  public boolean isDirectory(){
    return myIsDirectory;
  }

  /**
   * Returns the parent of the virtual file.
   *
   * @return the parent, or null if the file is a root directory or it was not possible to determine the parent
   * (depends on the specific VFS implementation).
   */
  @Nullable
  public VirtualFile getParent() {
    return myParent;
  }

  /**
   * Returns the object which performed the operation changing the VFS, or null if the change was
   * caused by an external process and detected during VFS refresh.
   *
   * @return the refresh initiating object, or null if it was not specified.
   */
  @Nullable
  public Object getRequestor() {
    return myRequestor;
  }

  /**
   * Returns the modification stamp of the file before the event.
   *
   * @return the modification stamp of the file before the event.
   * @see com.intellij.openapi.vfs.VirtualFile#getModificationStamp()
   */
  public long getOldModificationStamp(){
    return myOldModificationStamp;
  }

  /**
   * Returns the modification stamp of the file after the event.
   *
   * @return the modification stamp of the file after the event.
   * @see com.intellij.openapi.vfs.VirtualFile#getModificationStamp()
   */
  public long getNewModificationStamp() {
    return myNewModificationStamp;
  }

  public boolean isFromRefresh() {
    return myRequestor == null;
  }

  /**
   * Returns true if the VFS change described by the event is the save of a document.
   *
   * @return true if the VFS change described by the event is the save of a document, false otherwise.
   */
  public boolean isFromSave() {
    return myRequestor instanceof FileDocumentManager;
  }
}
