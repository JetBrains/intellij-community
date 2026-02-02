// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.annotation;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.ModCommandAwareExternalAnnotationsManager;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Bas Leijdekkers
 */
public final class AnnotateOverriddenMethodsIntention extends BaseElementAtCaretIntentionAction {
  private final PsiElementPredicate myPredicate = new AnnotateOverriddenMethodsPredicate();
  private @IntentionName String m_text = null;

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("annotate.overridden.methods.intention.family.name");
  }

  @Override
  public @NotNull String getText() {
    return m_text == null ? "" : m_text;
  }

  @Nullable
  PsiElement findMatchingElement(@Nullable PsiElement element) {
    if (element == null || !JavaLanguage.INSTANCE.equals(element.getLanguage())) return null;

    while (element != null) {
      if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) {
        break;
      }
      else if (myPredicate.satisfiedBy(element)) {
        return element;
      }
      element = element.getParent();
      if (element instanceof PsiFile) {
        break;
      }
    }
    return null;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement node) {
    final PsiAnnotation annotation = (PsiAnnotation)findMatchingElement(node);
    if (annotation == null) return false;
    final String annotationText = annotation.getText();
    final PsiElement grandParent = annotation.getParent().getParent();
    if (grandParent instanceof PsiMethod) {
      m_text = IntentionPowerPackBundle.message("annotate.overridden.methods.intention.method.name", annotationText);
    }
    else {
      m_text = IntentionPowerPackBundle.message("annotate.overridden.methods.intention.parameters.name", annotationText);
    }
    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element){
    final PsiAnnotation annotation = (PsiAnnotation)findMatchingElement(element);
    if (annotation == null) return;
    final String annotationName = annotation.getQualifiedName();
    if (annotationName == null) {
      return;
    }
    final NullableNotNullManager notNullManager = NullableNotNullManager.getInstance(project);
    final List<String> notNulls = notNullManager.getNotNulls();
    final List<String> nullables = notNullManager.getNullables();
    final List<String> annotationsToRemove;
    if (notNulls.contains(annotationName)) {
      annotationsToRemove = nullables;
    }
    else if (nullables.contains(annotationName)) {
      annotationsToRemove = notNulls;
    }
    else {
      annotationsToRemove = Collections.emptyList();
    }
    final PsiElement parent = annotation.getParent();
    final PsiElement grandParent = parent.getParent();
    final PsiMethod method;
    final int parameterIndex;
    if (!(grandParent instanceof PsiMethod)) {
      if (!(grandParent instanceof PsiParameter parameter)) {
        return;
      }
      final PsiElement greatGrandParent = grandParent.getParent();
      if (!(greatGrandParent instanceof PsiParameterList parameterList)) {
        return;
      }
      parameterIndex = parameterList.getParameterIndex(parameter);
      final PsiElement greatGreatGrandParent = greatGrandParent.getParent();
      if (!(greatGreatGrandParent instanceof PsiMethod)) {
        return;
      }
      method = (PsiMethod)greatGreatGrandParent;
    }
    else {
      parameterIndex = -1;
      method = (PsiMethod)grandParent;
    }
    final Collection<PsiMethod> overridingMethods = OverridingMethodsSearch.search(method).findAll();
    final List<PsiMethod> prepare = new ArrayList<>();
    final ModCommandAwareExternalAnnotationsManager annotationsManager = ModCommandAwareExternalAnnotationsManager.getInstance(project);
    final Map<PsiMethod, ExternalAnnotationsManager.AnnotationPlace> annotationPlaces = new LinkedHashMap<>();
    for (PsiMethod overridingMethod : overridingMethods) {
      annotationPlaces.put(overridingMethod, annotationsManager.chooseAnnotationsPlaceNoUi(overridingMethod));
    }

    askAboutSourceRootsWithoutExternalAnnotations(project, overridingMethods, annotationsManager, annotationPlaces);

    for (PsiMethod overridingMethod : overridingMethods) {
      if (annotationPlaces.get(overridingMethod) == ExternalAnnotationsManager.AnnotationPlace.IN_CODE) {
        prepare.add(overridingMethod);
      }
    }
    
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(prepare)) {
      return;
    }
    final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    ActionContext context = ActionContext.from(editor, element.getContainingFile());
    for (PsiMethod overridingMethod : overridingMethods) {
      ModCommandExecutor.executeInteractively(context, getFamilyName(), editor, () -> {
        PsiModifierListOwner target =
          parameterIndex == -1 ? overridingMethod : overridingMethod.getParameterList().getParameter(parameterIndex);
        return annotate(target, annotationName, attributes, annotationsToRemove, annotationPlaces.get(overridingMethod), annotationsManager);
      });
    }
    if (!prepare.isEmpty()) {
      UndoUtil.markPsiFileForUndo(annotation.getContainingFile());
    }
  }

  private static void askAboutSourceRootsWithoutExternalAnnotations(Project project,
                                                                    Collection<PsiMethod> overridingMethods,
                                                                    ExternalAnnotationsManager annotationsManager,
                                                                    Map<PsiMethod, ExternalAnnotationsManager.AnnotationPlace> annotationPlaces) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Map<VirtualFile, ExternalAnnotationsManager.AnnotationPlace> sourceRoots = new HashMap<>();
    for (PsiMethod overridingMethod : overridingMethods) {
      ExternalAnnotationsManager.AnnotationPlace annotationPlace = annotationPlaces.get(overridingMethod);
      if (annotationPlace == ExternalAnnotationsManager.AnnotationPlace.NEED_ASK_USER) {
        //ask once per source root
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(overridingMethod);
        VirtualFile sourceRoot = virtualFile != null ? fileIndex.getSourceRootForFile(virtualFile) : null;
        if (sourceRoot != null) {
          annotationPlaces
            .put(overridingMethod,
                 sourceRoots.computeIfAbsent(sourceRoot, __ -> annotationsManager.chooseAnnotationsPlace(overridingMethod)));
        }
      }
    }
  }

  private static @NotNull ModCommand annotate(@Nullable PsiModifierListOwner modifierListOwner,
                                              @NotNull String annotationName,
                                              @NotNull PsiNameValuePair @NotNull [] attributes,
                                              @NotNull List<String> annotationsToRemove,
                                              ExternalAnnotationsManager.@NotNull AnnotationPlace annotationAnnotationPlace,
                                              @NotNull ModCommandAwareExternalAnnotationsManager annotationsManager) throws ProcessCanceledException {
    if (modifierListOwner == null) return ModCommand.nop();
    PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(modifierListOwner, annotationName);
    if (target == null || target.hasAnnotation(annotationName)) return ModCommand.nop();
    if (annotationAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.NOWHERE) {
      return ModCommand.nop();
    }
    if (annotationAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.EXTERNAL) {
      return annotationsManager.annotateExternallyModCommand(modifierListOwner, annotationName, attributes, annotationsToRemove);
    }
    else {
      return ModCommand.psiUpdate(modifierListOwner, (owner, updater) -> {
        PsiAnnotationOwner writableTarget = Objects.requireNonNull(AnnotationTargetUtil.getTarget(owner, annotationName));
        for (String annotationToRemove : annotationsToRemove) {
          final PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, annotationToRemove);
          if (annotation != null) {
            annotation.delete();
          }
        }
        final PsiAnnotation inserted = writableTarget.addAnnotation(annotationName);
        for (PsiNameValuePair pair : attributes) {
          inserted.setDeclaredAttributeValue(pair.getName(), pair.getValue());
        }
        JavaCodeStyleManager.getInstance(modifierListOwner.getProject()).shortenClassReferences(inserted);
      });
    }
  }
}
