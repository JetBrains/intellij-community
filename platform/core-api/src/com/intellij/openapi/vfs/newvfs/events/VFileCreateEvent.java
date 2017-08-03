/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class VFileCreateEvent extends VFileEvent {
  @NotNull private final VirtualFile myParent;
  private final boolean myDirectory;
  @NotNull private final String myChildName;
  private VirtualFile myCreatedFile;

  public VFileCreateEvent(Object requestor,
                          @NotNull VirtualFile parent,
                          @NotNull String childName,
                          final boolean isDirectory,
                          final boolean isFromRefresh) {
    super(requestor, isFromRefresh);
    myChildName = childName;
    myParent = parent;
    myDirectory = isDirectory;
  }

  @NotNull
  public String getChildName() {
    return myChildName;
  }

  public boolean isDirectory() {
    return myDirectory;
  }

  @NotNull
  public VirtualFile getParent() {
    return myParent;
  }

  @NonNls
  @Override
  public String toString() {
    return "VfsEvent[create " + (myDirectory ? "dir " : "file ") +
           myChildName +  " in " + myParent.getUrl() + "]";
  }

  @NotNull
  @Override
  public String getPath() {
    return myParent.getPath() + "/" + myChildName;
  }

  @Override
  public VirtualFile getFile() {
    if (myCreatedFile != null) return myCreatedFile;
    return myCreatedFile = myParent.findChild(myChildName);
  }

  public void resetCache() {
    myCreatedFile = null;
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return myParent.getFileSystem();
  }

  @Override
  public boolean isValid() {
    if (myParent.isValid()) {
      boolean childExists = myParent.findChild(myChildName) != null;
      return !childExists;
    }

    return false;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileCreateEvent event = (VFileCreateEvent)o;

    if (myDirectory != event.myDirectory) return false;
    if (!myChildName.equals(event.myChildName)) return false;
    if (!myParent.equals(event.myParent)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = myParent.hashCode();
    result = 31 * result + (myDirectory ? 1 : 0);
    result = 31 * result + myChildName.hashCode();
    return result;
  }
}
