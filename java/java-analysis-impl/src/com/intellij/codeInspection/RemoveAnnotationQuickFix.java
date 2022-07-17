// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.nullable.AnnotateOverriddenMethodParameterFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;


public class RemoveAnnotationQuickFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PsiAnnotation> myAnnotation;
  private final SmartPsiElementPointer<PsiModifierListOwner> myListOwner;

  public RemoveAnnotationQuickFix(@NotNull PsiAnnotation annotation, @Nullable PsiModifierListOwner listOwner) {
    Project project = annotation.getProject();
    SmartPointerManager pm = SmartPointerManager.getInstance(project);
    myAnnotation = pm.createSmartPsiElementPointer(annotation);
    myListOwner = listOwner == null ? null : pm.createSmartPsiElementPointer(listOwner);
  }

  protected boolean shouldRemoveInheritors() {
    return false;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaAnalysisBundle.message("remove.annotation");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiAnnotation annotation = myAnnotation.getElement();
    if (annotation == null) return;

    PsiModifierListOwner listOwner = myListOwner == null ? null : myListOwner.getElement();
    String qualifiedName = annotation.getQualifiedName();

    List<PsiAnnotation> physical = new ArrayList<>();
    List<PsiModifierListOwner> externalOwners = new ArrayList<>();

    registerAnnotation(annotation, listOwner, physical, externalOwners);

    if (shouldRemoveInheritors() && qualifiedName != null) {
      Consumer<PsiModifierListOwner> inheritorProcessor = owner -> {
        registerAnnotation(AnnotationUtil.findAnnotation(owner, qualifiedName), owner, physical, externalOwners);
      };
      if (listOwner instanceof PsiMethod &&
          !AnnotateMethodFix.processModifiableInheritorsUnderProgress((PsiMethod)listOwner, inheritorProcessor)) {
        return;
      }
      if (listOwner instanceof PsiParameter &&
               !AnnotateOverriddenMethodParameterFix.processParameterInheritorsUnderProgress((PsiParameter)listOwner, inheritorProcessor)) {
        return;
      }
    }

    if (!FileModificationService.getInstance().preparePsiElementsForWrite(physical)) {
      return;
    }
    WriteAction.run(() -> {
      Set<PsiJavaFile> containingFiles = StreamEx.of(physical).map(PsiAnnotation::getContainingFile).select(PsiJavaFile.class).toSet();
      physical.forEach(PsiAnnotation::delete);
      containingFiles.forEach(JavaCodeStyleManager.getInstance(project)::removeRedundantImports);
    });

    if (qualifiedName != null) {
      for (PsiModifierListOwner owner : externalOwners) {
        ExternalAnnotationsManager.getInstance(project).deannotate(owner, qualifiedName);
      }
    }
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