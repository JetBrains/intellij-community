// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiMarkdownCodeBlock;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiMarkdownCodeBlockImpl extends CompositePsiElement implements PsiMarkdownCodeBlock {
  public PsiMarkdownCodeBlockImpl() {
    super(JavaDocElementType.DOC_MARKDOWN_CODE_BLOCK);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMarkdownCodeBlock(this);
      return;
    }
    super.accept(visitor);
  }

  @Override
  public String toString() {
    return "PsiMarkdownCodeBlock:";
  }

  @Override
  public @Nullable Language getCodeLanguage() {
    String languageInfo = getLanguageInfo();
    if (languageInfo == null) return getLanguage();
    return Language.findLanguageByID(languageInfo);
  }

  @Override
  public @NotNull String getCodeText() {
    StringBuilder builder = new StringBuilder();

    ASTNode child = getFirstChildNode().getTreeNext();
    if (hasLanguageInfo() && child != null) {
      child = child.getTreeNext();
    }

    while (child != null) {
      IElementType i = child.getElementType();
      if (i != JavaDocTokenType.DOC_CODE_FENCE && i != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
        builder.append(child.getText());
      }
      child = child.getTreeNext();
    }
    return builder.toString();
  }

  private @Nullable String getLanguageInfo() {
    if (!hasLanguageInfo()) return null;
    return getChildren()[1].getText().trim();
  }

  /** @return True if the block has language info. It doesn't always translate to a {@link com.intellij.lang.Language} */
  private boolean hasLanguageInfo() {
    PsiElement[] children = getChildren();
    if (children.length < 2) return false;
    PsiElement languageInfoChild = children[1];
    if (languageInfoChild.getNode().getElementType() != JavaDocTokenType.DOC_COMMENT_DATA) return false;
    return !languageInfoChild.getText().trim().isEmpty();
  }
}
