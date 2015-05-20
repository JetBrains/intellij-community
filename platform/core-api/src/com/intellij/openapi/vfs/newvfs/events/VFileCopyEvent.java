/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
public class VFileCopyEvent extends VFileEvent {
  private final VirtualFile myFile;
  private final VirtualFile myNewParent;
  private final String myNewChildName;

  public VFileCopyEvent(final Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent, @NotNull String newChildName) {
    super(requestor, false);
    myFile = file;
    myNewParent = newParent;
    myNewChildName = newChildName;
  }

  @Override
  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public VirtualFile getNewParent() {
    return myNewParent;
  }

  @NotNull
  public String getNewChildName() {
    return myNewChildName;
  }

  @Override
  @NonNls
  public String toString() {
    return "VfsEvent[copy " + myFile +" to " + myNewParent + " as " + myNewChildName +"]";
  }

  @NotNull
  @Override
  public String getPath() {
    return myNewParent.getPath() + "/" + myNewChildName;
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  @Override
  public boolean isValid() {
    return myFile.isValid() && myNewParent.findChild(myNewChildName) == null;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileCopyEvent event = (VFileCopyEvent)o;

    if (!myFile.equals(event.myFile)) return false;
    if (!myNewChildName.equals(event.myNewChildName)) return false;
    if (!myNewParent.equals(event.myNewParent)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + myNewParent.hashCode();
    result = 31 * result + myNewChildName.hashCode();
    return result;
  }
}
