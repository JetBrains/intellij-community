// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.javadoc.PsiMarkdownCodeBlock;
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

  @Override
  public String getLabelText() {
    StringBuilder builder = new StringBuilder();
    ASTNode child = getFirstChildNode();
    while (child != null) {

      // Subloop with nearly the same code to avoid recursion/multiple StringBuilder instances
      // The code block is assumed to be an inline code block
      if (child instanceof PsiMarkdownCodeBlock) {
        ASTNode codeChild = child.getFirstChildNode();
        while (codeChild != null) {
          if (codeChild.getElementType() != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
            builder.append(codeChild.getText());
          }
          codeChild = codeChild.getTreeNext();
        }
      }
      else if (child.getElementType() != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
        builder.append(child.getText());
      }
      child = child.getTreeNext();
    }
    return builder.toString();
  }
}
