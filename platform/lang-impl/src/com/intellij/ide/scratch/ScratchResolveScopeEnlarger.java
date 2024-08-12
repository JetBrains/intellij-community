// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ResolveScopeEnlarger;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public final class ScratchResolveScopeEnlarger extends ResolveScopeEnlarger{
  @Override
  public @Nullable SearchScope getAdditionalResolveScope(@NotNull VirtualFile file, @NotNull Project project) {
    return ScratchUtil.isScratch(file)? GlobalSearchScope.fileScope(project, file) : null;
  }
}
