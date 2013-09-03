/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

  public VFilePropertyChangeEvent(Object requestor,
                                  @NotNull VirtualFile file,
                                  @NotNull String propertyName,
                                  @Nullable Object oldValue,
                                  @Nullable Object newValue,
                                  boolean isFromRefresh) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VFilePropertyChangeEvent event = (VFilePropertyChangeEvent)o;

    if (!myFile.equals(event.myFile)) return false;
    if (myNewValue != null ? !myNewValue.equals(event.myNewValue) : event.myNewValue != null) return false;
    if (myOldValue != null ? !myOldValue.equals(event.myOldValue) : event.myOldValue != null) return false;
    if (!myPropertyName.equals(event.myPropertyName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + myPropertyName.hashCode();
    result = 31 * result + (myOldValue != null ? myOldValue.hashCode() : 0);
    result = 31 * result + (myNewValue != null ? myNewValue.hashCode() : 0);
    return result;
  }

  @NotNull
  @NonNls
  public String toString() {
    return "VfsEvent[property(" + myPropertyName + ") changed for '" + myFile + "':" +
           " oldValue = " + myOldValue + ", newValue = " + myNewValue + "]";
  }
}
