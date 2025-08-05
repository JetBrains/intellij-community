// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This extension point allows the resolve subsystem to define custom {@link GlobalSearchScope} this particular {@link VirtualFile} should
 * be resolved in.
 * <p> By default, this scope consists of the current module with all its dependencies, but sometimes it should be something completely different.
 * To add some scope to the existing resolve scope it may be easier to use {@link ResolveScopeEnlarger} instead.
 * @see ResolveScopeEnlarger
 */
public abstract class ResolveScopeProvider {
  public static final ExtensionPointName<ResolveScopeProvider> EP_NAME = ExtensionPointName.create("com.intellij.resolveScopeProvider");

  /**
   * @return {@link GlobalSearchScope} defining where this particular {@code file} should be resolved in. `Null` value means that
   * the {@code file} will be resolved in the one of the following scopes (first matching): module (with dependencies and libraries),
   * project (with the {@code file} itself), library (if the {@code file} is a part of it).
   */
  public abstract @Nullable GlobalSearchScope getResolveScope(@NotNull VirtualFile file, @NotNull Project project);

  @ApiStatus.Experimental
  public @Nullable GlobalSearchScope getResolveScope(@NotNull VirtualFile file, @NotNull CodeInsightContext context, @NotNull Project project) {
    return getResolveScope(file, project);
  }
}
