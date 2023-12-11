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
package com.siyeh.ig.packaging;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PackageGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PackageInMultipleModulesInspection extends PackageGlobalInspection {

  @Override
  public CommonProblemDescriptor @Nullable [] checkPackage(@NotNull RefPackage refPackage,
                                                           @NotNull AnalysisScope analysisScope,
                                                           @NotNull InspectionManager inspectionManager,
                                                           @NotNull GlobalInspectionContext globalInspectionContext) {
    final Project project = inspectionManager.getProject();
    final PsiPackage aPackage = ReadAction.compute(() -> JavaPsiFacade.getInstance(project).findPackage(refPackage.getQualifiedName()));
    if (aPackage == null)  return null;
    final PsiFile @NotNull [] files = ReadAction.compute(() -> aPackage.getFiles(GlobalSearchScope.projectScope(project)));
    final Set<@NotNull Module> modules = new HashSet<>();
    final ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    for (PsiFile file : files) {
      final Module module = index.getModuleForFile(file.getVirtualFile());
      if (module != null) {
        modules.add(module);
      }
    }
    final int moduleCount = modules.size();
    if (moduleCount <= 1) {
      return null;
    }
    final List<Module> moduleList = new ArrayList<>(modules);
    final String errorString;
    if (moduleCount == 2) {
      errorString = InspectionGadgetsBundle.message(
        "package.in.multiple.modules.problem.descriptor2", refPackage.getQualifiedName(),
        moduleList.get(0).getName(), moduleList.get(1).getName());
    }
    else if (moduleCount == 3) {
      errorString = InspectionGadgetsBundle.message(
        "package.in.multiple.modules.problem.descriptor3", aPackage.getQualifiedName(),
        moduleList.get(0).getName(), moduleList.get(1).getName(), moduleList.get(2).getName());
    }
    else {
      errorString = InspectionGadgetsBundle.message(
        "package.in.multiple.modules.problem.descriptor.many", aPackage.getQualifiedName(),
        moduleList.get(0).getName(), moduleList.get(1).getName(), moduleCount - 2);
    }

    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }
}
