// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A superclass for all element types for root AST nodes in a {@link com.intellij.psi.PsiFile}.
 */
public class IFileElementType extends ILazyParseableElementType {
  public IFileElementType(final @Nullable Language language) {
    super("FILE", language);
  }

  public IFileElementType(final @NonNls @NotNull String debugName, final @Nullable Language language) {
    super(debugName, language);
  }

  /**
   * Allows constructing file element types without registering them, as in {@link IElementType#IElementType(String, Language, boolean)}.
   */
  public IFileElementType(final @NonNls @NotNull String debugName, final @Nullable Language language, boolean register) {
    super(debugName, language, register);
  }

  @Override
  public @Nullable ASTNode parseContents(final @NotNull ASTNode chameleon) {
    PsiElement psi = chameleon.getPsi();
    if (psi == null) {
      throw new AssertionError("Bad chameleon: " + chameleon +
                               " of type " + chameleon.getElementType() +
                               " in #" + chameleon.getElementType().getLanguage());
    }
    return doParseContents(chameleon, psi);
  }
}
