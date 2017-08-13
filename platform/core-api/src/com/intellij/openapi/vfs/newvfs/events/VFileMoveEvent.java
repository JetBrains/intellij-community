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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class VFileMoveEvent extends VFileEvent {
  private final VirtualFile myFile;
  private final VirtualFile myOldParent;
  private final VirtualFile myNewParent;

  public VFileMoveEvent(final Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) {
    super(requestor, false);
    myFile = file;
    myNewParent = newParent;
    myOldParent = file.getParent();
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public VirtualFile getNewParent() {
    return myNewParent;
  }

  public VirtualFile getOldParent() {
    return myOldParent;
  }

  @Override
  @NonNls
  public String toString() {
    return "VfsEvent[move " + myFile.getName() +" from " + myOldParent + " to " + myNewParent + "]";
  }

  @NotNull
  @Override
  protected String computePath() {
    return myFile.getPath();
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  @Override
  public boolean isValid() {
    return myFile.isValid() && Comparing.equal(myFile.getParent(), myOldParent) && myOldParent.isValid();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileMoveEvent event = (VFileMoveEvent)o;

    if (!myFile.equals(event.myFile)) return false;
    if (!myNewParent.equals(event.myNewParent)) return false;
    if (!myOldParent.equals(event.myOldParent)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + myOldParent.hashCode();
    result = 31 * result + myNewParent.hashCode();
    return result;
  }

  public String getOldPath() {
    return myOldParent.getPath() + "/" + myFile.getName();
  }
}
