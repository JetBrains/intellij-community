// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public final class StaticGuardedByInstanceInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.concurrency.annotation.issues");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "StaticGuardedByInstance";
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    Visitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitAnnotation(@NotNull PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      if (!JCiPUtil.isGuardedByAnnotation(annotation)) {
        return;
      }
      final PsiMember member = PsiTreeUtil.getParentOfType(annotation, PsiMember.class);
      if (member == null) {
        return;
      }
      if (!member.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final String guardValue = JCiPUtil.getGuardValue(annotation);
      if (guardValue == null) {
        return;
      }

      final PsiAnnotationMemberValue guardRef = annotation.findAttributeValue("value");
      if (guardRef == null) {
        return;
      }
      if ("this".equals(guardValue)) {
        registerError(guardRef);
      }
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
      if (containingClass == null) {
        return;
      }
      final PsiField guardField = containingClass.findFieldByName(guardValue, true);
      if (guardField == null) {
        return;
      }
      if (guardField.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      registerError(guardRef);
    }

    @Override
    public void visitDocTag(@NotNull PsiDocTag psiDocTag) {
      super.visitDocTag(psiDocTag);
      if (!JCiPUtil.isGuardedByTag(psiDocTag)) {
        return;
      }
      final PsiMember member = PsiTreeUtil.getParentOfType(psiDocTag, PsiMember.class);
      if (member == null) {
        return;
      }
      if (!member.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final String guardValue = JCiPUtil.getGuardValue(psiDocTag);

      if ("this".equals(guardValue)) {
        registerError(psiDocTag);
      }
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(psiDocTag, PsiClass.class);
      if (containingClass == null) {
        return;
      }
      final PsiField guardField = containingClass.findFieldByName(guardValue, true);
      if (guardField == null) {
        return;
      }
      if (guardField.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      myHolder.registerProblem(psiDocTag, JavaAnalysisBundle.message("static.member.guarded.by.instance.0.loc", guardValue));
    }

    private void registerError(PsiElement element) {
      myHolder.registerProblem(element, JavaAnalysisBundle.message("static.member.guarded.by.instance.ref.loc"));
    }
  }
}