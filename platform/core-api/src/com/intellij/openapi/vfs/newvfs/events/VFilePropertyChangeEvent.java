/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VFilePropertyChangeEvent extends VFileEvent {
  private final VirtualFile myFile;
  private final String myPropertyName;
  private final Object myOldValue;
  private final Object myNewValue;

  public VFilePropertyChangeEvent(final Object requestor,
                                  @NotNull final VirtualFile file,
                                  @NotNull final String propertyName,
                                  final Object oldValue,
                                  final Object newValue,
                                  final boolean isFromRefresh) {
    super(requestor, isFromRefresh);
    myFile = file;
    myPropertyName = propertyName;
    myOldValue = oldValue;
    myNewValue = newValue;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  public Object getNewValue() {
    return myNewValue;
  }

  public Object getOldValue() {
    return myOldValue;
  }

  @NotNull
  public String getPropertyName() {
    return myPropertyName;
  }

  @NotNull
  @NonNls
  public String toString() {
    return "VfsEvent[property( " + myPropertyName + ") changed for '" + myFile + "': oldValue = " + myOldValue + ", newValue = " + myNewValue + "]";
  }

  @Override
  public String getPath() {
    return myFile.getPath();
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  public boolean equals(@Nullable final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFilePropertyChangeEvent event = (VFilePropertyChangeEvent)o;

    if (!myFile.equals(event.myFile)) return false;
    if (!myNewValue.equals(event.myNewValue)) return false;
    if (!myOldValue.equals(event.myOldValue)) return false;
    if (!myPropertyName.equals(event.myPropertyName)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myFile.hashCode();
    result = 31 * result + myPropertyName.hashCode();
    result = 31 * result + myOldValue.hashCode();
    result = 31 * result + myNewValue.hashCode();
    return result;
  }
}
