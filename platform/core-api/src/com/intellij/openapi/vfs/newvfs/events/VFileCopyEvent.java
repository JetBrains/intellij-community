// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VFileCopyEvent extends VFileEvent {
  private final VirtualFile myFile;
  private final VirtualFile myNewParent;
  private final String myNewChildName;
  private final FileAttributes myAttributes;
  private final String mySymlinkTarget;
  private final ChildInfo[] myChildren;
  private final boolean myAllChildren;

  @ApiStatus.Internal
  public VFileCopyEvent(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent, @NotNull String newChildName) {
    this(requestor, file, newParent, newChildName, null, null, null, false);
  }

  @ApiStatus.Internal
  public VFileCopyEvent(
    Object requestor,
    @NotNull VirtualFile file,
    @NotNull VirtualFile newParent,
    @NotNull String newChildName,
    @Nullable("null means should read from the created file") FileAttributes attributes,
    @Nullable String symlinkTarget,
    ChildInfo @Nullable("null means children are unknown") [] children,
    boolean allChildren
  ) {
    super(requestor);
    myFile = file;
    myNewParent = newParent;
    myNewChildName = newChildName;
    myAttributes = attributes;
    mySymlinkTarget = symlinkTarget;
    myChildren = children;
    myAllChildren = children != null && allChildren;
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  public @NotNull VirtualFile getNewParent() {
    return myNewParent;
  }

  public @NotNull String getNewChildName() {
    return myNewChildName;
  }

  public @Nullable FileAttributes getAttributes() {
    return myAttributes;
  }

  public @Nullable String getSymlinkTarget() {
    return mySymlinkTarget;
  }

  /**
   * Children of the copied file if it's a directory.
   * <br/>
   * <code>null</code> is returned if the file is not a directory or the children are not known.
   * If {@link #isAllChildren()} returns {@code false}, the returned array contains only some children.
   *
   * @return children of the copied file if it's a directory
   */
  @ApiStatus.Internal
  public ChildInfo @Nullable [] getChildren() {
    return myChildren;
  }

  @ApiStatus.Internal
  public boolean isAllChildren() {
    return myAllChildren;
  }

  /** @return {@code true} if the copied file is a directory that has no children. */
  public boolean isEmptyDirectory() {
    return myFile.isDirectory() && myAllChildren && myChildren != null && myChildren.length == 0;
  }

  public @Nullable VirtualFile findCreatedFile() {
    return myNewParent.isValid() ? myNewParent.findChild(myNewChildName) : null;
  }

  @Override
  public String toString() {
    return "VfsEvent[copy " + myFile +" to " + myNewParent + " as " + myNewChildName +"]"
           + (myChildren == null ? "" : " with "+myChildren.length+" children");
  }

  @Override
  protected @NotNull String computePath() {
    return myNewParent.getPath() + "/" + myNewChildName;
  }

  @Override
  public @NotNull VirtualFileSystem getFileSystem() {
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
