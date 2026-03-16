// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiArrayInitializerMemberValueImpl extends CompositePsiElement implements PsiArrayInitializerMemberValue {
  private static final Logger LOG = Logger.getInstance(PsiArrayInitializerMemberValueImpl.class);

  public PsiArrayInitializerMemberValueImpl() {
    super(JavaElementType.ANNOTATION_ARRAY_INITIALIZER);
  }

  @Override
  public PsiAnnotationMemberValue @NotNull [] getInitializers() {
    return getChildrenAsPsiElements(ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET, PsiAnnotationMemberValue.ARRAY_FACTORY);
  }

  @Override
  public int getInitializerCount() {
    return countChildren(ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET);
  }

  @Override
  public boolean isEmpty() {
    return findChildByType(ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET) == null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));

    switch (role) {
      case ChildRole.LBRACE:
        return findChildByType(JavaTokenType.LBRACE);

      case ChildRole.RBRACE:
        return findChildByType(JavaTokenType.RBRACE);

      default:
        return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);

    IElementType i = child.getElementType();
    if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    if (i == JavaTokenType.LBRACE) {
      return ChildRole.LBRACE;
    }
    if (i == JavaTokenType.RBRACE) {
      return ChildRole.RBRACE;
    }
    if (ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(child.getElementType())) {
      return ChildRole.ANNOTATION_VALUE;
    }
    return ChildRoleBase.NONE;
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(first.getElementType()) 
        && ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(last.getElementType())) {
      TreeElement firstAdded = super.addInternal(first, last, anchor, before);
      JavaSourceUtil.addSeparatingComma(this, first, ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET);
      return firstAdded;
    }

    return super.addInternal(first, last, anchor, before);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(child.getElementType())) {
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
