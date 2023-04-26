// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.lang.jvm.*;
import com.intellij.lang.jvm.actions.*;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.ExternalAnnotationsManager.AnnotationPlace;
import static com.intellij.codeInspection.sourceToSink.TaintValueFactory.UntaintedContext;

public class MarkAsSafeFix extends LocalQuickFixOnPsiElement {

  @NotNull
  private final String myName;

  @NotNull
  private final TaintValueFactory myTaintValueFactory;

  protected MarkAsSafeFix(@NotNull PsiElement element,
                          @NotNull String name,
                          @NotNull TaintValueFactory taintValueFactory) {
    super(element);
    myName = name;
    this.myTaintValueFactory = taintValueFactory;
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
    markAsSafe(project, elements, myTaintValueFactory);
  }

  @Nullable
  private List<PsiElement> getElementsToMark(@NotNull UExpression uExpression) {
    TaintAnalyzer taintAnalyzer = new TaintAnalyzer(myTaintValueFactory);
    TaintValue taintValue = taintAnalyzer.analyze(uExpression);
    if (taintValue != TaintValue.UNKNOWN) return null;
    return ContainerUtil.map(taintAnalyzer.getNonMarkedElements(), e -> e.myNonMarked);
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
    String message = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.preview");
    if (ContainerUtil.exists(toAnnotate, e -> fileToAnnotate(e) != file)) {
      return new IntentionPreviewInfo.Html(message);
    }
    ArrayList<PsiElement> ignoredElements = new ArrayList<>();
    annotateInCode(project, toAnnotate, myTaintValueFactory, true, ignoredElements);
    if (ignoredElements.isEmpty()) {
      return IntentionPreviewInfo.DIFF;
    }
    return new IntentionPreviewInfo.Html(message);
  }

  public static void markAsSafe(@NotNull Project project, @NotNull Collection<PsiElement> toAnnotate,
                                @NotNull TaintValueFactory taintValueFactory) {
    Map<PsiElement, AnnotationPlace> placedElements = getPlace(project, toAnnotate);
    annotate(project, placedElements, taintValueFactory);
  }

  private static @NotNull Map<PsiElement, AnnotationPlace> getPlace(Project project, @NotNull Collection<PsiElement> toAnnotate) {
    ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    return StreamEx.of(toAnnotate).toMap(e -> e, e -> annotationsManager.chooseAnnotationsPlaceNoUi(e));
  }

  private static void annotate(@NotNull Project project,
                               @NotNull Map<PsiElement, AnnotationPlace> placedElement,
                               @NotNull TaintValueFactory taintValueFactory) {
    MultiMap<PsiElement, String> problems = new MultiMap<>();
    ArrayList<PsiElement> toAnnotate = new ArrayList<>();
    for (Map.Entry<PsiElement, AnnotationPlace> entry : placedElement.entrySet()) {
      PsiElement element = entry.getKey();
      AnnotationPlace annotationPlace = entry.getValue();
      if (annotationPlace != AnnotationPlace.IN_CODE) {
        String message;
        if (element instanceof JvmField || element instanceof JvmMethod) {
          message = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.config", getRepresentText(element));
        }
        else {
          message = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.impossible", getRepresentText(element));
        }
        problems.put(element, List.of(message));
      }
      else {
        toAnnotate.add(element);
      }
    }
    ArrayList<PsiElement> ignoredElements = new ArrayList<>();
    annotateInCode(project, toAnnotate, taintValueFactory, false, ignoredElements);
    for (PsiElement element : ignoredElements) {
      if (element instanceof JvmField || element instanceof JvmMethod) {
        placedElement.put(element, AnnotationPlace.EXTERNAL);
        String message = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.config", getRepresentText(element));
        problems.put(element, List.of(message));
      }
      else {
        String message = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.impossible", getRepresentText(element));
        problems.put(element, List.of(message));
      }
    }
    if (!problems.isEmpty()) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(project, problems);
      if (!conflictsDialog.showAndGet()) {
        return;
      }
    }
    run(project, placedElement, taintValueFactory);
  }

  @NotNull
  private static String getRepresentText(@NotNull PsiElement element) {
    if (element instanceof JvmNamedElement jvmNamedElement && jvmNamedElement.getName() != null) {
      return jvmNamedElement.getName();
    }
    return element.getText();
  }

  private static void run(@NotNull Project project,
                          @NotNull Map<PsiElement, AnnotationPlace> placedElement,
                          @NotNull TaintValueFactory taintValueFactory) {
    String title = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.mark.as.safe.command.name");
    ArrayList<PsiElement> toAnnotate = new ArrayList<>();
    ArrayList<PsiElement> toConfig = new ArrayList<>();
    for (Map.Entry<PsiElement, AnnotationPlace> entry : placedElement.entrySet()) {
      AnnotationPlace annotationPlace = entry.getValue();
      if (annotationPlace == AnnotationPlace.IN_CODE) {
        toAnnotate.add(entry.getKey());
      }
      if (annotationPlace == AnnotationPlace.EXTERNAL || annotationPlace == AnnotationPlace.NEED_ASK_USER) {
        toConfig.add(entry.getKey());
      }
    }
    PsiFile[] files = filesToAnnotate(toAnnotate);
    WriteCommandAction.runWriteCommandAction(project, title, null, () -> {
      annotateInCode(project, toAnnotate, taintValueFactory, true, new ArrayList<>());
      addToConfig(project, toConfig, taintValueFactory.getContext());
    }, files);
  }

  private static void addToConfig(@NotNull Project project, @NotNull List<PsiElement> config, @NotNull UntaintedContext context) {
    UntaintedContext previousContext = context.copy();
    InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, model -> {
      for (PsiElement element : config) {
        if (element instanceof JvmMethod jvmMethod) {
          JvmClass containingClass = jvmMethod.getContainingClass();
          if (containingClass == null) continue;
          context.methodClass().add(containingClass.getQualifiedName());
          context.methodPatterns().add(jvmMethod.getName());
        }
        if (element instanceof JvmField jvmField) {
          JvmClass containingClass = jvmField.getContainingClass();
          if (containingClass == null) continue;
          context.fieldClass().add(containingClass.getQualifiedName());
          context.fieldPatterns().add(jvmField.getName());
        }
      }
    });

    UntaintedContext newContext = context.copy();

    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction() {
      @Override
      public void undo() {
        InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, model -> {
          copyContext(context, previousContext);
        });
      }

      @Override
      public void redo() {
        InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, model -> {
          copyContext(context, newContext);
        });
      }

      private static void copyContext(@NotNull UntaintedContext context, UntaintedContext newContext) {
        context.methodPatterns().clear();
        context.methodClass().clear();
        context.fieldClass().clear();
        context.fieldPatterns().clear();
        context.methodPatterns().addAll(newContext.methodPatterns());
        context.methodClass().addAll(newContext.methodClass());
        context.fieldPatterns().addAll(newContext.fieldPatterns());
        context.fieldClass().addAll(newContext.fieldClass());
      }
    });
  }

  @Nullable
  static PsiElement getSourcePsi(@NotNull PsiElement element) {
    if (element.isPhysical()) {
      return element;
    }
    // It is possible that some elements are non-physical (e.g. when we resolved kotlin reference in java file we get light element).
    // In such cases we want to get the original physical file from this non-physical element so that we can add annotation later on.
    // The simplest way to do it is to make two conversions: non-physical element -> uast element -> source psi
    UElement uElement = UastContextKt.toUElement(element);
    if (uElement == null) return null;
    PsiElement sourcePsi = uElement.getSourcePsi();
    if (sourcePsi == null) {
      SourceToSinkProvider provider = SourceToSinkProvider.sourceToSinkLanguageProvider.forLanguage(element.getLanguage());
      if (provider == null) return null;
      sourcePsi = provider.getPhysicalForLightElement(element);
    }
    return sourcePsi;
  }

  @Nullable
  private static PsiFile fileToAnnotate(@NotNull PsiElement element) {
    PsiElement sourcePsi = getSourcePsi(element);
    if (sourcePsi == null) return null;
    return sourcePsi.getContainingFile();
  }

  private static PsiFile[] filesToAnnotate(@NotNull Collection<PsiElement> elements) {
    Set<PsiFile> files = new HashSet<>();
    for (PsiElement element : elements) {
      PsiFile psiFile = fileToAnnotate(element);
      if (psiFile != null) {
        files.add(psiFile);
      }
    }
    return files.toArray(PsiFile.EMPTY_ARRAY);
  }

  private static void annotateInCode(@NotNull Project project, @NotNull Collection<PsiElement> toAnnotate,
                                     @NotNull TaintValueFactory taintValueFactory,
                                     boolean makeAction,
                                     @NotNull List<PsiElement> notProcessed) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    String annotation = taintValueFactory.getAnnotation();
    Set<PsiAnnotation.TargetType> targets = taintValueFactory.getAnnotationTarget(project, scope);
    if (!targets.isEmpty() && annotation != null) {
      for (PsiElement element : toAnnotate) {
        if (!annotateElement(project, annotation, targets, element, makeAction)) {
          notProcessed.add(element);
        }
      }
    }
    else {
      notProcessed.addAll(toAnnotate);
    }
  }

  private static boolean annotateElement(@NotNull Project project,
                                         String annotation,
                                         Set<PsiAnnotation.TargetType> targets,
                                         PsiElement element, boolean makeAction) {
    if (!element.isValid()) return false;
    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) return false;
    PsiModifierListOwner listOwner = ObjectUtils.tryCast(element, PsiModifierListOwner.class);
    if (listOwner == null) return false;
    PsiAnnotation.TargetType[] location = AnnotationTargetUtil.getTargetsForLocation(listOwner.getModifierList());
    Set<PsiAnnotation.TargetType> currentTypes = EnumSet.copyOf(targets);
    currentTypes.retainAll(Arrays.asList(location));
    if (currentTypes.size() == 0) {
      return false;
    }

    if ((currentTypes.contains(PsiAnnotation.TargetType.TYPE_USE))) {
      List<IntentionAction> actions = List.of();
      if (listOwner instanceof JvmParameter jvmParameter) {
        ChangeTypeRequest changeTypeRequest = createTypeRequest(jvmParameter.getType(), annotation);
        actions = JvmElementActionFactories.createChangeTypeActions(jvmParameter, changeTypeRequest);
      }
      else if (listOwner instanceof JvmField jvmField) {
        ChangeTypeRequest changeTypeRequest = createTypeRequest(jvmField.getType(), annotation);
        actions = JvmElementActionFactories.createChangeTypeActions(jvmField, changeTypeRequest);
      }
      else if (listOwner instanceof JvmMethod jvmMethod) {
        JvmType type = jvmMethod.getReturnType();
        if (type == null) return false;
        ChangeTypeRequest changeTypeRequest = createTypeRequest(type, annotation);
        actions = JvmElementActionFactories.createChangeTypeActions(jvmMethod, changeTypeRequest);
      }
      if (actions.size() == 1) {
        if (makeAction) {
          actions.get(0).invoke(project, null, psiFile);
        }
        return true;
      }
    }
    JvmModifiersOwner jvmModifiersOwner = ObjectUtils.tryCast(element, JvmModifiersOwner.class);
    if (jvmModifiersOwner == null) return false;
    AnnotationRequest request = AnnotationRequestsKt.annotationRequest(annotation);
    List<IntentionAction> actions = JvmElementActionFactories.createAddAnnotationActions(jvmModifiersOwner, request);
    if (actions.size() == 1) {
      if (makeAction) {
        actions.get(0).invoke(project, null, psiFile);
      }
      return true;
    }
    return false;
  }

  private static @NotNull ChangeTypeRequest createTypeRequest(@NotNull JvmType jvmType, @NotNull String defaultUntaintedAnnotation) {
    List<AnnotationRequest> annotations = StreamEx.of(jvmType.getAnnotations())
      .map(annotation -> AnnotationRequestsKt.annotationRequest(annotation))
      .append(AnnotationRequestsKt.annotationRequest(defaultUntaintedAnnotation))
      .collect(Collectors.toList());
    return MethodRequestsKt.typeRequest(null, annotations);
  }
}
