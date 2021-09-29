// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiCaseLabelElement;
import com.intellij.psi.PsiCaseLabelElementList;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;

public class PsiCaseLabelElementListImpl extends CompositePsiElement implements PsiCaseLabelElementList {
  private volatile PsiCaseLabelElement[] myElements;

  public PsiCaseLabelElementListImpl() {
    super(JavaElementType.CASE_LABEL_ELEMENT_LIST);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myElements = null;
  }

  @Override
  public PsiCaseLabelElement @NotNull [] getElements() {
    PsiCaseLabelElement[] elements = myElements;
    if (elements == null) {
      elements = getChildrenAsPsiElements(ElementType.JAVA_CASE_LABEL_ELEMENT_BIT_SET, PsiCaseLabelElement.ARRAY_FACTORY);
      if (elements.length > 10) {
        myElements = elements;
      }
    }
    return elements;
  }

  @Override
  public int getElementCount() {
    PsiCaseLabelElement[] elements = myElements;
    if (elements != null) return elements.length;

    return countChildren(ElementType.JAVA_CASE_LABEL_ELEMENT_BIT_SET);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitCaseLabelElementList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiCaseLabelElementList";
  }
}
