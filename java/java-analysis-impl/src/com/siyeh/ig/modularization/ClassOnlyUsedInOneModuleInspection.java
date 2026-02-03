// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.modularization;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.psi.PsiElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.dependency.DependencyUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UDeclarationKt;

import java.util.Set;

public final class ClassOnlyUsedInOneModuleInspection extends BaseGlobalInspection {

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
    final Set<RefClass> dependencies =
      DependencyUtils.calculateDependenciesForClass(refClass);
    RefModule otherModule = null;
    for (RefClass dependency : dependencies) {
      final RefModule module = dependency.getModule();
      if (refClass.getModule() == module) {
        return null;
      }
      if (otherModule != module) {
        if (otherModule == null) {
          otherModule = module;
        }
        else {
          return null;
        }
      }
    }
    final Set<RefClass> dependents =
      DependencyUtils.calculateDependentsForClass(refClass);
    for (RefClass dependent : dependents) {
      final RefModule module = dependent.getModule();
      if (refClass.getModule() == module) {
        return null;
      }
      if (otherModule != module) {
        if (otherModule == null) {
          otherModule = module;
        }
        else {
          return null;
        }
      }
    }
    if (otherModule == null) {
      return null;
    }
    PsiElement anchorPsi = UDeclarationKt.getAnchorPsi(refClass.getUastElement());
    if (anchorPsi == null) return null;
    final String moduleName = otherModule.getName();
    return new CommonProblemDescriptor[]{
      manager.createProblemDescriptor(anchorPsi,
                                      InspectionGadgetsBundle.message(
                                        "class.only.used.in.one.module.problem.descriptor",
                                        moduleName),
                                      true, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false)
    };
  }
}
