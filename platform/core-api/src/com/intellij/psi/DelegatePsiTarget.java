// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public class DelegatePsiTarget implements PsiTarget {
  private final PsiElement myElement;

  public DelegatePsiTarget(@NotNull PsiElement element) {
    myElement = element.getNavigationElement();
  }

  public int getTextOffset() {
    if (this instanceof PsiDeclaredTarget) {
      final TextRange range = ((PsiDeclaredTarget)this).getNameIdentifierRange();
      if (range != null) {
        return range.getStartOffset() + myElement.getTextRange().getStartOffset();
      }
    }

    return myElement.getTextOffset();
  }

  @Override
  public void navigate(boolean requestFocus) {
    final int offset = getTextOffset();
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(myElement);
    if (virtualFile != null && virtualFile.isValid()) {
      PsiNavigationSupport.getInstance().createNavigatable(myElement.getProject(), virtualFile, offset).navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    return PsiNavigationSupport.getInstance().canNavigate(myElement);
  }

  @Override
  public boolean canNavigateToSource() {
    return PsiNavigationSupport.getInstance().canNavigate(myElement);
  }

  @Override
  public final @NotNull PsiElement getNavigationElement() {
    return myElement;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DelegatePsiTarget psiTarget = (DelegatePsiTarget)o;

    if (!myElement.equals(psiTarget.myElement)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myElement.hashCode();
  }

  @Override
  public boolean isValid() {
    return getNavigationElement().isValid();
  }
}