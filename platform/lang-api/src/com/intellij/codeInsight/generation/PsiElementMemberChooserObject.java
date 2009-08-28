package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PsiElementMemberChooserObject extends MemberChooserObjectBase {
  private final PsiElement myPsiElement;

  public PsiElementMemberChooserObject(@NotNull final PsiElement psiElement, final String text) {
    super(text);
    myPsiElement = psiElement;
  }

  public PsiElementMemberChooserObject(final PsiElement psiElement, final String text, @Nullable final Icon icon) {
    super(text, icon);
    myPsiElement = psiElement;
  }

  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PsiElementMemberChooserObject that = (PsiElementMemberChooserObject)o;

    if (!myPsiElement.getManager().areElementsEquivalent(myPsiElement, that.myPsiElement)) return false;

    return true;
  }

  public int hashCode() {
    return myPsiElement.hashCode();
  }
}
