// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class VFileCreateEvent extends VFileEvent {
  @NotNull
  private final VirtualFile myParent;
  @NotNull
  private final String myChildName;
  private final boolean myDirectory;
  private final FileAttributes myAttributes;
  private final String mySymlinkTarget;
  private final ChildInfo[] myChildren;
  private VirtualFile myCreatedFile;

  public VFileCreateEvent(Object requestor,
                          @NotNull VirtualFile parent,
                          @NotNull String childName,
                          boolean isDirectory,
                          @Nullable("null means should read from the created file") FileAttributes attributes,
                          @Nullable String symlinkTarget,
                          boolean isFromRefresh,
                          @Nullable // null means children are unknown or not applicable
                          ChildInfo[] children) {
    super(requestor, isFromRefresh);
    myParent = parent;
    myChildName = childName;
    myDirectory = isDirectory;
    myAttributes = attributes;
    mySymlinkTarget = symlinkTarget;
    myChildren = children;
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

  @Nullable
  public FileAttributes getAttributes() {
    return myAttributes;
  }

  @Nullable
  public String getSymlinkTarget() {
    return mySymlinkTarget;
  }

  /** @return true if the newly created file is a directory which has no children. */
  public boolean isEmptyDirectory() {
    return isDirectory() && myChildren != null && myChildren.length == 0;
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
    return myParent.isValid() && myParent.findChild(myChildName) == null;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileCreateEvent event = (VFileCreateEvent)o;

    return myDirectory == event.myDirectory && myChildName.equals(event.myChildName) && myParent.equals(event.myParent);
  }

  @Override
  public int hashCode() {
    int result = myParent.hashCode();
    result = 31 * result + (myDirectory ? 1 : 0);
    result = 31 * result + myChildName.hashCode();
    return result;
  }

  @Override
  public String toString() {
    String kind = myDirectory ? (isEmptyDirectory() ? "(empty) " : "") + "dir " : "file ";
    return "VfsEvent[create " + kind + myParent.getUrl() + "/"+ myChildName +"]"
           + (myChildren == null ? "" : " with children:\n"+StringUtil.join(myChildren, Object::toString, "\n"));
  }

  @Nullable // null means children not available (e.g. the created file is not a directory) or unknown
  public ChildInfo[] getChildren() {
    return myChildren;
  }
}