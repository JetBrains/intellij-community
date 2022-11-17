// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.cache;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CodeStyleCachingService {

  Key<StackTraceElement[]> CALL_TRACE = Key.create(CodeStyleCachingService.class.getName() + ".call-trace");

  static CodeStyleCachingService getInstance(@NotNull Project project) {
    return project.getService(CodeStyleCachingService.class);
  }

  CodeStyleSettings tryGetSettings(@NotNull VirtualFile file);

  @Nullable
  CodeStyleSettings tryGetSettings(@NotNull PsiFile file);

  void scheduleWhenSettingsComputed(@NotNull PsiFile file, @NotNull Runnable runnable);

  @Nullable
  UserDataHolder getDataHolder(@NotNull VirtualFile virtualFile);
}
