package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.psi.PsiElement;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface EvaluatorBuilder {
  ExpressionEvaluator build(TextWithImports text, PsiElement contextElement) throws EvaluateException;

  ExpressionEvaluator build(PsiElement codeFragment, final PsiElement contextElement) throws EvaluateException;
}
