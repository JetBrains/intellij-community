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
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class VFileContentChangeEvent extends VFileEvent {
  private final VirtualFile myFile;
  private final long myOldModificationStamp;
  private final long myNewModificationStamp;

  public VFileContentChangeEvent(final Object requestor,
                                 @NotNull final VirtualFile file,
                                 final long oldModificationStamp,
                                 final long newModificationStamp,
                                 final boolean isFromRefresh) {
    super(requestor, isFromRefresh);
    myFile = file;
    myOldModificationStamp = oldModificationStamp;
    myNewModificationStamp = newModificationStamp == -1 ? LocalTimeCounter.currentTime() : newModificationStamp;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  public long getModificationStamp() {
    return myNewModificationStamp;
  }

  public long getOldModificationStamp() {
    return myOldModificationStamp;
  }

  @NonNls
  public String toString() {
    return "VfsEvent[update: " + myFile.getUrl() + "]";
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
    return myFile.isValid() && myFile.getModificationStamp() == myOldModificationStamp;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileContentChangeEvent event = (VFileContentChangeEvent)o;

    if (myNewModificationStamp != event.myNewModificationStamp) return false;
    if (myOldModificationStamp != event.myOldModificationStamp) return false;
    if (!myFile.equals(event.myFile)) return false;

    return true;
  }

  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + (int)(myOldModificationStamp ^ (myOldModificationStamp >>> 32));
    result = 31 * result + (int)(myNewModificationStamp ^ (myNewModificationStamp >>> 32));
    return result;
  }
}
