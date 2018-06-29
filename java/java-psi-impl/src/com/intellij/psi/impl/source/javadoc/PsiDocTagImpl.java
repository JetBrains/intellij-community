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
    DOC_TAG_VALUE_TOKEN, JAVA_CODE_REFERENCE, DOC_COMMENT_DATA, DOC_INLINE_TAG, DOC_REFERENCE_HOLDER));

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

  @NotNull
  @Override
  public PsiElement[] getDataElements() {
    return getChildrenAsPsiElements(VALUE_BIT_SET, PsiElement.ARRAY_FACTORY);
  }

  @NotNull
  @Override
  public String getName() {
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
  @NotNull
  public PsiReference[] getReferences() {
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

  public String toString() {
    return "PsiDocTag:" + getNameElement().getText();
  }
}