// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import org.jetbrains.annotations.NotNull;

public interface TodoCacheManager {
  final class SERVICE {
    private SERVICE() {
    }

    public static TodoCacheManager getInstance(Project project) {
      return ServiceManager.getService(project, TodoCacheManager.class);
    }
  }


  /**
   * @return all VirtualFile's that contain todoItems under project roots
   */
  PsiFile @NotNull [] getFilesWithTodoItems();

  /**
   * @return -1 if it's not known
   */
  int getTodoCount(@NotNull VirtualFile file, @NotNull IndexPatternProvider patternProvider);

  /**
   * @return -1 if it's not known
   */
  int getTodoCount(@NotNull VirtualFile file, @NotNull IndexPattern pattern);
}
