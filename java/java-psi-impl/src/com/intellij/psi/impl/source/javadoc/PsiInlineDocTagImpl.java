/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private static final TokenSet VALUE_NO_WHITESPACE_BIT_SET = TokenSet.orSet(TAG_VALUE_BIT_SET, TokenSet.create(
    JAVA_CODE_REFERENCE, DOC_TAG_VALUE_TOKEN, DOC_COMMENT_DATA, DOC_INLINE_TAG, DOC_REFERENCE_HOLDER, DOC_COMMENT_BAD_CHARACTER));
  private static final TokenSet VALUE_BIT_SET = TokenSet.orSet(TAG_VALUE_BIT_SET, TokenSet.create(
    JAVA_CODE_REFERENCE, DOC_TAG_VALUE_TOKEN, WHITE_SPACE, DOC_COMMENT_DATA, DOC_INLINE_TAG, DOC_REFERENCE_HOLDER, DOC_COMMENT_BAD_CHARACTER));

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

  @NotNull
  @Override
  public PsiElement[] getDataElements() {
    return getChildrenAsPsiElements(VALUE_BIT_SET, PsiElement.ARRAY_FACTORY);
  }

  @NotNull
  public PsiElement[] getDataElementsIgnoreWhitespaces() {
    return getChildrenAsPsiElements(VALUE_NO_WHITESPACE_BIT_SET, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public PsiDocTagValue getValueElement() {
    return (PsiDocTagValue)findPsiChildByType(TAG_VALUE_BIT_SET);
  }

  @Override
  public String getName() {
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
