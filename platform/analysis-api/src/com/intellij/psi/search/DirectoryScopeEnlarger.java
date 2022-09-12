// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Extend a directory scope by some other directories that can override/mirror the directory content.
 * E.g. additional results for `Find in Files...` can be added this way.
 *
 * @see com.intellij.psi.search.UseScopeEnlarger
 * @see GlobalSearchScopesCore#directoriesScope(Project, boolean, VirtualFile...)
 */
@ApiStatus.Experimental
public interface DirectoryScopeEnlarger {
  ExtensionPointName<DirectoryScopeEnlarger> EP_NAME = ExtensionPointName.create("com.intellij.directoryScopeEnlarger");

  @NotNull List<@NotNull VirtualFile> getAdditionalScope(@NotNull Project project, @NotNull VirtualFile directory);
}
