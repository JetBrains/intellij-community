// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AnnotateOverriddenMethodParameterFix implements LocalQuickFix {
  private final Nullability myTargetNullability;

  AnnotateOverriddenMethodParameterFix(@NotNull Nullability targetNullability) {
    myTargetNullability = targetNullability;
  }

  @Override
  public @NotNull String getName() {
    return myTargetNullability == Nullability.NOT_NULL ?
           JavaAnalysisBundle.message("annotate.overridden.methods.parameters.nonnull") :
           JavaAnalysisBundle.message("annotate.overridden.methods.parameters.nullable");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    List<PsiParameter> toAnnotate = new ArrayList<>();

    PsiParameter parameter = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiParameter.class, false);
    if (parameter == null || !processParameterInheritorsUnderProgress(parameter, toAnnotate::add)) {
      return;
    }

    FileModificationService.getInstance().preparePsiElementsForWrite(toAnnotate);
    ActionContext actionContext = ActionContext.from(descriptor);
    for (PsiParameter psiParam : toAnnotate) {
      assert psiParam != null : toAnnotate;
      ModCommandExecutor.executeInteractively(actionContext, getFamilyName(), null, () -> {
        NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(project).findEffectiveNullabilityInfo(psiParam);
        if (info != null && info.getNullability() == myTargetNullability &&
            info.getInheritedFrom() == null && !info.isInferred()) {
          return ModCommand.nop();
        }
        ModCommandAction action = myTargetNullability == Nullability.NOT_NULL
                                  ? AddAnnotationModCommandAction.createAddNotNullFix(psiParam)
                                  : AddAnnotationModCommandAction.createAddNullableFix(psiParam);
        return action == null || action.getPresentation(actionContext) == null ? ModCommand.nop() : action.perform(actionContext);
      });
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
      for (PsiMethod psiMethod : OverridingMethodsSearch.search(method).asIterable()) {
        ReadAction.run(() -> {
          if (psiMethod.isPhysical() && !NullableStuffInspectionBase.shouldSkipOverriderAsGenerated(psiMethod)) {
            consumer.accept(psiMethod);
          }
        });
      }
    }, JavaAnalysisBundle.message("searching.for.overriding.methods"), true, method.getProject());
  }
}
