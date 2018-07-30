// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.hint;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.MixinEP;
import org.jetbrains.annotations.NotNull;

/**
 * Returns the subset of the text range of the specified element which is considered its declaration.
 * For example, the declaration range of a method includes its modifiers, return type, name and
 * parameter list.
 *
 * Used by "Context info" action.
 */
public interface DeclarationRangeHandler<T extends PsiElement> {
  ExtensionPointName<MixinEP<DeclarationRangeHandler>> EP_NAME = ExtensionPointName.create("com.intellij.declarationRangeHandler");

  /**
   * Returns the declaration range for the specified container.
   * @param container the container
   * @return the declaration range for it.
   */
  @NotNull
  TextRange getDeclarationRange(@NotNull T container);
}
