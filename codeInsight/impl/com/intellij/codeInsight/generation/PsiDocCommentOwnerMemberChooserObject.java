/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

/**
 * @author peter
*/
public class PsiDocCommentOwnerMemberChooserObject extends PsiElementMemberChooserObject {
  public PsiDocCommentOwnerMemberChooserObject(final PsiDocCommentOwner owner, final String text, Icon icon) {
    super(owner, text, icon);
  }

  public PsiDocCommentOwner getPsiDocCommentOwner() {
    return (PsiDocCommentOwner)getPsiElement();
  }

  protected SimpleTextAttributes getTextAttributes(final JTree tree) {
    return new SimpleTextAttributes(
        getPsiDocCommentOwner().isDeprecated() ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN,
        tree.getForeground());
  }
}
