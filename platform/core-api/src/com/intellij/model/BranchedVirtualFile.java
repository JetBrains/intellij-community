// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

abstract class BranchedVirtualFile extends LightVirtualFile {
  @NotNull final VirtualFile original;
  @NotNull final ModelBranch branch;

  BranchedVirtualFile(@NotNull VirtualFile original, @NotNull ModelBranch branch) {
    super(original.getName(), original.getFileType(), "", original.getModificationStamp());
    this.original = original;
    this.branch = branch;
  }

  @Override
  public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException("Branch files shouldn't be modified");
  }

  @Override
  public void setContent(Object requestor, @NotNull CharSequence content, boolean fireEvent) {
    throw new UnsupportedOperationException("Branch files shouldn't be modified");
  }

  @Override
  public boolean isDirectory() {
    return original.isDirectory();
  }

  @Override
  public VirtualFile getParent() {
    VirtualFile parent = original.getParent();
    return parent == null ? null : branch.findFileCopy(parent);
  }

  @Override
  public VirtualFile[] getChildren() {
    VirtualFile[] children = original.getChildren();
    return children == null ? null : ContainerUtil.map2Array(children, VirtualFile.class, f -> branch.findFileCopy(f));
  }

}