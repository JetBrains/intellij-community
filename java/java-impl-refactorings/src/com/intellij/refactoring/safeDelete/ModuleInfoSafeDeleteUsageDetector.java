// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.*;
import com.intellij.refactoring.move.moveClassesOrPackages.ModifyModuleStatementUsageInfo;
import com.intellij.refactoring.move.moveClassesOrPackages.ModuleInfoUsageDetector;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteModuleStatementsUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ModuleInfoSafeDeleteUsageDetector extends ModuleInfoUsageDetector {

  public ModuleInfoSafeDeleteUsageDetector(@NotNull Project project, PsiElement @NotNull [] elementsToDelete) {
    super(project, elementsToDelete);
  }

  @Override
  public void detectModuleStatementsUsed(@NotNull List<? super UsageInfo> usageInfos, @NotNull MultiMap<PsiElement, String> conflicts) {
    if (mySourceClassesByDir.isEmpty()) return;
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(myProject);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    MultiMap<PsiJavaModule, PsiDirectory> sourceDirsByModuleDescriptor = groupDirsByModuleDescriptor(mySourceClassesByDir.keySet());
    List<ModifyModuleStatementUsageInfo> moduleStatementUsages = new SmartList<>();
    for (var entry : sourceDirsByModuleDescriptor.entrySet()) {
      PsiJavaModule sourceModuleDescriptor = entry.getKey();
      Collection<PsiDirectory> sourceDirs = entry.getValue();
      MultiMap<PsiPackage, PsiPackageAccessibilityStatement> sourceExports = collectModuleStatements(sourceModuleDescriptor.getExports());
      MultiMap<PsiPackage, PsiPackageAccessibilityStatement> sourceOpens = collectModuleStatements(sourceModuleDescriptor.getOpens());
      for (PsiDirectory sourceDir : sourceDirs) {
        String sourcePkgName = fileIndex.getPackageNameByDirectory(sourceDir.getVirtualFile());
        if (sourcePkgName == null) continue;
        PsiPackage sourcePkg = psiFacade.findPackage(sourcePkgName);
        if (sourcePkg == null) continue;
        List<PsiPackageAccessibilityStatement> sourceStatements = findModuleStatementsForPkg(sourcePkg, sourceExports, sourceOpens);
        if (sourceStatements.isEmpty()) continue;
        // if a package doesn't contain any other classes except moved ones then we need to delete a corresponding export statement
        Collection<PsiClass> sourceClasses = mySourceClassesByDir.get(sourceDir);
        if (dirContainsOnlyClasses(sourceDir, sourceClasses)) {
          moduleStatementUsages.addAll(
            ContainerUtil.map(sourceStatements, statement -> ModifyModuleStatementUsageInfo.createLastDeletionInfo(statement, sourceModuleDescriptor)));
        }
      }
    }
    if (moduleStatementUsages.isEmpty()) return;
    PsiPackageAccessibilityStatement firstModuleStatement = moduleStatementUsages.get(0).getModuleStatement();
    if (firstModuleStatement != null) {
      usageInfos.add(new SafeDeleteModuleStatementsUsageInfo(firstModuleStatement, moduleStatementUsages));
    }
  }

  @Override
  @NotNull public List<UsageInfo> createUsageInfosForNewlyCreatedDirs() {
    return Collections.emptyList();
  }
}
