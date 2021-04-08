// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInspection.dataFlow.StandardInstructionVisitor;
import com.intellij.codeInspection.dataFlow.lang.DfaInterceptor;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.Nullable;

public class JavaDfaInstructionVisitor extends StandardInstructionVisitor<PsiExpression> {
  protected JavaDfaInstructionVisitor() {
    super(new JavaDfaLanguageSupport(), null);
  }

  public JavaDfaInstructionVisitor(@Nullable DfaInterceptor<PsiExpression> interceptor) {
    super(new JavaDfaLanguageSupport(), interceptor);
  }

  protected JavaDfaInstructionVisitor(boolean stopAnalysisOnNpe) {
    super(new JavaDfaLanguageSupport(), null, stopAnalysisOnNpe);
  }

  public JavaDfaInstructionVisitor(@Nullable DfaInterceptor<PsiExpression> interceptor, boolean stopAnalysisOnNpe) {
    super(new JavaDfaLanguageSupport(), interceptor, stopAnalysisOnNpe);
  }
}
