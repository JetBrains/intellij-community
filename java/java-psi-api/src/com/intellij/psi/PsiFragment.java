// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a fragment of a template of a Java 21 Preview template expression.
 *
 * @author Bas Leijdekkers
 */
public interface PsiFragment extends PsiLiteralValue, PsiJavaToken, PsiLanguageInjectionHost {

  /**
   * Returns the type of the fragment token.
   * One of the fields STRING_TEMPLATE_BEGIN, STRING_TEMPLATE_MID, STRING_TEMPLATE_END,
   * TEXT_BLOCK_TEMPLATE_BEGIN, TEXT_BLOCK_TEMPLATE_MID or TEXT_BLOCK_TEMPLATE_END of JavaTokenType.
   *
   * @return the token type.
   */
  @Override
  IElementType getTokenType();

  /**
   * @return true, if this fragment is a text block fragment, false, if this is string fragment.
   */
  boolean isTextBlock();

  /**
   * Returns the string value of this fragment.
   *
   * @return the value of the expression, or null if the fragment is invalid (e.g. unclosed).
   */
  @Override
  @Nullable String getValue();
}
