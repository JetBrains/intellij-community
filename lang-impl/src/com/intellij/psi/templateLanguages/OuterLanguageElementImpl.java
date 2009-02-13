/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class OuterLanguageElementImpl extends LeafPsiElement implements OuterLanguageElement {
  public OuterLanguageElementImpl(IElementType type, CharSequence text) {
    super(type, text);
  }

  public void accept(@NotNull final PsiElementVisitor visitor) {
    visitor.visitOuterLanguageElement(this);
  }
}
