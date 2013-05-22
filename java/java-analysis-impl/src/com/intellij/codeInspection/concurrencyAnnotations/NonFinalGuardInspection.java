/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class NonFinalGuardInspection extends BaseJavaBatchLocalInspectionTool {

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.CONCURRENCY_ANNOTATION_ISSUES;
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Non-final @GuardedBy field";
  }

  @Override
  @NotNull
  public String getShortName() {
    return "NonFinalGuard";
  }


  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    public Visitor(ProblemsHolder holder) {

      myHolder = holder;
    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      if (!JCiPUtil.isGuardedByAnnotation(annotation)) {
        return;
      }
      final String guardValue = JCiPUtil.getGuardValue(annotation);
      if (guardValue == null || "this".equals(guardValue)) {
        return;
      }
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
      if (containingClass == null) {
        return;
      }
      final PsiField guardField = containingClass.findFieldByName(guardValue, true);
      if (guardField == null) {
        return;
      }
      if (guardField.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiAnnotationMemberValue member = annotation.findAttributeValue("value");
      if (member == null) {
        return;
      }
      myHolder.registerProblem(member, "Non-final @GuardedBy field #ref #loc");
    }

    @Override
    public void visitDocTag(PsiDocTag psiDocTag) {
      super.visitDocTag(psiDocTag);
      if (!JCiPUtil.isGuardedByTag(psiDocTag)) {
        return;
      }
      final String guardValue = JCiPUtil.getGuardValue(psiDocTag);
      if ("this".equals(guardValue)) {
        return;
      }
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(psiDocTag, PsiClass.class);
      if (containingClass == null) {
        return;
      }
      final PsiField guardField = containingClass.findFieldByName(guardValue, true);
      if (guardField == null) {
        return;
      }
      if (guardField.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      myHolder.registerProblem(psiDocTag, "Non-final @GuardedBy field \"" + guardValue + "\" #loc");
    }
  }
}