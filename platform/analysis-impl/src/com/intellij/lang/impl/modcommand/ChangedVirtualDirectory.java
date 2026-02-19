// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

final class ChangedVirtualDirectory extends LightVirtualFile {
  private final @NotNull Map<@NotNull String, @NotNull LightVirtualFile> myAddedChildren = new LinkedHashMap<>();
  private final VirtualFile myParent;

  ChangedVirtualDirectory(@NotNull VirtualFile original) {
    super(original, "", original.getModificationStamp());
    if (!original.isDirectory()) {
      throw new IllegalArgumentException();
    }
    myParent = original.getParent();
    super.setOriginalFile(original);
  }
  
  ChangedVirtualDirectory(@NotNull ChangedVirtualDirectory parent, @NotNull String name) {
    super(name);
    myParent = parent;
  }

  @Override
  public void setOriginalFile(VirtualFile originalFile) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public VirtualFile getParent() {
    return myParent;
  }

  @Override
  public boolean shouldSkipEventSystem() {
    return true;
  }

  @Override
  public @Nullable VirtualFile findChild(@NotNull String name) {
    VirtualFile child = myAddedChildren.get(name);
    if (child != null) return child;
    VirtualFile originalFile = getOriginalFile();
    return originalFile != null ? originalFile.findChild(name) : null;
  }

  @Override
  public VirtualFile[] getChildren() {
    VirtualFile originalFile = getOriginalFile();
    if (originalFile == null) return myAddedChildren.values().toArray(EMPTY_ARRAY);
    return Stream.concat(Stream.of(originalFile), myAddedChildren.values().stream())
      .toArray(VirtualFile[]::new);
  }

  @Override
  public @NotNull VirtualFile createChildData(Object requestor, @NotNull String name) {
    if (findChild(name) != null) {
      throw new IllegalArgumentException("Child exists: " + name);
    }
    LightVirtualFile file = new AddedVirtualFile(name);
    myAddedChildren.put(name, file);
    return file;
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull String name) {
    if (findChild(name) != null) {
      throw new IllegalArgumentException("Child exists: " + name);
    }
    LightVirtualFile file = new ChangedVirtualDirectory(this, name);
    myAddedChildren.put(name, file);
    return file;
  }

  @NotNull Map<@NotNull String, @NotNull LightVirtualFile> getAddedChildren() {
    return myAddedChildren;
  }

  class AddedVirtualFile extends LightVirtualFile {
    private AddedVirtualFile(@NotNull String name) { super(name); }

    @Override
    public void delete(Object requestor) {
      ChangedVirtualDirectory.this.myAddedChildren.remove(getName());
    }

    @Override
    public boolean shouldSkipEventSystem() {
      return true;
    }

    @Override
    public VirtualFile getParent() {
      return ChangedVirtualDirectory.this;
    }
  }
}
