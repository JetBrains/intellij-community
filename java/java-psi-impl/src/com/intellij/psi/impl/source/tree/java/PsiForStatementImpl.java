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
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiForStatementImpl extends PsiLoopStatementImpl implements PsiForStatement, Constants {
  private static final Logger LOG = Logger.getInstance(PsiForStatementImpl.class);

  public PsiForStatementImpl() {
    super(FOR_STATEMENT);
  }

  @Override
  public PsiStatement getInitialization(){
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.FOR_INITIALIZATION);
  }

  @Override
  public PsiExpression getCondition(){
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  @Override
  public PsiStatement getUpdate(){
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.FOR_UPDATE);
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

      case ChildRole.FOR_KEYWORD:
        return findChildByType(FOR_KEYWORD);

      case ChildRole.LPARENTH:
        return findChildByType(LPARENTH);

      case ChildRole.FOR_INITIALIZATION:
        final ASTNode initialization = PsiImplUtil.findStatementChild(this);
        // should be inside parens
        ASTNode paren = findChildByRole(ChildRole.LPARENTH);
        for(ASTNode child = paren; child != null; child = child.getTreeNext()){
          if (child == initialization) return initialization;
          if (child.getElementType() == RPARENTH) return null;
        }
        return null;

      case ChildRole.CONDITION:
        return findChildByType(EXPRESSION_BIT_SET);

      case ChildRole.FOR_SEMICOLON:
        return findChildByType(SEMICOLON);

      case ChildRole.FOR_UPDATE:
        ASTNode semicolon = findChildByRole(ChildRole.FOR_SEMICOLON);
        for(ASTNode child = semicolon; child != null; child = child.getTreeNext()){
          if (child.getPsi() instanceof PsiStatement) {
            return child;
          }
          if (child.getElementType() == RPARENTH) break;
        }
        return null;

      case ChildRole.RPARENTH:
        return findChildByType(RPARENTH);

      case ChildRole.LOOP_BODY:
        ASTNode rparenth = findChildByRole(ChildRole.RPARENTH);
        for(ASTNode child = rparenth; child != null; child = child.getTreeNext()){
          if (child.getPsi() instanceof PsiStatement) {
            return child;
          }
        }
        return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == FOR_KEYWORD) {
      return ChildRole.FOR_KEYWORD;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else if (i == SEMICOLON) {
      return ChildRole.FOR_SEMICOLON;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.CONDITION;
      }
      else if (child.getPsi() instanceof PsiStatement) {
        int role = getChildRole(child, ChildRole.FOR_INITIALIZATION);
        if (role != ChildRoleBase.NONE) return role;
        role = getChildRole(child, ChildRole.FOR_UPDATE);
        if (role != ChildRoleBase.NONE) return role;
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
      ((JavaElementVisitor)visitor).visitForStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString(){
    return "PsiForStatement";
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (getChildRole(child) == ChildRole.FOR_INITIALIZATION) {
      replaceChildInternal(child, (TreeElement)JavaPsiFacade.getElementFactory(getProject()).createStatementFromText(";", null));
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    ElementClassHint elementClassHint = processor.getHint(ElementClassHint.KEY);
    if (elementClassHint != null && !elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) return true;
    if (lastParent == null) {
      // Only patterns may introduce variables visible after loop
      return PsiWhileStatementImpl.processDeclarationsInLoopCondition(processor, state, place, this);
    }
    else if (lastParent.getParent() != this) {
      // Parent element should not see our vars
      return true;
    }
    PsiStatement initialization = getInitialization();
    if (initialization != null && lastParent != initialization) {
      if (!initialization.processDeclarations(processor, state, null, place)) return false;
    }
    PsiExpression condition = getCondition();
    if (condition != null && (lastParent == getBody() || lastParent == getUpdate())) {
      return condition.processDeclarations(processor, PatternResolveState.WHEN_TRUE.putInto(state), null, place);
    }
    return true;
  }
}
