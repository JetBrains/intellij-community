// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.JavaModuleSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


/**
 * Allows checking accessibility of the class and provide fixes.
 */
public interface JavaModuleSystemEx extends JavaModuleSystem {
  final class ErrorWithFixes {
    public final @NotNull @Nls String message;
    public final @NotNull List<IntentionAction> fixes;

    public ErrorWithFixes(@NotNull @Nls String message) {
      this(message, Collections.emptyList());
    }

    public ErrorWithFixes(@NotNull @Nls String message, @NotNull List<IntentionAction> fixes) {
      this.message = message;
      this.fixes = fixes;
    }
  }

  @Nullable
  default ErrorWithFixes checkAccess(@NotNull PsiClass target, @NotNull PsiElement place) {
    String packageName = PsiUtil.getPackageName(target);
    return packageName != null ? checkAccess(packageName, target.getContainingFile(), place) : null;
  }

  @Nullable
  ErrorWithFixes checkAccess(@NotNull String targetPackageName, @Nullable PsiFile targetFile, @NotNull PsiElement place);
}