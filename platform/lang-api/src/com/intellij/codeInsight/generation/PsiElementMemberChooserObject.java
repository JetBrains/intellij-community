// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PsiElementMemberChooserObject extends MemberChooserObjectBase {
  private final @NotNull PsiElement myPsiElement;
  private final @NotNull SmartPsiElementPointer<?> myPsiElementPointer;

  public PsiElementMemberChooserObject(final @NotNull PsiElement psiElement, final @NlsContexts.Label String text) {
    this(psiElement, text, null);
  }

  public PsiElementMemberChooserObject(@NotNull PsiElement psiElement, final @NlsContexts.Label String text, final @Nullable Icon icon) {
    super(text, icon);
    myPsiElement = psiElement;
    myPsiElementPointer = SmartPointerManager.createPointer(myPsiElement);
  }

  /**
   * @return PsiElement associated with this object. May return invalid element if the element was invalidated and cannot be restored
   * via smart pointer.
   */
  public @NotNull PsiElement getPsiElement() {
    PsiElement element = myPsiElementPointer.getElement();
    return element == null ?
           myPsiElement : // to at least get invalidation trace in PIEAE later 
           element;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PsiElementMemberChooserObject that = (PsiElementMemberChooserObject)o;

    return myPsiElementPointer.equals(that.myPsiElementPointer);
  }

  @Override
  public int hashCode() {
    return myPsiElementPointer.hashCode();
  }
}
