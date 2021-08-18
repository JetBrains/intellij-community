// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.StringFlowUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;


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
        TaintAnalyzer taintAnalyzer = new TaintAnalyzer();
        TaintValue taintValue = taintAnalyzer.analyze(uExpression);
        taintValue = taintValue.join(contextValue);
        if (taintValue == TaintValue.UNTAINTED) return;
        String errorMessage = JvmAnalysisBundle.message(taintValue.getErrorMessage(annotationContext));
        LocalQuickFix fix = taintValue == TaintValue.UNKNOWN ? createFix(element, uExpression) : null;
        holder.registerProblem(element, errorMessage, fix);
      }
    };
  }

  private static @Nullable LocalQuickFix createFix(@NotNull PsiElement element, @NotNull UExpression uExpression) {
    UResolvable uResolvable = ObjectUtils.tryCast(uExpression, UResolvable.class);
    if (uResolvable == null) return null;
    PsiNamedElement namedElement = ObjectUtils.tryCast(uResolvable.resolve(), PsiNamedElement.class);
    if (namedElement == null) return null;
    String name = namedElement.getName();
    if (name == null) return null;
    return new MarkAsSafeFix(element, name);
  }
}