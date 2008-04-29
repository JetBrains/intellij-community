/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

/**
 * @author peter
*/
public abstract class PsiElementClassMember<T extends PsiDocCommentOwner> extends PsiDocCommentOwnerMemberChooserObject implements ClassMemberWithElement {
  private final T myPsiMember;
  private final PsiSubstitutor mySubstitutor;

  protected PsiElementClassMember(final T psiMember, String text) {
    this(psiMember, PsiSubstitutor.EMPTY, text);
  }

  protected PsiElementClassMember(final T psiMember, final PsiSubstitutor substitutor, String text) {
    super(psiMember, text, psiMember.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    myPsiMember = psiMember;
    mySubstitutor = substitutor;
  }

  public T getElement() {
    return myPsiMember;
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  public MemberChooserObject getParentNodeDelegate() {
    final PsiClass psiClass = myPsiMember.getContainingClass();
    final String text = PsiFormatUtil.formatClass(psiClass, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_FQ_NAME);
    return new PsiDocCommentOwnerMemberChooserObject(psiClass, text, psiClass.getIcon(0));
  }

}
