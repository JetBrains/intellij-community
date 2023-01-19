// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix.makefinal;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiLocalVariable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A fix to make variable effectively final
 */
@ApiStatus.Internal
public
interface EffectivelyFinalFixer {
  ExtensionPointName<EffectivelyFinalFixer> EP_NAME = ExtensionPointName.create("com.intellij.java.effectively.final.fixer");

  /**
   * @param var variable to fix
   * @return true if current fix can convert the variable to effectively final
   */
  boolean isAvailable(@NotNull PsiLocalVariable var);

  /**
   * Performs fix
   *
   * @param var variable to fix
   */
  void fix(@NotNull PsiLocalVariable var);

  /**
   * @param var variable to fix
   * @return human-readable name of the fix
   */
  @IntentionName String getText(@NotNull PsiLocalVariable var);
}
