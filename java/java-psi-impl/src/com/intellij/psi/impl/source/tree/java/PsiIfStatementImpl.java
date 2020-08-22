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
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiIfStatementImpl extends CompositePsiElement implements PsiIfStatement, Constants {
  private static final Logger LOG = Logger.getInstance(PsiIfStatementImpl.class);

  public PsiIfStatementImpl() {
    super(IF_STATEMENT);
  }

  @Override
  public PsiExpression getCondition() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child == getThenBranch()) {
      replaceChildInternal(child, (TreeElement)JavaPsiFacade.getElementFactory(getProject()).createStatementFromText("{}", null));
      return;
    }
    if (child == getElseBranch()) {
      ASTNode elseKeyword = findChildByRole(ChildRole.ELSE_KEYWORD);
      if (elseKeyword != null) {
        super.deleteChildInternal(elseKeyword);
      }
    }
    super.deleteChildInternal(child);
  }

  @Override
  public PsiStatement getThenBranch() {
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.THEN_BRANCH);
  }

  @Override
  public PsiStatement getElseBranch() {
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.ELSE_BRANCH);
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
  public PsiKeyword getElseElement() {
    return (PsiKeyword)findChildByRoleAsPsiElement(ChildRole.ELSE_KEYWORD);
  }

  @Override
  public void setElseBranch(@NotNull PsiStatement statement) throws IncorrectOperationException {
    PsiStatement elseBranch = getElseBranch();
    if (elseBranch != null) elseBranch.delete();
    PsiKeyword elseElement = getElseElement();
    if (elseElement != null) elseElement.delete();

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getProject());
    PsiIfStatement ifStatement = (PsiIfStatement)elementFactory.createStatementFromText("if (true) {} else {}", this);
    ifStatement.getElseBranch().replace(statement);

    addRange(ifStatement.getElseElement(), ifStatement.getLastChild());
  }
  @Override
  public void setThenBranch(@NotNull PsiStatement statement) throws IncorrectOperationException {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getProject());
    ASTNode keyword = findChildByRole(ChildRole.IF_KEYWORD);
    LOG.assertTrue(keyword != null);
    PsiIfStatement ifStatementPattern = (PsiIfStatement)elementFactory.createStatementFromText("if (){}", this);
    if (getLParenth() == null) {
      addAfter(ifStatementPattern.getLParenth(), keyword.getPsi());
    }
    if (getRParenth() == null) {
      PsiElement anchor = getCondition() == null ? getLParenth() : getCondition();
      addAfter(ifStatementPattern.getRParenth(), anchor);
    }
    PsiStatement thenBranch = getThenBranch();
    if (thenBranch == null) {
      addAfter(statement, getRParenth());
    }
    else {
      thenBranch.replace(statement);
    }
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.IF_KEYWORD:
        return findChildByType(IF_KEYWORD);

      case ChildRole.LPARENTH:
        return findChildByType(LPARENTH);

      case ChildRole.CONDITION:
        return findChildByType(EXPRESSION_BIT_SET);

      case ChildRole.RPARENTH:
        return findChildByType(RPARENTH);

      case ChildRole.THEN_BRANCH:
        return PsiImplUtil.findStatementChild(this);

      case ChildRole.ELSE_KEYWORD:
        return findChildByType(ELSE_KEYWORD);

      case ChildRole.ELSE_BRANCH:
        ASTNode elseKeyword = findChildByRole(ChildRole.ELSE_KEYWORD);
        if (elseKeyword == null) return null;
        for(ASTNode child = elseKeyword.getTreeNext(); child != null; child = child.getTreeNext()){
          if (child.getPsi() instanceof PsiStatement) return child;
        }
        return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == IF_KEYWORD) {
      return ChildRole.IF_KEYWORD;
    }
    else if (i == ELSE_KEYWORD) {
      return ChildRole.ELSE_KEYWORD;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.CONDITION;
      }
      else if (child.getPsi() instanceof PsiStatement) {
        if (findChildByRoleAsPsiElement(ChildRole.THEN_BRANCH) == child) {
          return ChildRole.THEN_BRANCH;
        }
        return ChildRole.ELSE_BRANCH;
      }
      else {
        return ChildRoleBase.NONE;
      }
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitIfStatement(this);
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
    PsiExpression condition = getCondition();
    if (condition != null) {
      PsiStatement thenBranch = getThenBranch();
      PsiStatement elseBranch = getElseBranch();
      if (lastParent == null) {
        if (state.get(PatternResolveState.KEY) == PatternResolveState.WHEN_NONE) return true;
        PsiScopeProcessor conditionProcessor;
        if (state.get(PatternResolveState.KEY) == PatternResolveState.WHEN_BOTH) {
          conditionProcessor = processor;
        }
        else {
          conditionProcessor = (element, s) -> {
            LOG.assertTrue(element instanceof PsiPatternVariable);
            final NameHint hint = processor.getHint(NameHint.KEY);
            if (hint != null && !((PsiPatternVariable)element).getName().equals(hint.getName(s))) {
              return true;
            }
            ControlFlow flow;
            try {
              flow = ControlFlowFactory.getControlFlow(
                this, new LocalsControlFlowPolicy(this), ControlFlowOptions.NO_CONST_EVALUATE);
            }
            catch (AnalysisCanceledException e) {
              return true;
            }
            boolean thenCompletesNormally = canCompleteNormally(thenBranch, flow);
            boolean elseCompletesNormally = canCompleteNormally(elseBranch, flow);
            if (thenCompletesNormally == elseCompletesNormally ||
                PatternResolveState.fromBoolean(thenCompletesNormally) !=
                PatternResolveState.stateAtParent((PsiPatternVariable)element, condition)) {
              return true;
            }
            return processor.execute(element, s);
          };
        }
        return condition.processDeclarations(conditionProcessor, PatternResolveState.WHEN_BOTH.putInto(state), null, place);
      }
      if (lastParent == thenBranch) {
        return condition.processDeclarations(processor, PatternResolveState.WHEN_TRUE.putInto(state), null, place);
      }
      if (lastParent == elseBranch) {
        return condition.processDeclarations(processor, PatternResolveState.WHEN_FALSE.putInto(state), null, place);
      }
    }
    return true;
  }

  private static boolean canCompleteNormally(PsiStatement branch, ControlFlow flow) {
    if (branch == null) return true;
    int startOffset = flow.getStartOffset(branch);
    int endOffset = flow.getEndOffset(branch);
    return startOffset != -1 && endOffset != -1 && ControlFlowUtil.canCompleteNormally(flow, startOffset, endOffset);
  }

  @Override
  public String toString() {
    return "PsiIfStatement";
  }
}
