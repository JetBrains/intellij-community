// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class JavaBracesUnwrapper extends JavaUnwrapper {
  public JavaBracesUnwrapper() {
    super(CodeInsightBundle.message("unwrap.braces"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e instanceof PsiBlockStatement && !belongsToControlStructures(e);
  }

  private static boolean belongsToControlStructures(PsiElement e) {
    PsiElement p = e.getParent();

    return p instanceof PsiIfStatement
           || p instanceof PsiLoopStatement
           || p instanceof PsiTryStatement
           || p instanceof PsiCatchSection
           || p instanceof PsiSwitchLabeledRuleStatement;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    context.extractFromBlockOrSingleStatement((PsiStatement)element, element);
    context.delete(element);
  }
}