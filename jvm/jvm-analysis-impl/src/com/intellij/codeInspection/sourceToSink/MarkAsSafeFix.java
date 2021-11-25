// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.lang.jvm.JvmMethod;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.lang.jvm.actions.*;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UastContextKt;

import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.UntaintedAnnotationProvider.DEFAULT_UNTAINTED_ANNOTATION;

public class MarkAsSafeFix extends LocalQuickFixOnPsiElement {

  private final String myName;

  protected MarkAsSafeFix(@NotNull PsiElement element, @NotNull String name) {
    super(element);
    this.myName = name;
  }

  @Override
  public @NotNull String getText() {
    return JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.mark.as.safe.text", myName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.mark.as.safe.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    UExpression uExpression = UastContextKt.toUElementOfExpectedTypes(startElement, UCallExpression.class, UReferenceExpression.class);
    if (uExpression == null) return;
    TaintAnalyzer taintAnalyzer = new TaintAnalyzer();
    TaintValue taintValue = taintAnalyzer.analyze(uExpression);
    if (taintValue != TaintValue.UNKNOWN) return;
    taintAnalyzer.getNonMarkedElements().forEach(owner -> markAsSafe(project, owner.myNonMarked));
  }

  public static void markAsSafe(@NotNull Project project, @NotNull PsiElement element) {
    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null || !psiFile.isWritable()) return;
    JvmMethod jvmMethod = ObjectUtils.tryCast(element, JvmMethod.class);
    if (jvmMethod != null) {
      JvmType returnType = jvmMethod.getReturnType();
      if (returnType == null) return;
      ChangeTypeRequest changeTypeRequest = createTypeRequest(returnType);
      List<IntentionAction> actions = JvmElementActionFactories.createChangeTypeActions(jvmMethod, changeTypeRequest);
      if (actions.size() == 1) actions.get(0).invoke(project, null, psiFile);
      return;
    }
    JvmParameter jvmParameter = ObjectUtils.tryCast(element, JvmParameter.class);
    if (jvmParameter != null) {
      ChangeTypeRequest changeTypeRequest = createTypeRequest(jvmParameter.getType());
      List<IntentionAction> actions = JvmElementActionFactories.createChangeTypeActions(jvmParameter, changeTypeRequest);
      if (actions.size() == 1) actions.get(0).invoke(project, null, psiFile);
      return;
    }
    JvmModifiersOwner jvmModifiersOwner = ObjectUtils.tryCast(element, JvmModifiersOwner.class);
    if (jvmModifiersOwner == null) return;
    AnnotationRequest request = AnnotationRequestsKt.annotationRequest(DEFAULT_UNTAINTED_ANNOTATION);
    List<IntentionAction> actions = JvmElementActionFactories.createAddAnnotationActions(jvmModifiersOwner, request);
    if (actions.size() == 1) actions.get(0).invoke(project, null, psiFile);
  }

  private static @NotNull ChangeTypeRequest createTypeRequest(@NotNull JvmType jvmType) {
    List<AnnotationRequest> annotations = StreamEx.of(jvmType.getAnnotations())
      .map(annotation -> AnnotationRequestsKt.annotationRequest(annotation))
      .append(AnnotationRequestsKt.annotationRequest(DEFAULT_UNTAINTED_ANNOTATION))
      .collect(Collectors.toList());
    return MethodRequestsKt.typeRequest(null, annotations);
  }
}
