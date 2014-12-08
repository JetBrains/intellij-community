/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 1/28/11
 */
public class PossibleHeapPollutionVarargsInspection extends BaseJavaBatchLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance("#" + PossibleHeapPollutionVarargsInspection.class.getName());
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Possible heap pollution from parameterized vararg type";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "SafeVarargsDetector";
  }

  @NotNull
  @Override
  public String getID() {
    return "unchecked";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new HeapPollutionVisitor() {
      @Override
      protected void registerProblem(PsiMethod method, PsiIdentifier nameIdentifier) {
        final LocalQuickFix quickFix;
        if (GenericsHighlightUtil.isSafeVarargsNoOverridingCondition(method, PsiUtil.getLanguageLevel(method))) {
          quickFix = new AnnotateAsSafeVarargsQuickFix();
        }
        else {
          final PsiClass containingClass = method.getContainingClass();
          LOG.assertTrue(containingClass != null);
          boolean canBeFinal = !method.hasModifierProperty(PsiModifier.ABSTRACT) &&
                               !containingClass.isInterface() &&
                               OverridingMethodsSearch.search(method).findFirst() == null;
          quickFix = canBeFinal ? new MakeFinalAndAnnotateQuickFix() : null;
        }
        holder.registerProblem(nameIdentifier, "Possible heap pollution from parameterized vararg type #loc", quickFix);
      }
    };
  }

  private static class AnnotateAsSafeVarargsQuickFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
      return "Annotate as @SafeVarargs";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiIdentifier) {
        final PsiMethod psiMethod = (PsiMethod)psiElement.getParent();
        if (psiMethod != null) {
          new AddAnnotationPsiFix("java.lang.SafeVarargs", psiMethod, PsiNameValuePair.EMPTY_ARRAY).applyFix(project, descriptor);
        }
      }
    }
  }

  private static class MakeFinalAndAnnotateQuickFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
      return "Make final and annotate as @SafeVarargs";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement instanceof PsiIdentifier) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(psiElement)) return;
        final PsiMethod psiMethod = (PsiMethod)psiElement.getParent();
        psiMethod.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        new AddAnnotationPsiFix("java.lang.SafeVarargs", psiMethod, PsiNameValuePair.EMPTY_ARRAY).applyFix(project, descriptor);
      }
    }
  }

  public abstract static class HeapPollutionVisitor extends JavaElementVisitor {
    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!PsiUtil.getLanguageLevel(method).isAtLeast(LanguageLevel.JDK_1_7)) return;
      if (AnnotationUtil.isAnnotated(method, "java.lang.SafeVarargs", false)) return;
      if (!method.isVarArgs()) return;

      final PsiParameter[] parameters = method.getParameterList().getParameters();
      final PsiParameter psiParameter = parameters[parameters.length - 1];
      if (!psiParameter.isVarArgs()) return;

      final PsiType type = psiParameter.getType();
      LOG.assertTrue(type instanceof PsiEllipsisType, "type: " + type.getCanonicalText() + "; param: " + psiParameter);

      final PsiType componentType = ((PsiEllipsisType)type).getComponentType();
      if (JavaGenericsUtil.isReifiableType(componentType)) {
        return;
      }
      for (PsiReference reference : ReferencesSearch.search(psiParameter)) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiExpression && !PsiUtil.isAccessedForReading((PsiExpression)element)) {
          return;
        }
      }
      final PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier != null) {
        //if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return;
        //final PsiClass containingClass = method.getContainingClass();
        //if (containingClass == null || containingClass.isInterface()) return; do not add
        registerProblem(method, nameIdentifier);
      }
    }

    protected abstract void registerProblem(PsiMethod method, PsiIdentifier nameIdentifier);
  }
}
