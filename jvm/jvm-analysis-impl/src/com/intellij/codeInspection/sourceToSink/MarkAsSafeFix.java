// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.jvm.JvmMethod;
import com.intellij.lang.jvm.JvmModifiersOwner;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.lang.jvm.actions.*;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.ExternalAnnotationsManager.AnnotationPlace;
import static com.intellij.codeInspection.UntaintedAnnotationProvider.DEFAULT_UNTAINTED_ANNOTATION;

public class MarkAsSafeFix extends LocalQuickFixOnPsiElement {

  private final String myName;

  protected MarkAsSafeFix(@NotNull PsiElement element, @NotNull String name) {
    super(element);
    myName = name;
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
    List<PsiElement> elements = getElementsToMark(uExpression);
    if (elements == null) return;
    markAsSafe(project, elements, ApplicationManager.getApplication().isHeadlessEnvironment());
  }

  @Nullable
  private static List<PsiElement> getElementsToMark(@NotNull UExpression uExpression) {
    TaintAnalyzer taintAnalyzer = new TaintAnalyzer();
    TaintValue taintValue = taintAnalyzer.analyze(uExpression);
    if (taintValue != TaintValue.UNKNOWN) return null;
    return ContainerUtil.map2List(taintAnalyzer.getNonMarkedElements(), e -> e.myNonMarked);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    UExpression uExpression = UastContextKt.toUElementOfExpectedTypes(previewDescriptor.getStartElement(),
                                                                      UCallExpression.class, UReferenceExpression.class);
    if (uExpression == null) return IntentionPreviewInfo.EMPTY;
    List<PsiElement> toAnnotate = getElementsToMark(uExpression);
    if (toAnnotate == null) return IntentionPreviewInfo.EMPTY;
    PsiElement sourcePsi = uExpression.getSourcePsi();
    if (sourcePsi == null) return IntentionPreviewInfo.EMPTY;
    PsiFile file = sourcePsi.getContainingFile();
    Set<PsiFile> filesToAnnotate = ContainerUtil.map2Set(toAnnotate, e -> e.getContainingFile());
    if (ContainerUtil.exists(filesToAnnotate, f -> f != file)) {
      String fileNames = StringUtil.join(filesToAnnotate, f -> f.getName(), ", ");
      String message = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.preview.multiple.files", fileNames);
      return new IntentionPreviewInfo.Html(message);
    }
    if (ContainerUtil.exists(toAnnotate, e -> e.getContainingFile() != file)) return IntentionPreviewInfo.EMPTY;
    annotateInCode(project, toAnnotate);
    return IntentionPreviewInfo.DIFF;
  }

  public static void markAsSafe(@NotNull Project project, @NotNull Collection<PsiElement> toAnnotate, boolean isHeadlessMode) {
    AnnotationPlace place = getPlace(project, toAnnotate);
    if (place == AnnotationPlace.NEED_ASK_USER && !isHeadlessMode) {
      PsiModifierListOwner first = ContainerUtil.findInstance(toAnnotate, PsiModifierListOwner.class);
      if (first == null) return;
      ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
      place = annotationsManager.chooseAnnotationsPlace(first);
    }
    if (place != AnnotationPlace.EXTERNAL && place != AnnotationPlace.IN_CODE) return;
    boolean annotateExternally = place == AnnotationPlace.EXTERNAL;
    annotate(project, toAnnotate, annotateExternally);
  }

  private static @Nullable AnnotationPlace getPlace(Project project, @NotNull Collection<PsiElement> toAnnotate) {
    ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    return toAnnotate.stream().reduce(null,
                                      (acc, e) -> acc == AnnotationPlace.NOWHERE
                                                  ? acc
                                                  : join(acc, annotationsManager.chooseAnnotationsPlaceNoUi(e)),
                                      MarkAsSafeFix::join);
  }

  private static AnnotationPlace join(@Nullable AnnotationPlace acc, @Nullable AnnotationPlace place) {
    if (place == acc) return place;
    if (acc == null || place == AnnotationPlace.NOWHERE) return place;
    if (place == AnnotationPlace.EXTERNAL && acc == AnnotationPlace.IN_CODE ||
        place == AnnotationPlace.IN_CODE && acc == AnnotationPlace.EXTERNAL) {
      return AnnotationPlace.EXTERNAL;
    }
    return AnnotationPlace.NEED_ASK_USER;
  }

  private static void annotate(@NotNull Project project, @NotNull Collection<PsiElement> toAnnotate, boolean annotateExternally) {
    String title = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.mark.as.safe.command.name");
    if (annotateExternally) {
      CommandProcessor.getInstance().executeCommand(project, () -> annotateExternally(project, toAnnotate),
                                                    title, null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);
    }
    else {
      FileModificationService modificationService = FileModificationService.getInstance();
      boolean canModifyFiles = toAnnotate.stream()
        .map(e -> e.getContainingFile())
        .allMatch(f -> modificationService.prepareFileForWrite(f));
      if (!canModifyFiles) return;
      WriteCommandAction.runWriteCommandAction(project, title, null, () -> {
        annotateInCode(project, toAnnotate);
      });
    }
  }

  private static void annotateExternally(@NotNull Project project, @NotNull Collection<PsiElement> toAnnotate) {
    ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    for (PsiElement element : toAnnotate) {
      PsiModifierListOwner owner = ObjectUtils.tryCast(element, PsiModifierListOwner.class);
      if (owner == null) continue;
      try {
        annotationsManager.annotateExternally(owner, DEFAULT_UNTAINTED_ANNOTATION, owner.getContainingFile(), null);
      } catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {
        return;
      }
    }
  }

  private static void annotateInCode(@NotNull Project project, @NotNull Collection<PsiElement> toAnnotate) {
    for (PsiElement element : toAnnotate) {
      if (!element.isValid()) continue;
      PsiFile psiFile = element.getContainingFile();
      if (psiFile == null) continue;
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
  }

  private static @NotNull ChangeTypeRequest createTypeRequest(@NotNull JvmType jvmType) {
    List<AnnotationRequest> annotations = StreamEx.of(jvmType.getAnnotations())
      .map(annotation -> AnnotationRequestsKt.annotationRequest(annotation))
      .append(AnnotationRequestsKt.annotationRequest(DEFAULT_UNTAINTED_ANNOTATION))
      .collect(Collectors.toList());
    return MethodRequestsKt.typeRequest(null, annotations);
  }
}
