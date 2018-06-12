/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class PsiArrayInitializerMemberValueImpl extends CompositePsiElement implements PsiArrayInitializerMemberValue {
  private static final Logger LOG = Logger.getInstance(PsiArrayInitializerMemberValueImpl.class);
  private static final TokenSet MEMBER_SET = ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET;

  public PsiArrayInitializerMemberValueImpl() {
    super(JavaElementType.ANNOTATION_ARRAY_INITIALIZER);
  }

  @Override
  @NotNull
  public PsiAnnotationMemberValue[] getInitializers() {
    return getChildrenAsPsiElements(MEMBER_SET, PsiAnnotationMemberValue.ARRAY_FACTORY);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));

    switch (role) {
      default:
        return null;

      case ChildRole.LBRACE:
        return findChildByType(JavaTokenType.LBRACE);

      case ChildRole.RBRACE:
        return findChildByType(JavaTokenType.RBRACE);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);

    IElementType i = child.getElementType();
    if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == JavaTokenType.LBRACE) {
      return ChildRole.LBRACE;
    }
    else if (i == JavaTokenType.RBRACE) {
      return ChildRole.RBRACE;
    }
    else if (MEMBER_SET.contains(child.getElementType())) {
      return ChildRole.ANNOTATION_VALUE;
    }
    return ChildRoleBase.NONE;
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (MEMBER_SET.contains(first.getElementType()) && MEMBER_SET.contains(last.getElementType())) {
      TreeElement firstAdded = super.addInternal(first, last, anchor, before);
      JavaSourceUtil.addSeparatingComma(this, first, MEMBER_SET);
      return firstAdded;
    }

    return super.addInternal(first, last, anchor, before);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (MEMBER_SET.contains(child.getElementType())) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    super.deleteChildInternal(child);
  }

  @Override
  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationArrayInitializer(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiArrayInitializerMemberValue:" + getText();
  }
}
