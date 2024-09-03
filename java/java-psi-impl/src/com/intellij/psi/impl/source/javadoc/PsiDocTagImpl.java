// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiDocTagImpl extends CompositePsiElement implements PsiDocTag, Constants {
  private static final TokenSet TAG_VALUE_BIT_SET = TokenSet.create(
    DOC_TAG_VALUE_ELEMENT, DOC_METHOD_OR_FIELD_REF, DOC_PARAMETER_REF);
  private static final TokenSet VALUE_BIT_SET = TokenSet.orSet(TAG_VALUE_BIT_SET, TokenSet.create(
    DOC_TAG_VALUE_TOKEN, JAVA_CODE_REFERENCE, DOC_COMMENT_DATA, DOC_INLINE_TAG, DOC_REFERENCE_HOLDER, DOC_SHARP, DOC_LBRACKET, DOC_RBRACKET,
    DOC_LPAREN, DOC_RPAREN, DOC_CODE_FENCE, DOC_INLINE_CODE_FENCE, DOC_MARKDOWN_CODE_BLOCK, DOC_COMMA));

  public PsiDocTagImpl() {
    super(DOC_TAG);
  }

  @Override
  public PsiDocComment getContainingComment() {
    return (PsiDocComment)getParent();
  }

  @Override
  public PsiElement getNameElement() {
    return findPsiChildByType(DOC_TAG_NAME);
  }

  @Override
  public PsiDocTagValue getValueElement() {
    return (PsiDocTagValue)findPsiChildByType(TAG_VALUE_BIT_SET);
  }

  @Override
  public PsiElement @NotNull [] getDataElements() {
    return getChildrenAsPsiElements(VALUE_BIT_SET, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public @NotNull String getName() {
    if (getNameElement() == null) return "";
    return getNameElement().getText().substring(1);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameElement(), name);
    return this;
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
    else if (i == DOC_COMMENT_LEADING_ASTERISKS) {
      return ChildRole.DOC_COMMENT_ASTERISKS;
    }
    else if (TAG_VALUE_BIT_SET.contains(i)) {
      return ChildRole.DOC_TAG_VALUE;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTag(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiDocTag:" + getNameElement().getText();
  }
}