// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author dsl
 */
public class PsiForeachStatementImpl extends PsiLoopStatementImpl implements PsiForeachStatement, Constants {
  private static final Logger LOG = Logger.getInstance(PsiForeachStatementImpl.class);
  public PsiForeachStatementImpl() {
    super(FOREACH_STATEMENT);
  }

  @Override
  @NotNull
  public PsiParameter getIterationParameter() {
    PsiParameter parameter = (PsiParameter)findChildByRoleAsPsiElement(ChildRole.FOR_ITERATION_PARAMETER);
    if (parameter == null) {
      LOG.error("getIterationParameter is used when forEach element contains pattern. Migrate to getIterationDeclaration()");
      return new LightParameter("__pattern_replacement__", PsiType.BOOLEAN, this);
    }
    return parameter;
  }

  @Override
  @NotNull
  public PsiForeachDeclarationElement getIterationDeclaration() {
    PsiParameter parameter = (PsiParameter)findChildByRoleAsPsiElement(ChildRole.FOR_ITERATION_PARAMETER);
    if (parameter != null) {
      return parameter;
    } else {
      return Objects.requireNonNull(PsiTreeUtil.getChildOfType(this, PsiPattern.class));
    }
  }

  @Override
  public PsiExpression getIteratedValue() {
    return (PsiExpression) findChildByRoleAsPsiElement(ChildRole.FOR_ITERATED_VALUE);
  }

  @Override
  public PsiStatement getBody() {
    return (PsiStatement) findChildByRoleAsPsiElement(ChildRole.LOOP_BODY);
  }

  @Override
  @NotNull
  public PsiJavaToken getLParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.LPARENTH);
  }

  @Override
  public PsiJavaToken getRParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.RPARENTH);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));

    switch(role) {
      case ChildRole.LOOP_BODY:
        return PsiImplUtil.findStatementChild(this);

      case ChildRole.FOR_ITERATED_VALUE:
        return findChildByType(EXPRESSION_BIT_SET);

      case ChildRole.FOR_KEYWORD:
        return getFirstChildNode();

      case ChildRole.LPARENTH:
        return findChildByType(LPARENTH);

      case ChildRole.RPARENTH:
        return findChildByType(RPARENTH);

      case ChildRole.FOR_ITERATION_PARAMETER:
        return findChildByType(PARAMETER);

      case ChildRole.COLON:
        return findChildByType(COLON);

      default:
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
    else if (i == PARAMETER) {
      return ChildRole.FOR_ITERATION_PARAMETER;
    }
    else if (i == COLON) {
      return ChildRole.COLON;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.FOR_ITERATED_VALUE;
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
  public String toString() {
    return "PsiForeachStatement";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null || lastParent.getParent() != this || lastParent == getIteratedValue())
      // Parent element should not see our vars
      return true;

    PsiForeachDeclarationElement iterationDeclaration = getIterationDeclaration();
    if (iterationDeclaration instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)iterationDeclaration;
      return processor.execute(parameter, state);
    } else if (iterationDeclaration instanceof PsiDeconstructionPattern) {
      PsiDeconstructionPattern deconstructionPattern = (PsiDeconstructionPattern)iterationDeclaration;
      return deconstructionPattern.processDeclarations(processor, state, lastParent, place);
    } {
      return false;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitForeachStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
