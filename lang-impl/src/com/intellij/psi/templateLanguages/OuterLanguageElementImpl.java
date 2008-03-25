/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class OuterLanguageElementImpl extends LeafPsiElement implements OuterLanguageElement {
  public OuterLanguageElementImpl(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    super(type, buffer, startOffset, endOffset, table);
  }

  public void accept(@NotNull final PsiElementVisitor visitor) {
    visitor.visitOuterLanguageElement(this);
  }
}
