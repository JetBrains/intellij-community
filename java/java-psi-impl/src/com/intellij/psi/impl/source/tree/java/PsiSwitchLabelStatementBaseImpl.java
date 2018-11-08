// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public abstract class PsiSwitchLabelStatementBaseImpl extends CompositePsiElement implements PsiSwitchLabelStatementBase {
  protected PsiSwitchLabelStatementBaseImpl(IElementType type) {
    super(type);
  }

  @Override
  public boolean isDefaultCase() {
    return findChildByType(JavaTokenType.DEFAULT_KEYWORD) != null;
  }

  @Override
  public PsiExpressionList getCaseValues() {
    return (PsiExpressionList)findPsiChildByType(JavaElementType.EXPRESSION_LIST);
  }

  @Override
  public PsiSwitchStatement getEnclosingSwitchStatement() {
    PsiElement block = getParent();
    if (block != null) {
      PsiElement statement = block.getParent();
      if (statement instanceof PsiSwitchStatement) {
        return (PsiSwitchStatement)statement;
      }
    }
    return null;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent != null) {
      PsiSwitchStatement switchStatement = getEnclosingSwitchStatement();
      if (switchStatement != null) {
        PsiExpression expression = switchStatement.getExpression();
        if (expression != null) {
          PsiType type = expression.getType();
          if (type instanceof PsiClassType) {
            PsiClass aClass = ((PsiClassType)type).resolve();
            if (aClass != null) {
              aClass.processDeclarations(new FilterScopeProcessor(ElementClassFilter.ENUM_CONST, processor), state, this, place);
            }
          }
        }
      }
    }

    return true;
  }
}