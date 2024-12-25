// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AnnotateOverriddenMethodParameterFix implements LocalQuickFix {
  private final String myAnnotation;
  private final Nullability myTargetNullability;

  AnnotateOverriddenMethodParameterFix(@NotNull Nullability targetNullability, String annotation) {
    myAnnotation = annotation;
    myTargetNullability = targetNullability;
  }

  @Override
  public @NotNull String getName() {
    return JavaAnalysisBundle.message("annotate.overridden.methods.parameters", ClassUtil.extractClassName(myAnnotation));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    List<PsiParameter> toAnnotate = new ArrayList<>();

    PsiParameter parameter = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiParameter.class, false);
    if (parameter == null || !processParameterInheritorsUnderProgress(parameter, param -> {
      if (AddAnnotationPsiFix.isAvailable(param, myAnnotation)) {
        toAnnotate.add(param);
      }
    })) {
      return;
    }

    FileModificationService.getInstance().preparePsiElementsForWrite(toAnnotate);
    RuntimeException exception = null;
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    String[] annotationsToRemove =
      ArrayUtil.toStringArray(myTargetNullability == Nullability.NOT_NULL ? manager.getNullables() : manager.getNotNulls());
    for (PsiParameter psiParam : toAnnotate) {
      assert psiParam != null : toAnnotate;
      try {
        if (AnnotationUtil.isAnnotatingApplicable(psiParam, myAnnotation)) {
          NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(psiParam);
          if (info != null && info.getNullability() == myTargetNullability && !info.isInferred()) continue;
          AddAnnotationPsiFix fix = new AddAnnotationPsiFix(myAnnotation, psiParam, annotationsToRemove);
          PsiFile containingFile = psiParam.getContainingFile();
          if (psiParam.isValid() && fix.isAvailable(project, containingFile, psiParam, psiParam)) {
            fix.invoke(project, containingFile, psiParam, psiParam);
          }
        }
      }
      catch (PsiInvalidElementAccessException|IncorrectOperationException e) {
        exception = e;
      }
      if (exception != null) {
        throw exception;
      }
    }
  }

  public static boolean processParameterInheritorsUnderProgress(@NotNull PsiParameter parameter, @NotNull Consumer<? super PsiParameter> consumer) {
    PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
    if (method == null) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    int index = ArrayUtilRt.find(parameters, parameter);

    return processModifiableInheritorsUnderProgress(method, psiMethod -> {
      PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
      if (index < psiParameters.length) {
        consumer.accept(psiParameters[index]);
      }
    });
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("annotate.overridden.methods.parameters.family.name");
  }

  public static boolean processModifiableInheritorsUnderProgress(@NotNull PsiMethod method, @NotNull Consumer<? super PsiMethod> consumer) {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      for (PsiMethod psiMethod : OverridingMethodsSearch.search(method)) {
        ReadAction.run(() -> {
          if (psiMethod.isPhysical() && !NullableStuffInspectionBase.shouldSkipOverriderAsGenerated(psiMethod)) {
            consumer.accept(psiMethod);
          }
        });
      }
    }, JavaAnalysisBundle.message("searching.for.overriding.methods"), true, method.getProject());
  }
}
