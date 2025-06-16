// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.daemon.impl.quickfix.ExtendsListModCommandFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.*;
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

    ActionContext context = ActionContext.from(descriptor);
    return ModCommand.psiUpdate(context, updater ->
      processUsages(context, allFields, implementingClass, project, iface, updater));
  }

  private void processUsages(@NotNull ActionContext context, 
                             @NotNull PsiField @NotNull [] allFields,
                             PsiClass implementingClass,
                             Project project,
                             PsiClass iface,
                             @NotNull ModPsiUpdater updater) {
    Map<PsiReferenceExpression, PsiClass> replacements = findReplacements(allFields, implementingClass, updater);
    PsiClassType classType = JavaPsiFacade.getInstance(project).getElementFactory().createType(iface);
    PsiClass aClass = updater.getWritable(implementingClass);
    var fix = new ExtendsListModCommandFix(aClass, classType, false);
    ModCommandExecutor.getInstance().executeForFileCopy(fix.perform(context), aClass.getContainingFile());
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
