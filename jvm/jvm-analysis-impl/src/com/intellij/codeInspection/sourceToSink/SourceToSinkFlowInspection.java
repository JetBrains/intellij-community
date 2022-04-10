// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.UntaintedAnnotationProvider;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.StringFlowUtil;
import com.intellij.codeInspection.sourceToSink.propagate.PropagateFix;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;


public class SourceToSinkFlowInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (JavaPsiFacade.getInstance(holder.getProject()).findClass(UntaintedAnnotationProvider.DEFAULT_TAINTED_ANNOTATION,
                                                                 holder.getFile().getResolveScope()) == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        UExpression uExpression = UastContextKt.toUElementOfExpectedTypes(element, UCallExpression.class, UReferenceExpression.class);
        if (uExpression == null) return;
        PsiType expressionType = uExpression.getExpressionType();
        if (expressionType == null || !expressionType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return;
        UExpression usage = StringFlowUtil.goUp(uExpression, true, TaintValueFactory.INSTANCE);
        AnnotationContext annotationContext = AnnotationContext.fromExpression(usage);
        TaintValue contextValue = TaintValueFactory.INSTANCE.of(annotationContext);
        if (contextValue != TaintValue.UNTAINTED) return;
        TaintAnalyzer taintAnalyzer = new TaintAnalyzer();
        TaintValue taintValue = taintAnalyzer.analyze(uExpression);
        taintValue = taintValue.join(contextValue);
        if (taintValue == TaintValue.UNTAINTED) return;
        String errorMessage = JvmAnalysisBundle.message(taintValue.getErrorMessage(annotationContext));
        LocalQuickFix[] fixes = null;
        if (taintValue == TaintValue.UNKNOWN) {
          String name = getName((UResolvable)uExpression);
          if (name != null) {
            fixes = new LocalQuickFix[]{new MarkAsSafeFix(element, name), new PropagateFix(element, name)};
          }
        }
        holder.registerProblem(element, errorMessage, fixes);
      }
    };
  }

  @Override
  public @NotNull String getID() {
    return "tainting";
  }

  private static @Nullable String getName(@NotNull UResolvable uExpression) {
    PsiNamedElement namedElement = ObjectUtils.tryCast(uExpression.resolve(), PsiNamedElement.class);
    return namedElement == null ? null : namedElement.getName();
  }
}