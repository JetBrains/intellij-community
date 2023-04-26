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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.MethodMatcher;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.intellij.codeInspection.sourceToSink.TaintValueFactory.*;

public class SourceToSinkFlowInspection extends AbstractBaseJavaLocalInspectionTool {

  public List<String>
    taintedAnnotations = new ArrayList<>(List.of("javax.annotation.Tainted", "org.checkerframework.checker.tainting.qual.Tainted"));
  public List<String>
    untaintedAnnotations = new ArrayList<>(List.of("javax.annotation.Untainted", "org.checkerframework.checker.tainting.qual.Untainted"));

  public final MethodMatcher myUntaintedMethodMatcher = new MethodMatcher().finishDefault();
  public final List<String> myUntaintedFieldClasses = new ArrayList<>();
  public final List<String> myUntaintedFieldNames = new ArrayList<>();

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
      ),
      myUntaintedMethodMatcher.getTable(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.methods"))
        .prefix("myUntaintedMethodMatcher"),
      OptPane.table(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.fields"),
                    OptPane.column("myUntaintedFieldClasses",
                                   InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
                                   new JavaClassValidator()),
                    OptPane.column("myUntaintedFieldNames",
                                   JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.fields.name"))
      ));
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

    UntaintedContext context =
      new UntaintedContext(taintedAnnotations, untaintedAnnotations, firstAnnotation.orElse(null),
                                         myUntaintedMethodMatcher.getClassNames(), myUntaintedMethodMatcher.getMethodNamePatterns(),
                                         myUntaintedFieldClasses, myUntaintedFieldNames);


    TaintValueFactory factory = new TaintValueFactory(context);
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
            fixes = new LocalQuickFix[]{new MarkAsSafeFix(element, name, factory),
              new PropagateFix(element, name, factory, true)};
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

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    myUntaintedMethodMatcher.readSettings(element);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    super.writeSettings(element);
    myUntaintedMethodMatcher.writeSettings(element);
  }

  private static @Nullable String getName(@NotNull UResolvable uExpression) {
    PsiNamedElement namedElement = ObjectUtils.tryCast(uExpression.resolve(), PsiNamedElement.class);
    return namedElement == null ? null : namedElement.getName();
  }
}