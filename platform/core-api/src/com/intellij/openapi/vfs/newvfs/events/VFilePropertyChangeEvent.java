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
import com.intellij.util.FileContentUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class VFilePropertyChangeEvent extends VFileEvent {
  private final VirtualFile myFile;
  private final String myPropertyName;
  private final Object myOldValue;
  private final Object myNewValue;

  public VFilePropertyChangeEvent(Object requestor,
                                  @NotNull VirtualFile file,
                                  @VirtualFile.PropName @NotNull String propertyName,
                                  @Nullable Object oldValue,
                                  @Nullable Object newValue,
                                  boolean isFromRefresh) {
    super(requestor, isFromRefresh);
    myFile = file;
    myPropertyName = propertyName;
    myOldValue = oldValue;
    myNewValue = newValue;
    checkPropertyValuesCorrect(requestor, propertyName, oldValue, newValue);
  }

  public static void checkPropertyValuesCorrect(Object requestor, @VirtualFile.PropName @NotNull String propertyName, Object oldValue, Object newValue) {
    if (Comparing.equal(oldValue, newValue) && FileContentUtilCore.FORCE_RELOAD_REQUESTOR != requestor) {
      throw new IllegalArgumentException("Values must be different, got the same: " + oldValue);
    }
    switch (propertyName) {
      case VirtualFile.PROP_NAME:
        if (oldValue == null) throw new IllegalArgumentException("oldName must not be null");
        if (newValue == null) throw new IllegalArgumentException("newName must not be null");
        break;
      case VirtualFile.PROP_ENCODING:
        if (oldValue == null) throw new IllegalArgumentException("oldCharset must not be null");
        break;
      case VirtualFile.PROP_WRITABLE:
        if (!(oldValue instanceof Boolean)) throw new IllegalArgumentException("oldWriteable must be boolean, got " + oldValue);
        if (!(newValue instanceof Boolean)) throw new IllegalArgumentException("newWriteable must be boolean, got " + newValue);
        break;
      case VirtualFile.PROP_HIDDEN:
        if (!(oldValue instanceof Boolean)) throw new IllegalArgumentException("oldHidden must be boolean, got " + oldValue);
        if (!(newValue instanceof Boolean)) throw new IllegalArgumentException("newHidden must be boolean, got " + newValue);
        break;
      case VirtualFile.PROP_SYMLINK_TARGET:
        if (oldValue != null && !(oldValue instanceof String)) {
          throw new IllegalArgumentException("oldSymTarget must be String, got " + oldValue);
        }
        if (newValue != null && !(newValue instanceof String)) {
          throw new IllegalArgumentException("newSymTarget must be String, got " + newValue);
        }
        break;
      default:
        throw new IllegalArgumentException("Unknown property name: '"+propertyName+"'. Must be one of: VirtualFile.PROP_NAME, VirtualFile.PROP_ENCODING, VirtualFile.PROP_WRITABLE, VirtualFile.PROP_HIDDEN, VirtualFile.PROP_SYMLINK_TARGET");
    }
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
  @VirtualFile.PropName
  public String getPropertyName() {
    return myPropertyName;
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

  @Override
  @NotNull
  @NonNls
  public String toString() {
    return "VfsEvent[property(" + myPropertyName + ") changed for '" + myFile + "':" +
           " oldValue = " + myOldValue + ", newValue = " + myNewValue + "]";
  }

  @NotNull
  public String getOldPath() {
    String path = getPath();
    if (VirtualFile.PROP_NAME.equals(myPropertyName) && myNewValue instanceof String && myOldValue instanceof String) {
      String newName = (String)myNewValue;
      int i = path.lastIndexOf(newName);
      if (i != -1) path = new StringBuilder(path).replace(i, i + newName.length(), (String)myOldValue).toString();
    }
    return path;
  }
}
