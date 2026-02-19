// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.packaging;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PackageGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class ExceptionPackageInspection extends PackageGlobalInspection {
  @Override
  public CommonProblemDescriptor @Nullable [] checkPackage(@NotNull RefPackage refPackage,
                                                           @NotNull AnalysisScope analysisScope,
                                                           @NotNull InspectionManager inspectionManager,
                                                           @NotNull GlobalInspectionContext globalInspectionContext) {
    boolean isExceptionPackage = ReadAction.nonBlocking(() -> {
      final String packageName = refPackage.getQualifiedName();
      final Project project = globalInspectionContext.getProject();
      final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
      if (aPackage == null || aPackage.getSubPackages().length > 0) {
        return false;
      }
      final PsiClass[] classes = aPackage.getClasses(GlobalSearchScope.projectScope(project));
      if (classes.length == 0) {
        return false;
      }
      for (PsiClass aClass : classes) {
        if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_THROWABLE) &&
            !TestFrameworks.getInstance().isTestClass(aClass)) {
          return false;
        }
      }
      return true;
    }).executeSynchronously();
    if (!isExceptionPackage) return null;
    final String errorString = InspectionGadgetsBundle.message("exception.package.problem.descriptor", refPackage.getQualifiedName());
    return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
  }
}
