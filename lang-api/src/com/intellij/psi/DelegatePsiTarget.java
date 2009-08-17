/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
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

  public void navigate(boolean requestFocus) {
    final int offset = getTextOffset();
    final VirtualFile virtualFile = PsiUtilBase.getVirtualFile(myElement);
    if (virtualFile != null && virtualFile.isValid()) {
      new OpenFileDescriptor(myElement.getProject(), virtualFile, offset).navigate(requestFocus);
    }
  }

  public boolean canNavigate() {
    return EditSourceUtil.canNavigate(myElement);
  }

  public boolean canNavigateToSource() {
    return EditSourceUtil.canNavigate(myElement);
  }

  @NotNull
  public final PsiElement getNavigationElement() {
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

  public boolean isValid() {
    return getNavigationElement().isValid();
  }
}