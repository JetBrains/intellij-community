// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.unwrap;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class JavaSwitchExpressionUnwrapper extends JavaUnwrapper {

  public JavaSwitchExpressionUnwrapper() {
    super("Unwrap 'switch' expression");
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e instanceof PsiSwitchLabeledRuleStatement
           && ((PsiSwitchLabeledRuleStatement)e).getBody() instanceof PsiExpressionStatement
           && ((PsiSwitchLabeledRuleStatement)e).getEnclosingSwitchBlock() instanceof PsiSwitchExpression;
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    PsiSwitchLabeledRuleStatement rule = (PsiSwitchLabeledRuleStatement)e;
    return rule.getEnclosingSwitchBlock();
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) {
    PsiSwitchLabeledRuleStatement rule = (PsiSwitchLabeledRuleStatement)element;
    PsiSwitchBlock block = rule.getEnclosingSwitchBlock();
    PsiStatement body = rule.getBody();
    assert body instanceof PsiExpressionStatement;
    context.extractElement(((PsiExpressionStatement)body).getExpression(), block);
    context.deleteExactly(block);
  }
}
