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
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

public class PsiSynchronizedStatementImpl extends CompositePsiElement implements PsiSynchronizedStatement, Constants {
  private static final Logger LOG = Logger.getInstance(PsiSynchronizedStatementImpl.class);

  public PsiSynchronizedStatementImpl() {
    super(SYNCHRONIZED_STATEMENT);
  }

  @Override
  public PsiExpression getLockExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.LOCK);
  }

  @Override
  public PsiCodeBlock getBody() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.BLOCK);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.SYNCHRONIZED_KEYWORD:
        return findChildByType(SYNCHRONIZED_KEYWORD);

      case ChildRole.LPARENTH:
        return findChildByType(LPARENTH);

      case ChildRole.LOCK:
        return findChildByType(EXPRESSION_BIT_SET);

      case ChildRole.RPARENTH:
        return findChildByType(RPARENTH);

      case ChildRole.BLOCK:
        return findChildByType(CODE_BLOCK);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == SYNCHRONIZED_KEYWORD) {
      return ChildRole.SYNCHRONIZED_KEYWORD;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else if (i == CODE_BLOCK) {
      return ChildRole.BLOCK;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.LOCK;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSynchronizedStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSynchronizedStatement";
  }
}
