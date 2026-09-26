// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.controlflow;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ControlFlowProvider {
  ExtensionPointName<ControlFlowProvider> EP_NAME = ExtensionPointName.create("com.intellij.controlFlowProvider");

  /**
   * @return a control flow which contains the <code>element</code> or null if the <code>element</code> is not supported by provider
   */
  @Nullable
  ControlFlow getControlFlow(@NotNull PsiElement element);

  /**
   * @return an additional language-specific representation of the <code>instruction</code>
   * @param instruction belongs to a control flow which was created by the provider
   */
  @Nullable
  String getAdditionalInfo(@NotNull Instruction instruction);
}
