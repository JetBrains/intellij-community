// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public interface TodoCacheManager {

  /**
   * @deprecated please use {@link TodoCacheManager#getInstance} instead
   */
  @Deprecated(forRemoval = true)
  final class SERVICE {
    public static TodoCacheManager getInstance(Project project) {
      return TodoCacheManager.getInstance(project);
    }
  }

  static TodoCacheManager getInstance(Project project) {
    return project.getService(TodoCacheManager.class);
  }

  boolean processFilesWithTodoItems(@NotNull Processor<? super @NotNull PsiFile> processor);

  /**
   * @return -1 if it's not known
   */
  int getTodoCount(@NotNull VirtualFile file, @NotNull IndexPatternProvider patternProvider);

  /**
   * @return -1 if it's not known
   */
  int getTodoCount(@NotNull VirtualFile file, @NotNull IndexPattern pattern);
}
