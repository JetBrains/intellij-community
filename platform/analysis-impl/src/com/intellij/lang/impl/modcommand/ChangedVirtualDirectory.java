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

  ChangedVirtualDirectory(@NotNull VirtualFile original) {
    super(original, "", original.getModificationStamp());
    if (!original.isDirectory()) {
      throw new IllegalArgumentException();
    }
    super.setOriginalFile(original);
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
    return getOriginalFile().getParent();
  }

  @Override
  public boolean shouldSkipEventSystem() {
    return true;
  }

  @Override
  public @Nullable VirtualFile findChild(@NotNull String name) {
    VirtualFile child = myAddedChildren.get(name);
    return child == null ? getOriginalFile().findChild(name) : child;
  }

  @Override
  public VirtualFile[] getChildren() {
    return Stream.concat(Stream.of(getOriginalFile()), myAddedChildren.values().stream())
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
    throw new UnsupportedOperationException();
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
