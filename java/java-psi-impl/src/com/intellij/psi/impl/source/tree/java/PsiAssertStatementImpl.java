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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

public class PsiAssertStatementImpl extends CompositePsiElement implements PsiAssertStatement, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiAssertStatementImpl");

  public PsiAssertStatementImpl() {
    super(ASSERT_STATEMENT);
  }

  @Override
  public PsiExpression getAssertCondition() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  @Override
  public PsiExpression getAssertDescription() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.ASSERT_DESCRIPTION);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.ASSERT_KEYWORD:
        return findChildByType(ASSERT_KEYWORD);

      case ChildRole.CONDITION:
        return findChildByType(EXPRESSION_BIT_SET);

      case ChildRole.COLON:
        return findChildByType(COLON);

      case ChildRole.ASSERT_DESCRIPTION:
        {
          ASTNode colon = findChildByRole(ChildRole.COLON);
          if (colon == null) return null;
          ASTNode child;
          for(child = colon.getTreeNext(); child != null; child = child.getTreeNext()){
            if (EXPRESSION_BIT_SET.contains(child.getElementType())) break;
          }
          return child;
        }

      case ChildRole.CLOSING_SEMICOLON:
        return findChildByType(SEMICOLON);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == ASSERT_KEYWORD) {
      return ChildRole.ASSERT_KEYWORD;
    }
    else if (i == COLON) {
      return ChildRole.COLON;
    }
    else if (i == SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        int role = getChildRole(child, ChildRole.CONDITION);
        if (role != ChildRoleBase.NONE) return role;
        return ChildRole.ASSERT_DESCRIPTION;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAssertStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiAssertStatement";
  }
}
