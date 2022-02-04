// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.lang.jvm.JvmMethod;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.lang.jvm.actions.*;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UastContextKt;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.ExternalAnnotationsManager.AnnotationPlace;
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
  public boolean startInWriteAction() {
    return false;
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
    List<PsiElement> elements = ContainerUtil.map2List(taintAnalyzer.getNonMarkedElements(), e -> e.myNonMarked);
    markAsSafe(project, elements, ApplicationManager.getApplication().isHeadlessEnvironment());
  }

  public static void markAsSafe(@NotNull Project project, @NotNull Collection<PsiElement> toAnnotate, boolean isHeadlessMode) {
    AnnotationPlace place = getPlace(project, toAnnotate);
    if (place == AnnotationPlace.NEED_ASK_USER && !isHeadlessMode) {
      PsiModifierListOwner first = ContainerUtil.findInstance(toAnnotate, PsiModifierListOwner.class);
      if (first == null) return;
      ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
      if (!annotationsManager.hasConfiguredAnnotationRoot(first)) return;
      place = WriteAction.compute(() -> annotationsManager.chooseAnnotationsPlace(first));
    }
    if (place != AnnotationPlace.EXTERNAL && place != AnnotationPlace.IN_CODE) return;
    boolean annotateExternally = place == AnnotationPlace.EXTERNAL;
    annotate(project, toAnnotate, annotateExternally);
  }

  private static @Nullable AnnotationPlace getPlace(Project project, @NotNull Collection<PsiElement> toAnnotate) {
    ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    return toAnnotate.stream().reduce(null,
                                      (acc, e) -> acc == AnnotationPlace.NOWHERE ? acc : annotationsManager.chooseAnnotationsPlaceNoUi(e),
                                      MarkAsSafeFix::join);
  }

  private static AnnotationPlace join(@Nullable AnnotationPlace acc, @Nullable AnnotationPlace place) {
    if (place == acc) return place;
    if (acc == null || place == AnnotationPlace.NOWHERE) return place;
    return AnnotationPlace.NEED_ASK_USER;
  }

  private static void annotate(@NotNull Project project, @NotNull Collection<PsiElement> toAnnotate, boolean annotateExternally) {
    String title = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.mark.as.safe.text");
    if (annotateExternally) {
      Runnable annotateCommand = () -> {
        ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
        for (PsiElement element : toAnnotate) {
          PsiModifierListOwner owner = ObjectUtils.tryCast(element, PsiModifierListOwner.class);
          if (owner == null) continue;
          annotationsManager.annotateExternally(owner, DEFAULT_UNTAINTED_ANNOTATION, owner.getContainingFile(), null);
        }
      };
      CommandProcessor.getInstance().executeCommand(project, annotateCommand, title, null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);
      return;
    }
    WriteCommandAction.runWriteCommandAction(project, title, null, () -> {
      for (PsiElement element : toAnnotate) {
        if (!element.isValid()) continue;
        PsiFile psiFile = element.getContainingFile();
        if (psiFile == null) continue;
        if (!FileModificationService.getInstance().prepareFileForWrite(psiFile.getContainingFile())) continue;
        JvmMethod jvmMethod = ObjectUtils.tryCast(element, JvmMethod.class);
        if (jvmMethod != null) {
          JvmType returnType = jvmMethod.getReturnType();
          if (returnType == null) continue;
          ChangeTypeRequest changeTypeRequest = createTypeRequest(returnType);
          List<IntentionAction> actions = JvmElementActionFactories.createChangeTypeActions(jvmMethod, changeTypeRequest);
          if (actions.size() == 1) actions.get(0).invoke(project, null, psiFile);
          continue;
        }
        JvmParameter jvmParameter = ObjectUtils.tryCast(element, JvmParameter.class);
        if (jvmParameter != null) {
          ChangeTypeRequest changeTypeRequest = createTypeRequest(jvmParameter.getType());
          List<IntentionAction> actions = JvmElementActionFactories.createChangeTypeActions(jvmParameter, changeTypeRequest);
          if (actions.size() == 1) actions.get(0).invoke(project, null, psiFile);
          continue;
        }
        JvmModifiersOwner jvmModifiersOwner = ObjectUtils.tryCast(element, JvmModifiersOwner.class);
        if (jvmModifiersOwner == null) continue;
        AnnotationRequest request = AnnotationRequestsKt.annotationRequest(DEFAULT_UNTAINTED_ANNOTATION);
        List<IntentionAction> actions = JvmElementActionFactories.createAddAnnotationActions(jvmModifiersOwner, request);
        if (actions.size() == 1) actions.get(0).invoke(project, null, psiFile);
      }
    });
  }

  private static @NotNull ChangeTypeRequest createTypeRequest(@NotNull JvmType jvmType) {
    List<AnnotationRequest> annotations = StreamEx.of(jvmType.getAnnotations())
      .map(annotation -> AnnotationRequestsKt.annotationRequest(annotation))
      .append(AnnotationRequestsKt.annotationRequest(DEFAULT_UNTAINTED_ANNOTATION))
      .collect(Collectors.toList());
    return MethodRequestsKt.typeRequest(null, annotations);
  }
}
