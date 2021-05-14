// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory Shrago
 */
public class PsiParameterPattern extends PsiModifierListOwnerPattern<PsiParameter, PsiParameterPattern> {

  protected PsiParameterPattern() {
    super(PsiParameter.class);
  }

  public PsiParameterPattern ofMethod(int index, ElementPattern pattern) {
    return with(new PatternConditionPlus<PsiParameter, PsiMethod>("ofMethod", pattern) {
      @Override
      public boolean processValues(PsiParameter t,
                                   ProcessingContext context,
                                   PairProcessor<? super PsiMethod, ? super ProcessingContext> processor) {
        PsiElement scope = t.getDeclarationScope();
        if (!(scope instanceof PsiMethod)) return true;
        return processor.process((PsiMethod)scope, context);
      }

      @Override
      public boolean accepts(@NotNull final PsiParameter t, final ProcessingContext context) {
        if (!super.accepts(t, context)) return false;
        PsiMethod psiMethod = (PsiMethod)t.getDeclarationScope();

        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
        if (index < 0 || index >= parameters.length || !t.equals(parameters[index])) return false;
        return true;
      }
    });
  }

  public PsiParameterPattern ofMethod(ElementPattern<?> pattern) {
    return with(new PatternConditionPlus<PsiParameter, PsiMethod>("ofMethod", pattern) {
      @Override
      public boolean processValues(PsiParameter t,
                                   ProcessingContext context,
                                   PairProcessor<? super PsiMethod, ? super ProcessingContext> processor) {
        PsiElement scope = t.getDeclarationScope();
        if (!(scope instanceof PsiMethod)) return true;
        return processor.process((PsiMethod)scope, context);
      }
    });
  }
}
