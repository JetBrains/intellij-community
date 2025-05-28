// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.javadoc.PsiMarkdownReferenceLabel;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.tree.JavaDocElementType.DOC_MARKDOWN_REFERENCE_LABEL;

public class PsiMarkdownReferenceLabelImpl extends CompositePsiElement implements PsiMarkdownReferenceLabel {
  public PsiMarkdownReferenceLabelImpl() {
    super(DOC_MARKDOWN_REFERENCE_LABEL);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMarkdownReferenceLabel(this);
      return;
    }
    super.accept(visitor);
  }

  @Override
  public String toString() {
    return "PsiReferenceLabel:";
  }
}
