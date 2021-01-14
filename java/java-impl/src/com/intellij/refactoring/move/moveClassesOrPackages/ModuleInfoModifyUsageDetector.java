// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class ModuleInfoModifyUsageDetector extends ModuleInfoUsageDetector {
  private final MoveDestination myMoveDestination;
  private final MultiMap<PsiJavaModule, PsiDirectory> myAbsentDirsByModuleDescriptor = MultiMap.create();

  ModuleInfoModifyUsageDetector(@NotNull Project project, PsiElement @NotNull [] elementsToMove, @NotNull MoveDestination moveDestination) {
    super(project, elementsToMove);
    myMoveDestination = moveDestination;
  }

  @Override
  public void detectModuleStatementsUsed(@NotNull List<? super UsageInfo> usageInfos, @NotNull MultiMap<PsiElement, String> conflicts) {
    if (mySourceClassesByDir.isEmpty()) return;
    MultiMap<PsiJavaModule, PsiDirectory> sourceDirsByModuleDescriptor = groupDirsByModuleDescriptor(mySourceClassesByDir.keySet());
    if (sourceDirsByModuleDescriptor.isEmpty()) return;
    MultiMap<PsiJavaModule, PsiDirectory> absentDirsByModuleDescriptor = MultiMap.create();
    myAbsentDirsByModuleDescriptor.clear();
    detectModuleStatementsUsed(sourceDirsByModuleDescriptor, usageInfos, conflicts, absentDirsByModuleDescriptor);
    myAbsentDirsByModuleDescriptor.putAllValues(absentDirsByModuleDescriptor);
  }

  /**
   * Handling the absent directories which haven't been created yet during find usages operation.
   * Sample: we have a class pack1.A, we want to move it to pack1.pack2 which doesn't exist.
   */
  @Override
  @NotNull
  public List<UsageInfo> createUsageInfosForNewlyCreatedDirs() {
    if (myAbsentDirsByModuleDescriptor.isEmpty()) return Collections.emptyList();
    List<UsageInfo> result = new SmartList<>();
    detectModuleStatementsUsed(myAbsentDirsByModuleDescriptor, result, MultiMap.create(), MultiMap.create());
    return result;
  }

  private void detectModuleStatementsUsed(@NotNull MultiMap<PsiJavaModule, PsiDirectory> sourceDirsByModuleDescriptor,
                                          @NotNull List<? super UsageInfo> usageInfos,
                                          @NotNull MultiMap<PsiElement, String> conflicts,
                                          @NotNull MultiMap<PsiJavaModule, PsiDirectory> absentDirsByModuleDescriptor) {
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(myProject);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    PsiPackage targetPackage = psiFacade.findPackage(myMoveDestination.getTargetPackage().getQualifiedName());
    for (var entry : sourceDirsByModuleDescriptor.entrySet()) {
      PsiJavaModule sourceModuleDescriptor = entry.getKey();
      Collection<PsiDirectory> sourceDirs = entry.getValue();
      MultiMap<PsiPackage, PsiPackageAccessibilityStatement> sourceExports = collectModuleStatements(sourceModuleDescriptor.getExports());
      MultiMap<PsiPackage, PsiPackageAccessibilityStatement> sourceOpens = collectModuleStatements(sourceModuleDescriptor.getOpens());
      Map<PsiJavaModule, DirectoryWithModuleStatements> targetModuleStatementsByModuleDescriptor = new HashMap<>();
      for (PsiDirectory sourceDir : sourceDirs) {
        PsiDirectory targetDirectory = myMoveDestination.getTargetIfExists(sourceDir);
        if (targetDirectory == null) {
          absentDirsByModuleDescriptor.putValue(sourceModuleDescriptor, sourceDir);
          continue;
        }
        String sourcePkgName = fileIndex.getPackageNameByDirectory(sourceDir.getVirtualFile());
        if (sourcePkgName == null) continue;
        PsiPackage sourcePkg = psiFacade.findPackage(sourcePkgName);
        if (sourcePkg == null) continue;
        List<PsiPackageAccessibilityStatement> sourceStatements = findModuleStatementsForPkg(sourcePkg, sourceExports, sourceOpens);
        if (sourceStatements.isEmpty()) continue;
        // if a package doesn't contain any other classes except moved ones then we need to delete a corresponding export statement
        Collection<PsiClass> sourceClasses = mySourceClassesByDir.get(sourceDir);
        if (dirContainsOnlyClasses(sourceDir, sourceClasses)) {
          sourceStatements.forEach(statement -> usageInfos.add(ModifyModuleStatementUsageInfo.createLastDeletionInfo(statement, sourceModuleDescriptor)));
        }
        PsiJavaModule targetModuleDescriptor = JavaModuleGraphUtil.findDescriptorByElement(targetDirectory);
        // so far we don't take into account a motion between separate JPMS-modules
        if (sourceModuleDescriptor == targetModuleDescriptor) {
          DirectoryWithModuleStatements dirWithStatements = targetModuleStatementsByModuleDescriptor
            .computeIfAbsent(sourceModuleDescriptor, __ -> new DirectoryWithModuleStatements(targetDirectory, new SmartList<>()));
          dirWithStatements.myModuleStatements.addAll(sourceStatements);
        }
        else if (targetModuleDescriptor != null) {
          sourceClasses.forEach(c -> conflicts.put(c, List.of(
            JavaRefactoringBundle.message("move.classes.or.packages.different.modules.exports.conflict",
                                          RefactoringUIUtil.getDescription(c, false),
                                          StringUtil.htmlEmphasize(sourceModuleDescriptor.getName()),
                                          StringUtil.htmlEmphasize(targetModuleDescriptor.getName())))));
        }
      }
      for (var statementEntry : targetModuleStatementsByModuleDescriptor.entrySet()) {
        PsiJavaModule targetModuleDescriptor = statementEntry.getKey();
        List<PsiPackageAccessibilityStatement> sourceModuleStatements = statementEntry.getValue().myModuleStatements;
        assert targetPackage != null;
        List<PsiPackageAccessibilityStatement> targetPkgModuleStatements = findModuleStatementsForPkg(targetModuleDescriptor, targetPackage);
        List<PsiPackageAccessibilityStatement> statementsToCreate = new SmartList<>();
        List<PsiPackageAccessibilityStatement> statementsToDelete = new SmartList<>();
        mergeModuleStatements(sourceModuleStatements, targetPkgModuleStatements, targetPackage, statementsToCreate, statementsToDelete);
        if (statementsToCreate.isEmpty()) continue;
        if (!dirContainsOnlyClasses(statementEntry.getValue().myDir, Collections.emptyList())) {
          conflicts.put(targetModuleDescriptor.getNameIdentifier(), List.of(
            JavaRefactoringBundle.message("move.classes.or.packages.new.module.exports.conflict",
                                          StringUtil.htmlEmphasize(targetPackage.getQualifiedName()))));
        }
        statementsToDelete.stream().map(pkgStatement -> ModifyModuleStatementUsageInfo.createDeletionInfo(pkgStatement, targetModuleDescriptor))
          .forEach(usageInfos::add);
        statementsToCreate.stream().map(pkgStatement -> ModifyModuleStatementUsageInfo.createAdditionInfo(pkgStatement, targetModuleDescriptor))
          .forEach(usageInfos::add);
      }
    }
  }

  @NotNull
  private static List<PsiPackageAccessibilityStatement> findModuleStatementsForPkg(@NotNull PsiJavaModule moduleDescriptor,
                                                                                   @NotNull PsiPackage psiPackage) {
    MultiMap<PsiPackage, PsiPackageAccessibilityStatement> exports = collectModuleStatements(moduleDescriptor.getExports());
    MultiMap<PsiPackage, PsiPackageAccessibilityStatement> opens = collectModuleStatements(moduleDescriptor.getOpens());
    return findModuleStatementsForPkg(psiPackage, exports, opens);
  }

  /**
   * Sample:
   * We have three JPMS modules: module1, module2, module3
   * - module1 contains:
   * -- package pack1 contains class S1
   * -- package pack2 contains class S2
   * -- package pack1.pack2 contains class S3
   * -- module-info exports pack1 to module2, pack2 to module 3, pack1.pack2 to module2
   * We move S1, S2 to pack1.pack2 and merge exports in such a way that module1 exports pack1.pack2 both to module2 and module3.
   * The same is true for opens one.
   *
   * @param sourceStatements corresponds to the statements in all source directories
   * @param targetStatements corresponds to the statements in the target directory
   * @param targetPackage corresponds to the target package
   * @param statementsToDelete statements to be deleted in the target module descriptor
   * @param statementsToCreate statements to be created in the target module descriptor
   */
  private void mergeModuleStatements(@NotNull List<PsiPackageAccessibilityStatement> sourceStatements,
                                     @NotNull List<PsiPackageAccessibilityStatement> targetStatements,
                                     @NotNull PsiPackage targetPackage,
                                     @NotNull List<PsiPackageAccessibilityStatement> statementsToCreate,
                                     @NotNull List<PsiPackageAccessibilityStatement> statementsToDelete) {
    List<PsiPackageAccessibilityStatement> allModuleStatements = new SmartList<>(sourceStatements);
    allModuleStatements.addAll(targetStatements);
    Map<PsiPackageAccessibilityStatement.Role, Set<String>> allModuleRefNamesByRole = new EnumMap<>(PsiPackageAccessibilityStatement.Role.class);
    for (PsiPackageAccessibilityStatement moduleStatement : allModuleStatements) {
      PsiPackageAccessibilityStatement.Role moduleStatementRole = moduleStatement.getRole();
      Set<String> existingModuleRefNames = allModuleRefNamesByRole.get(moduleStatementRole);
      if (existingModuleRefNames == null) {
        allModuleRefNamesByRole.put(moduleStatementRole, new HashSet<>(moduleStatement.getModuleNames()));
        continue;
      }
      if (existingModuleRefNames.isEmpty()) continue;
      List<String> moduleRefNames = moduleStatement.getModuleNames();
      // if module ref is empty then the package is exported/opened to any module, so we are extending a current visibility
      if (moduleRefNames.isEmpty()) {
        existingModuleRefNames.clear();
      }
      else {
        existingModuleRefNames.addAll(moduleRefNames);
      }
    }
    removeRedundantModuleStatements(targetStatements, statementsToDelete, allModuleRefNamesByRole);
    statementsToCreate.addAll(createNewModuleStatements(targetPackage, allModuleRefNamesByRole));
  }

  private static void removeRedundantModuleStatements(@NotNull List<PsiPackageAccessibilityStatement> sourceStatements,
                                                      @NotNull List<PsiPackageAccessibilityStatement> statementsToDelete,
                                                      @NotNull Map<PsiPackageAccessibilityStatement.Role, Set<String>> allModuleRefNamesByRole) {
    Map<PsiPackageAccessibilityStatement.Role, PsiPackageAccessibilityStatement> moduleStatementsByRole = new EnumMap<>(
      PsiPackageAccessibilityStatement.Role.class);
    for (PsiPackageAccessibilityStatement sourceStatement : sourceStatements) {
      moduleStatementsByRole.put(sourceStatement.getRole(), sourceStatement);
    }
    for (var entry : moduleStatementsByRole.entrySet()) {
      PsiPackageAccessibilityStatement.Role role = entry.getKey();
      Set<String> moduleRefNames = allModuleRefNamesByRole.get(role);
      PsiPackageAccessibilityStatement moduleStatement = entry.getValue();
      if (moduleRefNames.equals(new HashSet<>(moduleStatement.getModuleNames()))) {
        allModuleRefNamesByRole.remove(role);
      }
      else {
        statementsToDelete.add(moduleStatement);
      }
    }
  }

  @NotNull
  private List<PsiPackageAccessibilityStatement> createNewModuleStatements(@NotNull PsiPackage targetPackage,
                                                                           @NotNull Map<PsiPackageAccessibilityStatement.Role, Set<String>> allModuleRefNamesByRole) {
    List<PsiPackageAccessibilityStatement> result = new SmartList<>();
    PsiElementFactory psiFactory = PsiElementFactory.getInstance(myProject);
    CodeStyleManager styleManager = CodeStyleManager.getInstance(myProject);
    for (var entry : allModuleRefNamesByRole.entrySet()) {
      String moduleStatementText = formModuleStatementText(entry.getKey(), new TreeSet<>(entry.getValue()), targetPackage.getQualifiedName());
      PsiPackageAccessibilityStatement moduleStatement =
        (PsiPackageAccessibilityStatement)psiFactory.createModuleStatementFromText(moduleStatementText, targetPackage);
      result.add((PsiPackageAccessibilityStatement)styleManager.reformat(moduleStatement));
    }
    return result;
  }

  @NotNull
  private static String formModuleStatementText(@NotNull PsiPackageAccessibilityStatement.Role role, @NotNull Set<String> moduleRefNames, @NotNull String packageName) {
    String roleText = null;
    if (role == PsiPackageAccessibilityStatement.Role.EXPORTS) {
      roleText = PsiKeyword.EXPORTS;
    }
    else if (role == PsiPackageAccessibilityStatement.Role.OPENS) {
      roleText = PsiKeyword.OPENS;
    }
    assert roleText != null;
    String moduleRefsText;
    if (moduleRefNames.isEmpty()) {
      moduleRefsText = "";
    }
    else {
      moduleRefsText = " to " + String.join(",", moduleRefNames);
    }
    return roleText + " " + packageName + moduleRefsText;
  }

  private static class DirectoryWithModuleStatements {
    @NotNull
    private final PsiDirectory myDir;
    @NotNull
    private final List<PsiPackageAccessibilityStatement> myModuleStatements;

    private DirectoryWithModuleStatements(@NotNull PsiDirectory dir, @NotNull List<PsiPackageAccessibilityStatement> moduleStatements) {
      myDir = dir;
      myModuleStatements = moduleStatements;
    }
  }
}
