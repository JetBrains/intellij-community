// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.packaging;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefJavaUtil;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.psi.PsiElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.dependency.DependencyUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UDeclarationKt;

import java.util.Set;

public final class ClassOnlyUsedInOnePackageInspection extends BaseGlobalInspection {

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(
    @NotNull RefEntity refEntity,
    @NotNull AnalysisScope scope,
    @NotNull InspectionManager manager,
    @NotNull GlobalInspectionContext globalContext) {
    if (!(refEntity instanceof RefClass refClass)) {
      return null;
    }
    final RefEntity owner = refClass.getOwner();
    if (!(owner instanceof RefPackage)) {
      return null;
    }
    final Set<RefClass> dependencies = DependencyUtils.calculateDependenciesForClass(refClass);
    RefPackage otherPackage = null;
    for (RefClass dependency : dependencies) {
      final RefPackage refPackage = RefJavaUtil.getPackage(dependency);
      if (owner == refPackage) {
        return null;
      }
      if (otherPackage != refPackage) {
        if (otherPackage == null) {
          otherPackage = refPackage;
        }
        else {
          return null;
        }
      }
    }
    final Set<RefClass> dependents = DependencyUtils.calculateDependentsForClass(refClass);
    for (RefClass dependent : dependents) {
      final RefPackage refPackage = RefJavaUtil.getPackage(dependent);
      if (owner == refPackage) {
        return null;
      }
      if (otherPackage != refPackage) {
        if (otherPackage == null) {
          otherPackage = refPackage;
        }
        else {
          return null;
        }
      }
    }
    if (otherPackage == null) {
      return null;
    }
    PsiElement anchorPsi = UDeclarationKt.getAnchorPsi(refClass.getUastElement());
    if (anchorPsi == null) return null;
    final String packageName = otherPackage.getName();
    return new CommonProblemDescriptor[]{
      manager.createProblemDescriptor(anchorPsi,
                                      InspectionGadgetsBundle.message(
                                        "class.only.used.in.one.package.problem.descriptor",
                                        packageName),
                                      true, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false)
    };
  }
}
