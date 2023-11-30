// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PsiForeachStatementImpl extends PsiForeachStatementBaseImpl implements PsiForeachStatement, Constants {
  public PsiForeachStatementImpl() {
    super(FOREACH_STATEMENT);
  }

  @Override
  @NotNull
  public PsiParameter getIterationParameter() {
    return (PsiParameter)Objects.requireNonNull(findChildByRoleAsPsiElement(ChildRole.FOR_ITERATION_PARAMETER));
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

    PsiParameter parameter = getIterationParameter();
    if (parameter.isUnnamed()) return true;
    return processor.execute(parameter, state);
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
