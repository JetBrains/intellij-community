// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ModCommandAwareExternalAnnotationsManager;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.nullable.NullableStuffInspectionBase;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @see com.intellij.codeInsight.intention.AddAnnotationModCommandAction
 */
public class RemoveAnnotationQuickFix extends ModCommandQuickFix {
  private final SmartPsiElementPointer<PsiAnnotation> myAnnotation;
  private final SmartPsiElementPointer<PsiModifierListOwner> myListOwner;
  private final boolean myRemoveInheritors;

  public RemoveAnnotationQuickFix(@NotNull PsiAnnotation annotation, @Nullable PsiModifierListOwner listOwner) {
    this(annotation, listOwner, false);
  }

  public RemoveAnnotationQuickFix(@NotNull PsiAnnotation annotation, @Nullable PsiModifierListOwner listOwner, boolean removeInheritors) {
    Project project = annotation.getProject();
    SmartPointerManager pm = SmartPointerManager.getInstance(project);
    myAnnotation = pm.createSmartPsiElementPointer(annotation);
    myListOwner = listOwner == null ? null : pm.createSmartPsiElementPointer(listOwner);
    myRemoveInheritors = removeInheritors;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("remove.annotation");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiAnnotation annotation = myAnnotation.getElement();
    if (annotation == null) return ModCommand.nop();

    PsiModifierListOwner listOwner = myListOwner == null ? null : myListOwner.getElement();
    String qualifiedName = annotation.getQualifiedName();

    List<PsiAnnotation> physical = new ArrayList<>();
    List<PsiModifierListOwner> externalOwners = new ArrayList<>();

    registerAnnotation(annotation, listOwner, physical, externalOwners);

    if (myRemoveInheritors && qualifiedName != null && !IntentionPreviewUtils.isIntentionPreviewActive()) {
      if (listOwner instanceof PsiMethod method) {
        for (PsiMethod psiMethod : OverridingMethodsSearch.search(method).asIterable()) {
          if (psiMethod.isPhysical() && !NullableStuffInspectionBase.shouldSkipOverriderAsGenerated(psiMethod)) {
            registerAnnotation(AnnotationUtil.findAnnotation(psiMethod, qualifiedName), psiMethod, physical, externalOwners);
          }
        }
      }
      else if (listOwner instanceof PsiParameter parameter) {
        PsiMethod method = ObjectUtils.tryCast(parameter.getDeclarationScope(), PsiMethod.class);
        if (method == null) return ModCommand.nop();
        int index = method.getParameterList().getParameterIndex(parameter);
        if (index < 0) return ModCommand.nop();

        for (PsiMethod psiMethod : OverridingMethodsSearch.search(method).asIterable()) {
          if (psiMethod.isPhysical() && !NullableStuffInspectionBase.shouldSkipOverriderAsGenerated(psiMethod)) {
            PsiParameter subParameter = psiMethod.getParameterList().getParameter(index);
            if (subParameter != null) {
              registerAnnotation(AnnotationUtil.findAnnotation(subParameter, qualifiedName), subParameter, physical, externalOwners);
            }
          }
        }
      }
    }
    return ModCommand.psiUpdate(ActionContext.from(descriptor), updater -> {
      List<PsiAnnotation> annotations = ContainerUtil.map(physical, updater::getWritable);
      Set<PsiJavaFile> containingFiles = StreamEx.of(annotations).map(PsiAnnotation::getContainingFile).select(PsiJavaFile.class).toSet();
      annotations.forEach(PsiAnnotation::delete);
      containingFiles.forEach(JavaCodeStyleManager.getInstance(project)::removeRedundantImports);
    }).andThen(deannotateExternal(project, qualifiedName, externalOwners));
  }

  private static @NotNull ModCommand deannotateExternal(@NotNull Project project, String qualifiedName, List<PsiModifierListOwner> externalOwners) {
    if (qualifiedName != null) {
      return ModCommandAwareExternalAnnotationsManager.getInstance(project).deannotateModCommand(externalOwners, List.of(qualifiedName));
    }
    return ModCommand.nop();
  }

  private static void registerAnnotation(@Nullable PsiAnnotation annotation,
                                         @Nullable PsiModifierListOwner listOwner,
                                         @NotNull List<PsiAnnotation> physical,
                                         @NotNull List<PsiModifierListOwner> externalOwners) {
    if (annotation == null) return;

    if (AnnotationUtil.isExternalAnnotation(annotation)) {
      ContainerUtil.addIfNotNull(externalOwners, listOwner);
    }
    else {
      physical.add(annotation);
    }
  }
}
