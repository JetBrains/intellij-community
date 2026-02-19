// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

/**
 * This extension allow adding additional fixes in places where analysis wants to have different type for variable.
 */
public interface ChangeVariableTypeQuickFixProvider {
  ExtensionPointName<ChangeVariableTypeQuickFixProvider> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.changeVariableTypeQuickFixProvider");

  /**
   * Provides fixes for variables which are expected to have different type.
   * @param variable variable to create type fixes for
   * @param toReturn desired type
   */
  IntentionAction @NotNull [] getFixes(@NotNull PsiVariable variable, @NotNull PsiType toReturn);
}
