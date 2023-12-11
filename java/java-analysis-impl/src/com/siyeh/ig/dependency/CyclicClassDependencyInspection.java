/*
 * Copyright 2006-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.dependency;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.util.RefEntityAlphabeticalComparator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UDeclarationKt;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class CyclicClassDependencyInspection extends BaseGlobalInspection {

  public boolean ignoreInSameFile = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreInSameFile", InspectionGadgetsBundle.message("cyclic.class.dependency.ignore.in.same.file")));
  }

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(
    @NotNull RefEntity refEntity,
    @NotNull AnalysisScope analysisScope,
    @NotNull InspectionManager inspectionManager,
    @NotNull GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefClass refClass)) {
      return null;
    }
    if (refClass.isAnonymous() || refClass.isLocalClass() || refClass.isSyntheticJSP()) {
      return null;
    }
    final Set<RefClass> dependencies = DependencyUtils.calculateTransitiveDependenciesForClass(refClass);
    final Set<RefClass> dependents = DependencyUtils.calculateTransitiveDependentsForClass(refClass);
    final VirtualFile vFile = refClass.getPointer().getVirtualFile();
    if (ignoreInSameFile) {
      final Predicate<RefClass> filter = aClass -> aClass.getPointer().getVirtualFile().equals(vFile);
      dependencies.removeIf(filter);
      dependents.removeIf(filter);
    }
    final Set<RefClass> mutualDependents = new HashSet<>(dependencies);
    mutualDependents.retainAll(dependents);
    final int numMutualDependents = mutualDependents.size();
    if (numMutualDependents == 0) {
      return null;
    }
    final String errorString;
    if (numMutualDependents == 1) {
      final RefClass[] classes = mutualDependents.toArray(new RefClass[1]);
      errorString = InspectionGadgetsBundle.message("cyclic.class.dependency.1.problem.descriptor",
                                                    refEntity.getName(), classes[0].getExternalName());
    }
    else if (numMutualDependents == 2) {
      final RefClass[] classes = mutualDependents.toArray(new RefClass[2]);
      Arrays.sort(classes, RefEntityAlphabeticalComparator.getInstance());
      errorString = InspectionGadgetsBundle.message("cyclic.class.dependency.2.problem.descriptor",
                                                    refEntity.getName(), classes[0].getExternalName(), classes[1].getExternalName());
    }
    else {
      errorString = InspectionGadgetsBundle.message("cyclic.class.dependency.problem.descriptor",
                                                    refEntity.getName(), Integer.valueOf(numMutualDependents));
    }
    final PsiElement anchor = UDeclarationKt.getAnchorPsi(refClass.getUastElement());
    if (anchor == null) return null;
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(anchor, errorString, (LocalQuickFix)null,
                                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false)
    };
  }
}
