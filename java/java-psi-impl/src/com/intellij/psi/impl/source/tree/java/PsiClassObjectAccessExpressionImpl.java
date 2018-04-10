/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.RowIcon;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PsiClassObjectAccessExpressionImpl extends ExpressionPsiElement implements PsiClassObjectAccessExpression, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiClassObjectAccessExpressionImpl");

  public PsiClassObjectAccessExpressionImpl() {
    super(CLASS_OBJECT_ACCESS_EXPRESSION);
  }

  @NotNull
  @Override
  public PsiType getType() {
    return PsiImplUtil.getType(this);
  }

  @Override
  @NotNull
  public PsiTypeElement getOperand() {
    return (PsiTypeElement)findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.TYPE:
        return findChildByType(TYPE);

      case ChildRole.DOT:
        return findChildByType(DOT);

      case ChildRole.CLASS_KEYWORD:
        return findChildByType(CLASS_KEYWORD);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == TYPE) {
      return ChildRole.TYPE;
    }
    else if (i == DOT) {
      return ChildRole.DOT;
    }
    else if (i == CLASS_KEYWORD) {
      return ChildRole.CLASS_KEYWORD;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClassObjectAccessExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiClassObjectAccessExpression:" + getText();
  }

  @Override
  protected Icon computeBaseIcon(int flags) {
    return getElementIcon(flags);
  }

  @Override
  @NotNull
  public Icon getElementIcon(final int flags) {
    final RowIcon rowIcon = ElementBase.createLayeredIcon(this, PlatformIcons.FIELD_ICON, 0);
    rowIcon.setIcon(PlatformIcons.PUBLIC_ICON, 1);
    return rowIcon;
  }
}

