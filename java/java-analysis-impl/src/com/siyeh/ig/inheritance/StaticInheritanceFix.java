// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

class StaticInheritanceFix extends ModCommandQuickFix {
  private final boolean myReplaceInWholeProject;

  StaticInheritanceFix(boolean replaceInWholeProject) {
    myReplaceInWholeProject = replaceInWholeProject;
  }

  @Override
  public @NotNull String getName() {
    String scope =
      myReplaceInWholeProject ? InspectionGadgetsBundle.message("the.whole.project") : InspectionGadgetsBundle.message("this.class");
    return InspectionGadgetsBundle.message("static.inheritance.replace.quickfix", scope);
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("static.inheritance.fix.family.name");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)descriptor.getStartElement();
    final PsiClass iface = (PsiClass)referenceElement.resolve();
    assert iface != null;
    final PsiField[] allFields = iface.getAllFields();

    final PsiClass implementingClass = PsiUtil.getContainingClass(referenceElement);
    assert implementingClass != null;
    final PsiFile file = implementingClass.getContainingFile();

    return ModCommand.psiUpdate(ActionContext.from(descriptor), updater ->
      processUsages(allFields, implementingClass, project, iface, file, updater));
  }

  private void processUsages(@NotNull PsiField @NotNull [] allFields,
                             PsiClass implementingClass,
                             Project project,
                             PsiClass iface,
                             PsiFile file,
                             @NotNull ModPsiUpdater updater) {
    Map<PsiReferenceExpression, PsiClass> replacements = findReplacements(allFields, implementingClass, updater);
    PsiClassType classType = JavaPsiFacade.getInstance(project).getElementFactory().createType(iface);
    IntentionAction fix = QuickFixFactory.getInstance().createExtendsListFix(updater.getWritable(implementingClass), classType, false);
    fix.invoke(project, null, file);
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    replacements.forEach((referenceExpression, containingClass) -> {
      final PsiReferenceExpression qualified = (PsiReferenceExpression)
        elementFactory.createExpressionFromText("xxx." + referenceExpression.getText(), referenceExpression);
      final PsiReferenceExpression newReference = (PsiReferenceExpression)referenceExpression.replace(qualified);
      final PsiReferenceExpression qualifier = (PsiReferenceExpression)newReference.getQualifierExpression();
      assert qualifier != null : DebugUtil.psiToString(newReference, true);
      qualifier.bindToElement(containingClass);
    });
  }

  private @NotNull Map<PsiReferenceExpression, PsiClass> findReplacements(@NotNull PsiField @NotNull [] allFields,
                                                                          @NotNull PsiClass implementingClass,
                                                                          @NotNull ModPsiUpdater updater) {
    Map<PsiReferenceExpression, PsiClass> replacements = new LinkedHashMap<>();
    for (final PsiField field : allFields) {
      SearchScope scope = implementingClass.getUseScope();
      final Query<PsiReference> search = ReferencesSearch.search(field, scope, false);
      for (PsiReference reference : search.asIterable()) {
        if (!(reference instanceof PsiReferenceExpression referenceExpression)) {
          continue;
        }
        if (!myReplaceInWholeProject) {
          boolean isInheritor = false;
          PsiClass aClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
          while (aClass != null) {
            isInheritor = InheritanceUtil.isInheritorOrSelf(aClass, implementingClass, true);
            if (isInheritor) break;
            aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
          }
          if (!isInheritor) continue;
        }

        final PsiClass containingClass = Objects.requireNonNull(field.getContainingClass());
        replacements.put(updater.getWritable(referenceExpression), containingClass);
      }
    }
    return replacements;
  }
}
