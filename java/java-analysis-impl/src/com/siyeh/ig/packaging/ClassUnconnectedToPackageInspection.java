// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.packaging;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.dependency.DependencyUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UDeclarationKt;

import java.util.Set;

public final class ClassUnconnectedToPackageInspection extends BaseGlobalInspection {

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(
    @NotNull RefEntity refEntity,
    @NotNull AnalysisScope analysisScope,
    @NotNull InspectionManager manager,
    @NotNull GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefClass refClass)) {
      return null;
    }
    final RefEntity owner = refClass.getOwner();
    if (!(owner instanceof RefPackage)) {
      return null;
    }


    int numClasses = getClassesCount(manager, owner);
    if (numClasses == 1) {
      return null;
    }

    final Set<RefClass> dependencies = DependencyUtils.calculateDependenciesForClass(refClass);
    for (RefClass dependency : dependencies) {
      if (inSamePackage(refClass, dependency)) {
        return null;
      }
    }
    final Set<RefClass> dependents = DependencyUtils.calculateDependentsForClass(refClass);
    for (RefClass dependent : dependents) {
      if (inSamePackage(refClass, dependent)) {
        return null;
      }
    }
    PsiElement anchorPsi = UDeclarationKt.getAnchorPsi(refClass.getUastElement());
    if (anchorPsi == null) return null;
    return new CommonProblemDescriptor[]{
      manager.createProblemDescriptor(anchorPsi,
                                      InspectionGadgetsBundle.message(
                                        "class.unconnected.to.package.problem.descriptor"),
                                      true, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false)
    };
  }

  private static int getClassesCount(@NotNull InspectionManager manager, RefEntity owner) {
    return ReadAction.compute(() -> {
      final Project project = manager.getProject();
      final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(owner.getQualifiedName());
      if (aPackage == null || aPackage.getSubPackages().length > 0) {
        return -1;
      }
      return aPackage.getClasses(GlobalSearchScope.projectScope(project)).length;
    });
  }

  private static boolean inSamePackage(RefClass class1, RefClass class2) {
    return class1.getOwner() == class2.getOwner();
  }
}
