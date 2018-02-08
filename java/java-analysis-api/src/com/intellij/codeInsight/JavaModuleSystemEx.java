// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ApiStatus.Experimental
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
    PsiFile file = target.getContainingFile();
    return file instanceof PsiClassOwner ? checkAccess(((PsiClassOwner)file).getPackageName(), (PsiClassOwner)file, place) : null;
  }

  @Nullable
  ErrorWithFixes checkAccess(@NotNull String targetPackageName, @Nullable PsiClassOwner targetFile, @NotNull PsiElement place);
}