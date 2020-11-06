// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.PsiPackageAccessibilityStatement.Role;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Detector helps to find exports/opens associated with package
 * and create corresponding usage infos.
 */
class ModuleInfoUsageDetector {
  private final Project myProject;
  private final MoveDestination myMoveDestination;
  private final MultiMap<PsiDirectory, PsiClass> sourceClassesByDir;

  private final MultiMap<PsiJavaModule, PsiDirectory> myAbsentDirsByModuleDescriptor = MultiMap.create();

  ModuleInfoUsageDetector(@NotNull Project project, PsiElement @NotNull [] elementsToMove, @NotNull MoveDestination moveDestination) {
    myProject = project;
    myMoveDestination = moveDestination;
    sourceClassesByDir = groupClassesByDir(elementsToMove);
  }

  void detectModuleStatementsUsed(@NotNull List<UsageInfo> usageInfos, @NotNull MultiMap<PsiElement, String> conflicts) {
    if (sourceClassesByDir.isEmpty()) return;
    MultiMap<PsiJavaModule, PsiDirectory> sourceDirsByModuleDescriptor = groupDirsByModuleDescriptor(sourceClassesByDir.keySet());
    if (sourceDirsByModuleDescriptor.isEmpty()) return;
    MultiMap<PsiJavaModule, PsiDirectory> absentDirsByModuleDescriptor = MultiMap.create();
    myAbsentDirsByModuleDescriptor.clear();
    detectModuleStatementsUsed(sourceDirsByModuleDescriptor, usageInfos, conflicts, absentDirsByModuleDescriptor);
    myAbsentDirsByModuleDescriptor.putAllValues(absentDirsByModuleDescriptor);
  }

  /**
   * Handling the absent directories which are not visible during find usages operation
   * as they haven't been created yet.
   * Sample: we have a class pack1.A, we want to move it to pack1.pack2 which doesn't exist.
   */
  @NotNull
  List<UsageInfo> createUsageInfosForNewlyCreatedDirs() {
    if (myAbsentDirsByModuleDescriptor.isEmpty()) return Collections.emptyList();
    List<UsageInfo> result = new SmartList<>();
    detectModuleStatementsUsed(myAbsentDirsByModuleDescriptor, result, MultiMap.create(), MultiMap.create());
    return result;
  }

  private void detectModuleStatementsUsed(@NotNull MultiMap<PsiJavaModule, PsiDirectory> sourceDirsByModuleDescriptor,
                                          @NotNull List<UsageInfo> usageInfos,
                                          @NotNull MultiMap<PsiElement, String> conflicts,
                                          @NotNull MultiMap<PsiJavaModule, PsiDirectory> absentDirsByModuleDescriptor) {
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(myProject);
    // we work with package name not with package as a corresponding directory may not exist yet
    String targetPackageName = myMoveDestination.getTargetPackage().getQualifiedName();
    for (var entry : sourceDirsByModuleDescriptor.entrySet()) {
      PsiJavaModule sourceModuleDescriptor = entry.getKey();
      Collection<PsiDirectory> sourceDirs = entry.getValue();
      MultiMap<String, PsiPackageAccessibilityStatement> sourceExports = collectModuleStatements(sourceModuleDescriptor.getExports());
      MultiMap<String, PsiPackageAccessibilityStatement> sourceOpens = collectModuleStatements(sourceModuleDescriptor.getOpens());
      Map<PsiJavaModule, DirectoryWithModuleStatements> targetModuleStatementsByModuleDescriptor = new HashMap<>();
      for (PsiDirectory sourceDir : sourceDirs) {
        PsiDirectory targetDirectory = myMoveDestination.getTargetIfExists(sourceDir);
        if (targetDirectory == null) {
          absentDirsByModuleDescriptor.putValue(sourceModuleDescriptor, sourceDir);
          continue;
        }
        PsiJavaModule targetModuleDescriptor = JavaModuleGraphUtil.findDescriptorByElement(targetDirectory);
        String sourcePkgName = fileIndex.getPackageNameByDirectory(sourceDir.getVirtualFile());
        if (sourcePkgName == null) continue;
        List<PsiPackageAccessibilityStatement> sourceStatements = findModuleStatementsForPkg(sourcePkgName, sourceExports, sourceOpens);
        if (sourceStatements.isEmpty()) continue;
        // if a package doesn't contain any other classes except moved ones then we need to delete a corresponding export statement
        Collection<PsiClass> sourceClasses = sourceClassesByDir.get(sourceDir);
        if (dirContainsOnlyClasses(sourceDir, sourceClasses)) {
          sourceStatements.forEach(statement -> usageInfos.add(ModifyModuleStatementUsageInfo.createDeletionInfo(statement, sourceModuleDescriptor)));
        }
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
        List<PsiPackageAccessibilityStatement> targetPkgModuleStatements = findModuleStatementsForPkg(targetModuleDescriptor, targetPackageName);
        List<PsiPackageAccessibilityStatement> statementsToCreate = new SmartList<>();
        List<PsiPackageAccessibilityStatement> statementsToDelete = new SmartList<>();
        mergeModuleStatements(sourceModuleStatements, targetPkgModuleStatements, targetPackageName, statementsToCreate, statementsToDelete);
        if (statementsToCreate.isEmpty()) continue;
        if (!dirContainsOnlyClasses(statementEntry.getValue().myDir, Collections.emptyList())) {
          conflicts.put(targetModuleDescriptor.getNameIdentifier(), List.of(
            JavaRefactoringBundle.message("move.classes.or.packages.new.module.exports.conflict",
                                          StringUtil.htmlEmphasize(targetPackageName))));
        }
        statementsToDelete.stream().map(pkgStatement -> ModifyModuleStatementUsageInfo.createDeletionInfo(pkgStatement, targetModuleDescriptor))
          .forEach(usageInfos::add);
        statementsToCreate.stream().map(pkgStatement -> ModifyModuleStatementUsageInfo.createAdditionInfo(pkgStatement, targetModuleDescriptor))
          .forEach(usageInfos::add);
      }
    }
  }

  @NotNull
  private static MultiMap<PsiDirectory, PsiClass> groupClassesByDir(PsiElement @NotNull [] elementsToMove) {
    MultiMap<PsiDirectory, PsiClass> result = new MultiMap<>();
    for (PsiElement element : elementsToMove) {
      PsiClass psiClass = ObjectUtils.tryCast(element, PsiClass.class);
      // grant access only to public/protected classes
      if (psiClass == null || !classVisibleToOtherModules(psiClass)) continue;
      PsiJavaFile javaFile = ObjectUtils.tryCast(psiClass.getContainingFile(), PsiJavaFile.class);
      if (javaFile == null) continue;
      PsiDirectory directory = javaFile.getContainingDirectory();
      if (directory != null) {
        result.putValue(directory, psiClass);
      }
    }
    return result;
  }

  @NotNull
  private static MultiMap<PsiJavaModule, PsiDirectory> groupDirsByModuleDescriptor(@NotNull Set<PsiDirectory> dirs) {
    MultiMap<PsiJavaModule, PsiDirectory> result = new MultiMap<>();
    for (PsiDirectory directory : dirs) {
      PsiJavaModule moduleDescriptor = JavaModuleGraphUtil.findDescriptorByElement(directory);
      if (moduleDescriptor != null) {
        result.putValue(moduleDescriptor, directory);
      }
    }
    return result;
  }

  @NotNull
  private static List<PsiPackageAccessibilityStatement> findModuleStatementsForPkg(@NotNull PsiJavaModule moduleDescriptor,
                                                                                   @NotNull String packageName) {
    MultiMap<String, PsiPackageAccessibilityStatement> exports = collectModuleStatements(moduleDescriptor.getExports());
    MultiMap<String, PsiPackageAccessibilityStatement> opens = collectModuleStatements(moduleDescriptor.getOpens());
    return findModuleStatementsForPkg(packageName, exports, opens);
  }

  @NotNull
  private static List<PsiPackageAccessibilityStatement> findModuleStatementsForPkg(@NotNull String packageName,
                                                                                   @NotNull MultiMap<String, PsiPackageAccessibilityStatement> exports,
                                                                                   @NotNull MultiMap<String, PsiPackageAccessibilityStatement> opens) {
    List<PsiPackageAccessibilityStatement> result = new SmartList<>();
    result.addAll(exports.get(packageName));
    result.addAll(opens.get(packageName));
    return result;
  }

  @NotNull
  private static MultiMap<String, PsiPackageAccessibilityStatement> collectModuleStatements(@NotNull Iterable<PsiPackageAccessibilityStatement> statements) {
    MultiMap<String, PsiPackageAccessibilityStatement> result = new MultiMap<>();
    for (PsiPackageAccessibilityStatement pkgStatement : statements) {
      PsiJavaCodeReferenceElement packageReference = pkgStatement.getPackageReference();
      if (packageReference == null) continue;
      PsiPackage psiPackage = ObjectUtils.tryCast(packageReference.resolve(), PsiPackage.class);
      if (psiPackage == null) continue;
      result.putValue(psiPackage.getQualifiedName(), pkgStatement);
    }
    return result;
  }

  private static boolean dirContainsOnlyClasses(@NotNull PsiDirectory psiDirectory, @NotNull Collection<PsiClass> classes) {
    List<PsiClass> javaClassesInDir = new SmartList<>();
    for (PsiFile file : psiDirectory.getFiles()) {
      PsiJavaFile javaFile = ObjectUtils.tryCast(file, PsiJavaFile.class);
      if (javaFile == null) continue;
      for (PsiClass psiClass : javaFile.getClasses()) {
        if (classVisibleToOtherModules(psiClass)) {
          javaClassesInDir.add(psiClass);
        }

      }
    }
    javaClassesInDir.removeAll(new SmartHashSet<>(classes));
    return javaClassesInDir.isEmpty();
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
   * @param targetPackageName corresponds to the target package name
   * @param statementsToDelete statements to be deleted in the target module descriptor
   * @param statementsToCreate statements to be created in the target module descriptor
   */
  private void mergeModuleStatements(@NotNull List<PsiPackageAccessibilityStatement> sourceStatements,
                                     @NotNull List<PsiPackageAccessibilityStatement> targetStatements,
                                     @NotNull String targetPackageName,
                                     @NotNull List<PsiPackageAccessibilityStatement> statementsToCreate,
                                     @NotNull List<PsiPackageAccessibilityStatement> statementsToDelete) {
    List<PsiPackageAccessibilityStatement> allModuleStatements = new SmartList<>(sourceStatements);
    allModuleStatements.addAll(targetStatements);
    Map<Role, Set<String>> allModuleRefNamesByRole = new EnumMap<>(Role.class);
    for (PsiPackageAccessibilityStatement moduleStatement : allModuleStatements) {
      Role moduleStatementRole = moduleStatement.getRole();
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
    statementsToCreate.addAll(createNewModuleStatements(targetPackageName, allModuleRefNamesByRole));
  }

  private static boolean classVisibleToOtherModules(@NotNull PsiClass psiClass) {
    return psiClass.hasModifierProperty(PsiModifier.PUBLIC) || psiClass.hasModifierProperty(PsiModifier.PROTECTED);
  }

  private static void removeRedundantModuleStatements(@NotNull List<PsiPackageAccessibilityStatement> sourceStatements,
                                                      @NotNull List<PsiPackageAccessibilityStatement> statementsToDelete,
                                                      @NotNull Map<Role, Set<String>> allModuleRefNamesByRole) {
    Map<Role, PsiPackageAccessibilityStatement> moduleStatementsByRole = new EnumMap<>(Role.class);
    for (PsiPackageAccessibilityStatement sourceStatement : sourceStatements) {
      moduleStatementsByRole.put(sourceStatement.getRole(), sourceStatement);
    }
    for (var entry : moduleStatementsByRole.entrySet()) {
      Role role = entry.getKey();
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
  private List<PsiPackageAccessibilityStatement> createNewModuleStatements(@NotNull String targetPackageName,
                                                                           @NotNull Map<Role, Set<String>> allModuleRefNamesByRole) {
    List<PsiPackageAccessibilityStatement> result = new SmartList<>();
    PsiElementFactory psiFactory = PsiElementFactory.getInstance(myProject);
    CodeStyleManager styleManager = CodeStyleManager.getInstance(myProject);
    for (var entry : allModuleRefNamesByRole.entrySet()) {
      String moduleStatementText = formModuleStatementText(entry.getKey(), new TreeSet<>(entry.getValue()), targetPackageName);
      PsiPackageAccessibilityStatement moduleStatement =
        (PsiPackageAccessibilityStatement)psiFactory.createModuleStatementFromText(moduleStatementText, null);
      result.add((PsiPackageAccessibilityStatement)styleManager.reformat(moduleStatement));
    }
    return result;
  }

  @NotNull
  private static String formModuleStatementText(@NotNull Role role, @NotNull Set<String> moduleRefNames, @NotNull String packageName) {
    String roleText = null;
    if (role == Role.EXPORTS) {
      roleText = PsiKeyword.EXPORTS;
    }
    else if (role == Role.OPENS) {
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

  static class ModifyModuleStatementUsageInfo extends UsageInfo {
    private final PsiJavaModule myModuleDescriptor;
    private final ModifyingOperation myModifyingOperation;

    private ModifyModuleStatementUsageInfo(@NotNull PsiPackageAccessibilityStatement moduleStatement,
                                           @NotNull PsiJavaModule descriptor,
                                           @NotNull ModifyingOperation modifyingOperation) {
      super(moduleStatement);
      myModuleDescriptor = descriptor;
      myModifyingOperation = modifyingOperation;
    }

    @NotNull
    PsiJavaModule getModuleDescriptor() {
      return myModuleDescriptor;
    }

    @NotNull
    PsiPackageAccessibilityStatement getModuleStatement() {
      return (PsiPackageAccessibilityStatement)Objects.requireNonNull(getElement());
    }

    @NotNull
    static ModifyModuleStatementUsageInfo createAdditionInfo(@NotNull PsiPackageAccessibilityStatement moduleStatement,
                                                             @NotNull PsiJavaModule descriptor) {
      return new ModifyModuleStatementUsageInfo(moduleStatement, descriptor, ModifyingOperation.ADD);
    }

    @NotNull
    static ModifyModuleStatementUsageInfo createDeletionInfo(@NotNull PsiPackageAccessibilityStatement moduleStatement,
                                                             @NotNull PsiJavaModule descriptor) {
      return new ModifyModuleStatementUsageInfo(moduleStatement, descriptor, ModifyingOperation.DELETE);
    }

    boolean isAddition() {
      return myModifyingOperation == ModifyingOperation.ADD;
    }

    boolean isDeletion() {
      return myModifyingOperation == ModifyingOperation.DELETE;
    }

    private enum ModifyingOperation {
      ADD, DELETE
    }
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
