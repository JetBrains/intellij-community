// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.junit;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

public final class MultipleExceptionsDeclaredOnTestMethodInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "multiple.exceptions.declared.on.test.method.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new MultipleExceptionsDeclaredOnTestMethodFix();
  }

  private static class MultipleExceptionsDeclaredOnTestMethodFix
    extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "throws Exception");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiReferenceList referenceList)) {
        return;
      }
      final PsiJavaCodeReferenceElement[] referenceElements =
        referenceList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        referenceElement.delete();
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(
        project);
      final GlobalSearchScope scope = referenceList.getResolveScope();
      final PsiJavaCodeReferenceElement referenceElement =
        factory.createReferenceElementByFQClassName(
          CommonClassNames.JAVA_LANG_EXCEPTION, scope);
      referenceList.add(referenceElement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantExceptionDeclarationVisitor();
  }

  private static class RedundantExceptionDeclarationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!TestUtils.isJUnitTestMethod(method)) {
        return;
      }
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiJavaCodeReferenceElement[] referenceElements =
        throwsList.getReferenceElements();
      if (referenceElements.length < 2) {
        return;
      }

      final Query<PsiReference> query =
        MethodReferencesSearch.search(method);
      final PsiReference firstReference = query.findFirst();
      if (firstReference != null) {
        return;
      }
      registerError(throwsList);
    }
  }
}