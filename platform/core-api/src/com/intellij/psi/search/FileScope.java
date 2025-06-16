// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

final class FileScope extends GlobalSearchScope implements VirtualFileEnumeration {
  private final VirtualFile myVirtualFile; // files can be out of project roots
  private final @Nullable @Nls String myDisplayName;
  private final Module myModule;

  FileScope(@NotNull Project project, @Nullable VirtualFile virtualFile, @Nullable @Nls String displayName) {
    super(project);
    myVirtualFile = virtualFile;
    myDisplayName = displayName;
    FileIndexFacade facade = project.isDefault() ? null : FileIndexFacade.getInstance(project);
    myModule = virtualFile == null || facade == null ? null : facade.getModuleForFile(virtualFile);
  }

  @Override
  public @NotNull @Unmodifiable Collection<VirtualFile> getFilesIfCollection() {
    return Collections.singleton(myVirtualFile);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return Comparing.equal(myVirtualFile, file);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return aModule == myModule;
  }

  @Override
  public boolean isSearchInLibraries() {
    return myModule == null;
  }

  @Override
  public String toString() {
    return "File: " + myVirtualFile;
  }

  @Override
  public @NotNull String getDisplayName() {
    return myDisplayName != null ? myDisplayName : super.getDisplayName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || o.getClass() != getClass()) return false;
    FileScope files = (FileScope)o;
    return Objects.equals(myVirtualFile, files.myVirtualFile) &&
           Objects.equals(myDisplayName, files.myDisplayName) &&
           Objects.equals(myModule, files.myModule);
  }

  @Override
  protected int calcHashCode() {
    return Objects.hash(myVirtualFile, myModule, myDisplayName);
  }

  @Override
  public boolean contains(int fileId) {
    return myVirtualFile instanceof VirtualFileWithId && ((VirtualFileWithId)myVirtualFile).getId() == fileId;
  }

  @Override
  public int @NotNull [] asArray() {
    return myVirtualFile instanceof VirtualFileWithId
           ? new int[]{((VirtualFileWithId)myVirtualFile).getId()}
           : ArrayUtil.EMPTY_INT_ARRAY;
  }
}
