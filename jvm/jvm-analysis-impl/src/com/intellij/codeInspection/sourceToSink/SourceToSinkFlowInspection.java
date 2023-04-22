// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.StringFlowUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SourceToSinkFlowInspection extends AbstractBaseJavaLocalInspectionTool {

  public List<String>
    taintedAnnotations = new ArrayList<>(List.of("javax.annotation.Tainted", "org.checkerframework.checker.tainting.qual.Tainted"));
  public List<String>
    untaintedAnnotations = new ArrayList<>(List.of("javax.annotation.Untainted", "org.checkerframework.checker.tainting.qual.Untainted"));

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.stringList("taintedAnnotations",
                         JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.tainted.annotations"),
                         new JavaClassValidator().annotationsOnly()
      ),
      OptPane.stringList("untaintedAnnotations",
                         JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.annotations"),
                         new JavaClassValidator().annotationsOnly()
      )
    );
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(holder.getProject());
    Optional<String> firstAnnotation = untaintedAnnotations.stream()
      .filter(ann -> JavaPsiFacade.getInstance(holder.getProject()).findClass(ann, scope) != null)
      .findFirst();
    if (firstAnnotation.isEmpty()) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    TaintValueFactory factory =
      new TaintValueFactory(taintedAnnotations, untaintedAnnotations, firstAnnotation.orElse(null));
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        UExpression uExpression = UastContextKt.toUElementOfExpectedTypes(element, UCallExpression.class, UReferenceExpression.class);
        if (uExpression == null) return;
        PsiType expressionType = uExpression.getExpressionType();
        if (expressionType == null || !expressionType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return;
        UExpression usage = StringFlowUtil.goUp(uExpression, true, factory);
        AnnotationContext annotationContext = AnnotationContext.fromExpression(usage);
        TaintValue contextValue = factory.of(annotationContext);
        if (contextValue != TaintValue.UNTAINTED) return;
        TaintAnalyzer taintAnalyzer = new TaintAnalyzer(factory);
        TaintValue taintValue = taintAnalyzer.analyze(uExpression);
        taintValue = taintValue.join(contextValue);
        if (taintValue == TaintValue.UNTAINTED) return;
        String errorMessage = JvmAnalysisBundle.message(taintValue.getErrorMessage(annotationContext));
        LocalQuickFix[] fixes = null;
        if (taintValue == TaintValue.UNKNOWN) {
          String name = getName((UResolvable)uExpression);
          if (name != null) {
            fixes = new LocalQuickFix[]{new MarkAsSafeFix(element, name, factory), new PropagateFix(element, name, factory)};
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