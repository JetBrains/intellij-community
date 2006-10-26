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
public class PsiDocCommentOwnerMemberChooserObject extends MemberChooserObjectBase {
  private final PsiDocCommentOwner myOwner;

  public PsiDocCommentOwnerMemberChooserObject(final PsiDocCommentOwner owner, final String text, Icon icon) {
    super(text, icon);
    myOwner = owner;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PsiDocCommentOwnerMemberChooserObject that = (PsiDocCommentOwnerMemberChooserObject)o;

    if (!myOwner.equals(that.myOwner)) return false;

    return true;
  }

  public int hashCode() {
    return myOwner.hashCode();
  }

  public PsiDocCommentOwner getPsiElement() {
    return myOwner;
  }

  protected SimpleTextAttributes getTextAttributes(final JTree tree) {
    return new SimpleTextAttributes(
        myOwner.isDeprecated() ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN,
        tree.getForeground());
  }
}
