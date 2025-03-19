// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.search.impl.VirtualFileEnumerationAware;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author gregsh
 */
public final class ScratchesSearchScope extends GlobalSearchScope implements VirtualFileEnumerationAware {
  private static final NotNullLazyKey<GlobalSearchScope, Project> SCRATCHES_SCOPE_KEY = NotNullLazyKey.createLazyKey(
    "SCRATCHES_SCOPE_KEY",
    project -> new ScratchesSearchScope(project));

  public static @NotNull GlobalSearchScope getScratchesScope(@NotNull Project project) {
    return SCRATCHES_SCOPE_KEY.getValue(project);
  }

  private ScratchesSearchScope(@NotNull Project project) {
    super(project);
  }

  @Override
  public @NotNull String getDisplayName() {
    return ScratchesNamedScope.scratchesAndConsoles();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return ScratchesNamedScope.contains(Objects.requireNonNull(getProject()), file);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return false;
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }

  @Override
  public VirtualFileEnumeration extractFileEnumeration() {
    return ScratchFileService.getInstance().extractFileEnumeration();
  }
}
