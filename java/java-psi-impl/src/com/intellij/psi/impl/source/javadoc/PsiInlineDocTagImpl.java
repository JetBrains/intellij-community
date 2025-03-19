// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiInlineDocTagImpl extends CompositePsiElement implements PsiInlineDocTag, Constants {
  private static final TokenSet TAG_VALUE_BIT_SET = TokenSet.create(DOC_TAG_VALUE_ELEMENT, DOC_METHOD_OR_FIELD_REF);
  static final TokenSet VALUE_BIT_SET = TokenSet.orSet(TAG_VALUE_BIT_SET, TokenSet.create(
    JAVA_CODE_REFERENCE, DOC_TAG_VALUE_TOKEN, WHITE_SPACE, DOC_COMMENT_DATA, DOC_INLINE_TAG, DOC_REFERENCE_HOLDER,
    DOC_COMMENT_BAD_CHARACTER, DOC_SNIPPET_TAG_VALUE,
    DOC_SHARP, DOC_LBRACKET, DOC_RBRACKET, DOC_LPAREN, DOC_RPAREN, DOC_CODE_FENCE, DOC_INLINE_CODE_FENCE, DOC_MARKDOWN_CODE_BLOCK, DOC_COMMA));

  public PsiInlineDocTagImpl() {
    super(DOC_INLINE_TAG);
  }

  @Override
  public PsiDocComment getContainingComment() {
    ASTNode scope = getTreeParent();
    while (scope.getElementType() != JavaDocElementType.DOC_COMMENT) {
      scope = scope.getTreeParent();
    }
    return (PsiDocComment)SourceTreeToPsiMap.treeElementToPsi(scope);
  }

  @Override
  public PsiElement getNameElement() {
    return findPsiChildByType(DOC_TAG_NAME);
  }

  @Override
  public PsiElement @NotNull [] getDataElements() {
    return getChildrenAsPsiElements(VALUE_BIT_SET, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public PsiDocTagValue getValueElement() {
    return (PsiDocTagValue)findPsiChildByType(TAG_VALUE_BIT_SET);
  }

  @Override
  public @NotNull String getName() {
    final PsiElement nameElement = getNameElement();
    if (nameElement == null) return "";
    return nameElement.getText().substring(1);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    assert child.getTreeParent() == this : child.getTreeParent();
    IElementType i = child.getElementType();
    if (i == DOC_TAG_NAME) {
      return ChildRole.DOC_TAG_NAME;
    }
    else if (i == JavaDocElementType.DOC_COMMENT || i == DOC_INLINE_TAG) {
      return ChildRole.DOC_CONTENT;
    }
    else if (i == DOC_INLINE_TAG_START) {
      return ChildRole.DOC_INLINE_TAG_START;
    }
    else if (i == DOC_INLINE_TAG_END) {
      return ChildRole.DOC_INLINE_TAG_END;
    }
    else if (TAG_VALUE_BIT_SET.contains(i)) {
      return ChildRole.DOC_TAG_VALUE;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitInlineDocTag(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    PsiElement nameElement = getNameElement();
    return "PsiInlineDocTag:" + (nameElement != null ? nameElement.getText() : null);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameElement(), name);
    return this;
  }
}
