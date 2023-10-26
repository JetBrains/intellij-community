// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Infer variable types in macros
 * @author Max Medvedev
 */
public abstract class VariableTypeCalculator {
  public static final ExtensionPointName<VariableTypeCalculator> EP_NAME =
    ExtensionPointName.create("com.intellij.variableTypeCalculator");

  @Nullable
  @Contract(pure = true)
  public abstract PsiType inferVarTypeAt(@NotNull PsiVariable var, @NotNull PsiElement place);

  /**
   * @return inferred type of variable in the context of place
   */
  @NotNull
  public static PsiType getVarTypeAt(@NotNull PsiVariable var, @NotNull PsiElement place) {
    for (VariableTypeCalculator calculator : EP_NAME.getExtensionList()) {
      final PsiType type = calculator.inferVarTypeAt(var, place);
      if (type != null) return type;
    }

    return var.getType();
  }
}
