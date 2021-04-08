// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.lang.DfaInterceptor;
import com.intellij.codeInspection.dataFlow.lang.DfaLanguageSupport;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaDfaInstructionVisitor extends InstructionVisitor<PsiExpression> {
  protected JavaDfaInstructionVisitor() {
    this(new JavaDfaLanguageSupport(), null, false);
  }

  public JavaDfaInstructionVisitor(@Nullable DfaInterceptor<PsiExpression> interceptor) {
    this(new JavaDfaLanguageSupport(), interceptor, false);
  }

  protected JavaDfaInstructionVisitor(boolean stopAnalysisOnNpe) {
    super(new JavaDfaLanguageSupport(), null, stopAnalysisOnNpe);
  }

  public JavaDfaInstructionVisitor(@Nullable DfaInterceptor<PsiExpression> interceptor, boolean stopAnalysisOnNpe) {
    super(new JavaDfaLanguageSupport(), interceptor, stopAnalysisOnNpe);
  }

  protected JavaDfaInstructionVisitor(@NotNull DfaLanguageSupport<PsiExpression> support, @Nullable DfaInterceptor<PsiExpression> interceptor, boolean stopAnalysisOnNpe) {
    super(support, interceptor, stopAnalysisOnNpe);
  }
}
