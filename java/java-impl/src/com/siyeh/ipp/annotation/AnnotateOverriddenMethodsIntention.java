// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.annotation;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

  @NotNull
  @Override
  public final String getText() {
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
  public final boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement node) {
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
    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
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
    try {
      for (PsiMethod overridingMethod : overridingMethods) {
        if (parameterIndex == -1) {
          annotate(overridingMethod, annotationName, attributes, annotationsToRemove, annotationPlaces.get(overridingMethod), annotationsManager);
        }
        else {
          final PsiParameterList parameterList = overridingMethod.getParameterList();
          final PsiParameter[] parameters = parameterList.getParameters();
          final PsiParameter parameter = parameters[parameterIndex];
          annotate(parameter, annotationName, attributes, annotationsToRemove, annotationPlaces.get(overridingMethod), annotationsManager);
        }
      }
    }
    catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {
      //escape on configuring root cancel further annotations
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

  private static void annotate(PsiModifierListOwner modifierListOwner,
                               String annotationName,
                               PsiNameValuePair[] attributes,
                               List<String> annotationsToRemove,
                               ExternalAnnotationsManager.AnnotationPlace annotationAnnotationPlace,
                               ExternalAnnotationsManager annotationsManager) throws ProcessCanceledException {
    PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(modifierListOwner, annotationName);
    if (target == null || target.hasAnnotation(annotationName)) return;
    if (annotationAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.NOWHERE) {
      return;
    }
    if (annotationAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.EXTERNAL) {
      for (String annotationToRemove : annotationsToRemove) {
        annotationsManager.deannotate(modifierListOwner, annotationToRemove);
      }
      try {
        annotationsManager.annotateExternally(modifierListOwner, annotationName, modifierListOwner.getContainingFile(), attributes);
      }
      catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {}
    }
    else {
      WriteAction.run(() -> {
        for (String annotationToRemove : annotationsToRemove) {
          final PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierListOwner, annotationToRemove);
          if (annotation != null) {
            annotation.delete();
          }
        }
        final PsiAnnotation inserted = target.addAnnotation(annotationName);
        for (PsiNameValuePair pair : attributes) {
          inserted.setDeclaredAttributeValue(pair.getName(), pair.getValue());
        }
        JavaCodeStyleManager.getInstance(modifierListOwner.getProject()).shortenClassReferences(inserted);
      });
    }
  }
}
