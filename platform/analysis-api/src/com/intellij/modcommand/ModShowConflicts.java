// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ModShowConflicts(@NotNull Map<@NotNull PsiElement, @NotNull Conflict> conflicts,
                               @NotNull ModCommand nextStep) implements ModCommand {
  public record Conflict(@NotNull List<@NotNull @Nls String> messages) {
  }

  @Override
  public boolean isEmpty() {
    return conflicts().isEmpty() && nextStep().isEmpty();
  }

  @Override
  public @NotNull Set<@NotNull VirtualFile> modifiedFiles() {
    return nextStep.modifiedFiles();
  }
}
