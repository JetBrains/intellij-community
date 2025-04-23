// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.*;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * The FileWeakScope class represents a specialized search scope that is limited to a single VirtualFile
 * and manages the file reference using a {@link WeakReference}. This is used to define a constrained search
 * area when the file may not necessarily belong to the project roots and is backed by weak references to ensure
 * memory efficiency.
 */
@ApiStatus.Experimental
final class FileWeakScope extends GlobalSearchScope implements VirtualFileEnumeration {
  private final WeakReference<VirtualFile> myVirtualFile; // files can be out of project roots
  private final @Nullable @Nls String myDisplayName;
  private final @Nullable Module myModule;
  private final int myHashcode;
  FileWeakScope(@NotNull Project project, @NotNull VirtualFile virtualFile, @Nullable @Nls String displayName) {
    super(project);
    myVirtualFile = new WeakReference<>(virtualFile);
    myDisplayName = displayName;
    FileIndexFacade facade = project.isDefault() ? null : FileIndexFacade.getInstance(project);
    myModule = facade == null ? null : facade.getModuleForFile(virtualFile);
    myHashcode = Objects.hash(virtualFile, myModule, myDisplayName);
  }

  @Override
  public @NotNull @Unmodifiable Collection<VirtualFile> getFilesIfCollection() {
    VirtualFile file = myVirtualFile.get();
    if (file == null) return Collections.emptyList();
    return Collections.singleton(file);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return Comparing.equal(myVirtualFile.get(), file);
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
    return "Weak File: " + myVirtualFile.get();
  }

  @Override
  public @NotNull String getDisplayName() {
    return myDisplayName != null ? myDisplayName : super.getDisplayName();
  }

  @Override
  public boolean contains(int fileId) {
    VirtualFile file = myVirtualFile.get();
    return file instanceof VirtualFileWithId && ((VirtualFileWithId)file).getId() == fileId;
  }

  @Override
  public int @NotNull [] asArray() {
    VirtualFile file = myVirtualFile.get();
    return file instanceof VirtualFileWithId
           ? new int[]{((VirtualFileWithId)file).getId()}
           : ArrayUtil.EMPTY_INT_ARRAY;
  }

  @Override
  protected int calcHashCode() {
    return myHashcode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || o.getClass() != getClass()) return false;
    FileWeakScope files = (FileWeakScope)o;
    VirtualFile currentVirtualFile = myVirtualFile.get();
    if (currentVirtualFile == null) return false;
    return Objects.equals(currentVirtualFile, files.myVirtualFile.get()) &&
           Objects.equals(myDisplayName, files.myDisplayName) &&
           Objects.equals(myModule, files.myModule);
  }
}
