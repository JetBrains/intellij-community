/*
 * Copyright 2006-2022 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ClassWithTooManyDependenciesInspection extends BaseGlobalInspection {

  @SuppressWarnings("PublicField")
  public int limit = 10;

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefClass refClass) {
      if (!(refClass.getOwner() instanceof RefPackage)) {
        return null;
      }
      final Set<RefClass> dependencies = DependencyUtils.calculateDependenciesForClass(refClass);
      final int numDependencies = dependencies.size();
      if (numDependencies <= limit) {
        return null;
      }
      final String errorString = InspectionGadgetsBundle.message("class.with.too.many.dependencies.problem.descriptor",
                                                                 refClass.getName(), numDependencies, limit);
      return new CommonProblemDescriptor[] {manager.createProblemDescriptor(errorString)};
    }
    return null;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("limit", InspectionGadgetsBundle.message("class.with.too.many.dependencies.max.option"), 1,
             1000));
  }
}
