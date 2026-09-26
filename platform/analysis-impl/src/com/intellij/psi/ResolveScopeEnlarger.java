// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This extension point allows resolve subsystem to modify existing resolve scope for the particular {@link VirtualFile} by specifying
 * {@link SearchScope} which should be added to the existing resolve scope.
 * For example, {@link com.intellij.ide.scratch.ScratchResolveScopeEnlarger} adds current scratch file to the standard resolve scope
 * to be able to resolve stuff inside scratch file even if it's outside the project roots.
 */
public abstract class ResolveScopeEnlarger {
  public static final ExtensionPointName<ResolveScopeEnlarger> EP_NAME = ExtensionPointName.create("com.intellij.resolveScopeEnlarger");

  public abstract @Nullable SearchScope getAdditionalResolveScope(@NotNull VirtualFile file, @NotNull Project project);

  @ApiStatus.Experimental
  public @Nullable SearchScope getAdditionalResolveScope(@NotNull VirtualFile file, @NotNull CodeInsightContext context, @NotNull Project project) {
    return getAdditionalResolveScope(file, project);
  }
}
