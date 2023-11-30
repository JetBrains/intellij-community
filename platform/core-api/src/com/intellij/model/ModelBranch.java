// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * @deprecated failed experiment; removing
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public interface ModelBranch extends UserDataHolder {
  static @NotNull ModelPatch performInBranch(@NotNull Project project, @NotNull Consumer<? super ModelBranch> action) {
    throw new UnsupportedOperationException();
  }

  <T extends PsiElement> @NotNull T obtainPsiCopy(@NotNull T original);

  static @Nullable ModelBranch getFileBranch(@NotNull VirtualFile file) {
    return null;
  }
}
