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
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class PsiWhileStatementImpl extends PsiLoopStatementImpl implements PsiWhileStatement, Constants {
  private static final Logger LOG = Logger.getInstance(PsiWhileStatementImpl.class);

  public PsiWhileStatementImpl() {
    super(WHILE_STATEMENT);
  }

  @Override
  public PsiExpression getCondition(){
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  @Override
  public PsiStatement getBody(){
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.LOOP_BODY);
  }

  @Override
  public PsiJavaToken getLParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.LPARENTH);
  }

  @Override
  public PsiJavaToken getRParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.RPARENTH);
  }

  @Override
  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.WHILE_KEYWORD:
        return findChildByType(WHILE_KEYWORD);

      case ChildRole.LPARENTH:
        return findChildByType(LPARENTH);

      case ChildRole.CONDITION:
        return findChildByType(EXPRESSION_BIT_SET);

      case ChildRole.RPARENTH:
        return findChildByType(RPARENTH);

      case ChildRole.LOOP_BODY:
        return PsiImplUtil.findStatementChild(this);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == WHILE_KEYWORD) {
      return ChildRole.WHILE_KEYWORD;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.CONDITION;
      }
      else if (child.getPsi() instanceof PsiStatement) {
        return ChildRole.LOOP_BODY;
      }
      else {
        return ChildRoleBase.NONE;
      }
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitWhileStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    ElementClassHint elementClassHint = processor.getHint(ElementClassHint.KEY);
    if (elementClassHint != null && !elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) return true;
    if (lastParent == null) {
      return processDeclarationsInLoopCondition(processor, state, place, this);
    }
    PsiExpression condition = getCondition();
    if (condition != null && lastParent == getBody()) {
      return condition.processDeclarations(processor, PatternResolveState.WHEN_TRUE.putInto(state), null, place);
    }
    return true;
  }

  static boolean processDeclarationsInLoopCondition(@NotNull PsiScopeProcessor processor,
                                                    @NotNull ResolveState state,
                                                    @NotNull PsiElement place,
                                                    @NotNull PsiConditionalLoopStatement loop) {
    if (state.get(PatternResolveState.KEY) == PatternResolveState.WHEN_NONE) return true;
    PsiExpression condition = loop.getCondition();
    if (condition == null) return true;
    PsiScopeProcessor conditionProcessor = (element, s) -> {
      assert element instanceof PsiPatternVariable;
      final NameHint hint = processor.getHint(NameHint.KEY);
      if (hint != null && !((PsiPatternVariable)element).getName().equals(hint.getName(s))) {
        return true;
      }
      PatternResolveState resolveState = PatternResolveState.stateAtParent((PsiPatternVariable)element, condition);
      if (resolveState == PatternResolveState.WHEN_TRUE ||
          !PsiTreeUtil
            .processElements(loop, e -> !(e instanceof PsiBreakStatement) || ((PsiBreakStatement)e).findExitedStatement() != loop)) {
        return true;
      }
      return processor.execute(element, s);
    };
    return condition.processDeclarations(conditionProcessor, PatternResolveState.WHEN_BOTH.putInto(state), null, place);
  }

  @Override
  public String toString(){
    return "PsiWhileStatement";
  }
}
