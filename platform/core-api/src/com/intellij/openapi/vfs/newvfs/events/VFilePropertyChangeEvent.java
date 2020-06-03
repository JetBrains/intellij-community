// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.FileContentUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Objects;

public final class VFilePropertyChangeEvent extends VFileEvent {
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
        if (!(oldValue instanceof String)) throw new IllegalArgumentException("oldName must be String, got "+oldValue);
        if (newValue == null) throw new IllegalArgumentException("newName must not be null");
        if (!(newValue instanceof String)) throw new IllegalArgumentException("newName must be String, got "+newValue);
        break;
      case VirtualFile.PROP_ENCODING:
        if (oldValue == null) throw new IllegalArgumentException("oldCharset must not be null");
        if (!(oldValue instanceof Charset)) throw new IllegalArgumentException("oldValue must be Charset, got "+oldValue);
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
        throw new IllegalArgumentException(
          "Unknown property name '" + propertyName + "'. " +
          "Must be one of VirtualFile.{PROP_NAME|PROP_ENCODING|PROP_WRITABLE|PROP_HIDDEN|PROP_SYMLINK_TARGET}");
    }
  }

  @ApiStatus.Experimental
  public boolean isRename() {
    return myPropertyName == VirtualFile.PROP_NAME && getRequestor() != FileContentUtilCore.FORCE_RELOAD_REQUESTOR;
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
  public String getPath() {
    return computePath();
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
    if (!Objects.equals(myNewValue, event.myNewValue)) return false;
    if (!Objects.equals(myOldValue, event.myOldValue)) return false;
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
  @Override
  public String toString() {
    return "VfsEvent[property(" + myPropertyName + ") changed for '" + myFile + "': " + myOldValue + " -> " + myNewValue + ']';
  }

  @NotNull
  public String getOldPath() {
    return getPathWithFileName(myOldValue);
  }

  @NotNull
  public String getNewPath() {
    return getPathWithFileName(myNewValue);
  }

  /** Replaces file name in {@code myFile} path with {@code fileName}, if an event is a rename event; leaves path as is otherwise */
  @NotNull
  private String getPathWithFileName(Object fileName) {
    if (VirtualFile.PROP_NAME.equals(myPropertyName)) {
      // fileName must be String, according to `checkPropertyValuesCorrect` implementation
      VirtualFile parent = myFile.getParent();
      if (parent == null) {
        return ((String)fileName);
      }
      return parent.getPath() + "/" + fileName;
    }
    return getPath();
  }
}