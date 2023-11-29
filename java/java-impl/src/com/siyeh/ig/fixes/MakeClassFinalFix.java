// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.modcommand.ModShowConflicts;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.modcommand.ModCommand.*;
import static com.intellij.psi.PsiModifier.ABSTRACT;
import static com.intellij.psi.PsiModifier.FINAL;

/**
 * @author Bas Leijdekkers
 */
public class MakeClassFinalFix extends ModCommandQuickFix {

  private final String className;

  public MakeClassFinalFix(PsiClass aClass) {
    className = aClass.getName();
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message(
      "make.class.final.fix.name", className);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("make.class.final.fix.family.name");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiClass containingClass = findClassToFix(element);
    if (containingClass == null) {
      return nop();
    }
    final PsiModifierList modifierList = containingClass.getModifierList();
    assert modifierList != null;
    final List<String> conflictMessages = new ArrayList<>();
    if (!IntentionPreviewUtils.isIntentionPreviewActive()) {
      final Query<PsiClass> search = ClassInheritorsSearch.search(containingClass);
      search.forEach(aClass -> {
        conflictMessages.add(InspectionGadgetsBundle
                               .message("0.will.no.longer.be.overridable.by.1", RefactoringUIUtil.getDescription(containingClass, false),
                                        RefactoringUIUtil.getDescription(aClass, false)));
        return true;
      });
    }
    ModShowConflicts.Conflict conflict = new ModShowConflicts.Conflict(conflictMessages);
    return showConflicts(Map.of(containingClass, conflict))
      .andThen(psiUpdate(modifierList, MakeClassFinalFix::doMakeFinal));
  }

  private static void doMakeFinal(PsiModifierList modifierList) {
    modifierList.setModifierProperty(FINAL, true);
    modifierList.setModifierProperty(ABSTRACT, false);
  }

  private static @Nullable PsiClass findClassToFix(PsiElement element) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (containingClass == null) {
      return null;
    }
    return containingClass.getModifierList() == null ? null : containingClass;
  }
}
