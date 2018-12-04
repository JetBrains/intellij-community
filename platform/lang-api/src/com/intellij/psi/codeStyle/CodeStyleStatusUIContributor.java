// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CodeStyleStatusUIContributor {

  boolean areActionsAvailable(@NotNull VirtualFile file);

  @Nullable
  AnAction[] getActions(@NotNull PsiFile file);

  @NotNull
  String getTooltip();

  @Nullable
  String getHint();

  @Nullable
  String getAdvertisementText(@NotNull PsiFile psiFile);

  @Nullable
  AnAction createDisableAction(@NotNull Project project);
}
