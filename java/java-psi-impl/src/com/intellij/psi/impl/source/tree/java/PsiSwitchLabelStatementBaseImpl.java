// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightExpressionList;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    PsiCaseLabelElementList elementList = getCaseLabelElementList();
    if (elementList == null) return null;
    PsiExpression[] expressions = PsiTreeUtil.getChildrenOfType(elementList, PsiExpression.class);
    expressions = expressions != null ? expressions : PsiExpression.EMPTY_ARRAY;
    return new LightExpressionList(getManager(), getLanguage(), expressions, elementList, elementList.getTextRange());
  }

  @Override
  public @Nullable PsiExpression getGuardExpression() {
    return PsiTreeUtil.getChildOfType(this, PsiExpression.class);
  }

  @Nullable
  @Override
  public PsiSwitchBlock getEnclosingSwitchBlock() {
    PsiElement codeBlock = getParent();
    if (codeBlock != null) {
      PsiElement switchBlock = codeBlock.getParent();
      if (switchBlock instanceof PsiSwitchBlock) {
        return (PsiSwitchBlock)switchBlock;
      }
    }
    return null;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent instanceof PsiCaseLabelElementList) {
      PsiSwitchBlock switchStatement = getEnclosingSwitchBlock();
      if (switchStatement != null) {
        PsiExpression expression = switchStatement.getExpression();
        if (expression != null) {
          PsiType type = expression.getType();
          if (type instanceof PsiClassType) {
            PsiClass aClass = ((PsiClassType)type).resolve();
            if (aClass != null) {
              if (!aClass.processDeclarations(new FilterScopeProcessor(ElementClassFilter.ENUM_CONST, processor), state, this, place)) {
                return false;
              }
            }
          }
        }
      }
    }

    return true;
  }

  @Override
  public @Nullable PsiCaseLabelElementList getCaseLabelElementList() {
    return PsiTreeUtil.getChildOfType(this, PsiCaseLabelElementList.class);
  }

  protected boolean processPatternVariables(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, @NotNull PsiElement place) {
    final PsiCaseLabelElementList patternsInCaseLabel = getCaseLabelElementList();
    if (patternsInCaseLabel == null) return true;
    if (!patternsInCaseLabel.processDeclarations(processor, state, null, place)) return false;

    PsiExpression guardExpression = getGuardExpression();
    if (guardExpression != null) {
      return guardExpression.processDeclarations(processor, PatternResolveState.WHEN_TRUE.putInto(state), null, place);
    }
    return true;
  }
}