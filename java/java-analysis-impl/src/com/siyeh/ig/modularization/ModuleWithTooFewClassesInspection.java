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
package com.siyeh.ig.modularization;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ModuleWithTooFewClassesInspection extends BaseGlobalInspection {

  @SuppressWarnings("PublicField")
  public int limit = 10;

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope analysisScope,
                                                           @NotNull InspectionManager inspectionManager,
                                                           @NotNull GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefModule refModule)) {
      return null;
    }
    final Project project = inspectionManager.getProject();
    if (ModuleManager.getInstance(project).getModules().length == 1) {
      return null;
    }
    final ModuleFileIndex index = ModuleRootManager.getInstance(refModule.getModule()).getFileIndex();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final int[] count = {0};
    index.iterateContent(fileOrDir -> {
      if (fileOrDir.isDirectory()) return true;
      if (!analysisScope.contains(fileOrDir)) return true;
      final PsiFile file = psiManager.findFile(fileOrDir);
      if (!(file instanceof PsiClassOwner)) return true;
      count[0] += ((PsiClassOwner)file).getClasses().length;
      return count[0] <= limit;
    });
    if (count[0] >= limit || count[0] == 0) {
      return null;
    }
    final String errorString = InspectionGadgetsBundle.message("module.with.too.few.classes.problem.descriptor",
                                                               refModule.getName(), Integer.valueOf(count[0]), Integer.valueOf(limit));
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("limit", InspectionGadgetsBundle.message("module.with.too.few.classes.min.option"), 2, 1000));
  }
}
