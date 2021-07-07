// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.StringFlowUtil;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.Objects;

public class SourceToSinkFlowInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        UExpression uExpression = UastContextKt.toUElementOfExpectedTypes(element, UCallExpression.class, UReferenceExpression.class);
        if (uExpression == null) return;
        UExpression usage = StringFlowUtil.goUp(uExpression, true, TaintValueFactory.INSTANCE);
        AnnotationContext annotationContext = AnnotationContext.fromExpression(usage);
        TaintValue contextValue = TaintValueFactory.INSTANCE.of(annotationContext);
        if (contextValue != TaintValue.UNTAINTED) return;
        TaintValue taintValue = TaintAnalyzer.getTaintValue(uExpression);
        if (taintValue == null) return;
        taintValue = taintValue.join(contextValue);
        if (taintValue == TaintValue.UNTAINTED) return;
        String errorMessage = Objects.requireNonNull(taintValue.getErrorMessage());
        LocalQuickFix fix = null;
        if (taintValue == TaintValue.UNKNOWN) {
          PsiModifierListOwner target = getTarget(uExpression);
          if (target == null) return;
          fix = new UntaintedAnnotationProvider().createFix(target);
        }
        holder.registerProblem(element, JvmAnalysisBundle.message(errorMessage), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix);
      }
    };
  }
  
  private static @Nullable PsiModifierListOwner getTarget(@NotNull UExpression expression) {
    if (!(expression instanceof UResolvable)) return null;
    return ObjectUtils.tryCast(((UResolvable)expression).resolve(), PsiModifierListOwner.class);
  }
}