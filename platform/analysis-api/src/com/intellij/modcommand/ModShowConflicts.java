// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shows UI that displays conflicts and requires user confirmation to proceed.
 * Not executed in batch; preview shows next step automatically (it must be computed before conflicts are shown).
 * 
 * @param conflicts conflicts to show
 */
public record ModShowConflicts(@NotNull Map<@NotNull PsiElement, @NotNull Conflict> conflicts) implements ModCommand {
  /**
   * Conflict description
   * @param messages list of user-readable messages that describe the problem
   */
  public record Conflict(@NotNull List<@NotNull @Nls String> messages) {
  }

  @Override
  public boolean isEmpty() {
    return conflicts().isEmpty();
  }
}
