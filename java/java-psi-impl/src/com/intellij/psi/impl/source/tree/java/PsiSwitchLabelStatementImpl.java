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
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiSwitchLabelStatementImpl extends CompositePsiElement implements PsiSwitchLabelStatement, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiSwitchLabelStatementImpl");

  public PsiSwitchLabelStatementImpl() {
    super(SWITCH_LABEL_STATEMENT);
  }

  @Override
  public boolean isDefaultCase() {
    return findChildByRoleAsPsiElement(ChildRole.DEFAULT_KEYWORD) != null;
  }

  @Override
  public PsiExpression getCaseValue() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CASE_EXPRESSION);
  }

  @Override
  public PsiSwitchStatement getEnclosingSwitchStatement() {
    final CompositeElement guessedSwitch = getTreeParent().getTreeParent();
    return guessedSwitch != null && guessedSwitch.getElementType() == SWITCH_STATEMENT
           ? (PsiSwitchStatement)SourceTreeToPsiMap.treeElementToPsi(guessedSwitch)
           : null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.CASE_KEYWORD:
        return findChildByType(CASE_KEYWORD);

      case ChildRole.DEFAULT_KEYWORD:
        return findChildByType(DEFAULT_KEYWORD);

      case ChildRole.CASE_EXPRESSION:
        return findChildByType(EXPRESSION_BIT_SET);

      case ChildRole.COLON:
        return findChildByType(COLON);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == CASE_KEYWORD) {
      return ChildRole.CASE_KEYWORD;
    }
    else if (i == DEFAULT_KEYWORD) {
      return ChildRole.DEFAULT_KEYWORD;
    }
    else if (i == COLON) {
      return ChildRole.COLON;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.CASE_EXPRESSION;
      }
      else {
        return ChildRoleBase.NONE;
      }
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    if (lastParent == null) return true;

    final PsiSwitchStatement switchStatement = getEnclosingSwitchStatement();
    if (switchStatement != null) {
      final PsiExpression expression = switchStatement.getExpression();
      if (expression != null && expression.getType() instanceof PsiClassType) {
        final PsiClass aClass = ((PsiClassType)expression.getType()).resolve();
        if(aClass != null) aClass.processDeclarations(new FilterScopeProcessor(ElementClassFilter.ENUM_CONST, processor), state, this, place);
      }
    }
    return true;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSwitchLabelStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiSwitchLabelStatement";
  }
}
