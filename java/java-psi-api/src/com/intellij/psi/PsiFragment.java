// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.tree.IElementType;

/**
 * @author Bas Leijdekkers
 */
public interface PsiFragment extends PsiLiteralValue {

  /**
   * Returns the type of the token.
   * One of the fields STRING_TEMPLATE_BEGIN, STRING_TEMPLATE_MID, STRING_TEMPLATE_END,
   * TEXT_BLOCK_TEMPLATE_BEGIN, TEXT_BLOCK_TEMPLATE_MID or TEXT_BLOCK_TEMPLATE_END of JavaTokenType.
   *
   * @return the token type.
   */
  IElementType getTokenType();

  boolean isTextBlock();
}
