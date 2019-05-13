// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class VFileCreateEvent extends VFileEvent {
  @NotNull private final VirtualFile myParent;
  private final boolean myDirectory;
  private final boolean myEmptyDirectory;
  @NotNull private final String myChildName;
  private final FileAttributes myAttributes;
  private VirtualFile myCreatedFile;

  public VFileCreateEvent(Object requestor,
                          @NotNull VirtualFile parent,
                          @NotNull String childName,
                          boolean isDirectory,
                          @Nullable("null means read from the created file") FileAttributes attributes,
                          boolean isFromRefresh,
                          boolean isEmptyDirectory) {
    super(requestor, isFromRefresh);
    myChildName = childName;
    myParent = parent;
    myDirectory = isDirectory;
    myAttributes = attributes;
    myEmptyDirectory = isEmptyDirectory;
  }

  @NotNull
  public String getChildName() {
    return myChildName;
  }

  public boolean isDirectory() {
    return myDirectory;
  }

  /**
   * @return true if the newly created file is a directory which has no children.
   */
  public boolean isEmptyDirectory() {
    return isDirectory() && myEmptyDirectory;
  }

  @NotNull
  public VirtualFile getParent() {
    return myParent;
  }

  @Nullable
  public FileAttributes getAttributes() {
    return myAttributes;
  }

  @NonNls
  @Override
  public String toString() {
    return "VfsEvent[create " + (myDirectory ? "dir " : "file ") + myChildName +  " in " + myParent.getUrl() + "]";
  }

  @NotNull
  @Override
  protected String computePath() {
    String parentPath = myParent.getPath();
    // jar file returns "x.jar!/"
    return StringUtil.endsWithChar(parentPath, '/') ?  parentPath + myChildName : parentPath + "/" + myChildName;
  }

  @Override
  public VirtualFile getFile() {
    VirtualFile createdFile = myCreatedFile;
    if (createdFile == null) {
      myCreatedFile = createdFile = myParent.findChild(myChildName);
    }
    return createdFile;
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