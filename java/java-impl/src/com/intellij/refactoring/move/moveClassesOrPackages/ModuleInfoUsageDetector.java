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
import com.intellij.psi.search.GlobalSearchScope;
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
  private final PsiElement[] myElementsToMove;
  private final MoveDestination myMoveDestination;

  ModuleInfoUsageDetector(@NotNull Project project, PsiElement @NotNull [] elementsToMove, @NotNull MoveDestination moveDestination) {
    myProject = project;
    myElementsToMove = elementsToMove;
    myMoveDestination = moveDestination;
  }

  void detectModuleStatementsUsed(@NotNull List<UsageInfo> usageInfos, @NotNull MultiMap<PsiElement, String> conflicts) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    PsiPackage targetPackage = psiFacade.findPackage(myMoveDestination.getTargetPackage().getQualifiedName());
    if (targetPackage == null) return;
    MultiMap<PsiDirectory, PsiClass> sourceClassesByDir = groupClassesByDir(myElementsToMove);
    if (sourceClassesByDir.isEmpty()) return;
    MultiMap<PsiJavaModule, PsiDirectory> sourceDirsByModuleDescriptor = groupDirsByModuleDescriptor(sourceClassesByDir.keySet());
    if (sourceDirsByModuleDescriptor.isEmpty()) return;
    PsiDirectory targetDirectory = null;
    for (PsiDirectory dir : targetPackage.getDirectories(GlobalSearchScope.projectScope(myProject))) {
      targetDirectory = myMoveDestination.getTargetDirectory(dir);
      if (targetDirectory != null) {
        break;
      }
    }
    if (targetDirectory == null) return;
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(myProject);
    PsiJavaModule targetModuleDescriptor = JavaModuleGraphUtil.findDescriptorByElement(targetDirectory);
    List<PsiPackageAccessibilityStatement> sourcePkgModuleStatements = new SmartList<>();
    for (var entry : sourceDirsByModuleDescriptor.entrySet()) {
      PsiJavaModule sourceModuleDescriptor = entry.getKey();
      Collection<PsiDirectory> sourceDirs = entry.getValue();
      MultiMap<PsiPackage, PsiPackageAccessibilityStatement> exports = collectModuleStatements(sourceModuleDescriptor.getExports());
      MultiMap<PsiPackage, PsiPackageAccessibilityStatement> opens = collectModuleStatements(sourceModuleDescriptor.getOpens());
      for (PsiDirectory sourceDir : sourceDirs) {
        String packageName = fileIndex.getPackageNameByDirectory(sourceDir.getVirtualFile());
        if (packageName == null) continue;
        PsiPackage psiPackage = psiFacade.findPackage(packageName);
        if (psiPackage == null) continue;
        List<PsiPackageAccessibilityStatement> moduleStatements = findModuleStatementsForPkg(psiPackage, exports, opens);
        if (moduleStatements.isEmpty()) continue;
        // if a package doesn't contain any other classes except moved ones then we need to delete a corresponding export statement
        Collection<PsiClass> sourceClasses = sourceClassesByDir.get(sourceDir);
        if (dirContainsOnlyClasses(sourceDir, sourceClasses)) {
          moduleStatements.forEach(statement -> usageInfos.add(ModifyModuleStatementUsageInfo.createDeletionInfo(statement, sourceModuleDescriptor)));
        }
        // so far we don't take into account a motion between separate JPMS-modules
        if (sourceModuleDescriptor == targetModuleDescriptor) {
          sourcePkgModuleStatements.addAll(moduleStatements);
        }
        else if (targetModuleDescriptor != null) {
          sourceClasses.forEach(c -> conflicts.put(c, List.of(
            JavaRefactoringBundle.message("move.classes.or.packages.different.modules.exports.conflict",
                                          RefactoringUIUtil.getDescription(c, false),
                                          StringUtil.htmlEmphasize(sourceModuleDescriptor.getName()),
                                          StringUtil.htmlEmphasize(targetModuleDescriptor.getName())))));
        }
      }
    }
    if (targetModuleDescriptor == null) return;
    List<PsiPackageAccessibilityStatement> targetPkgModuleStatements = findModuleStatementsForPkg(targetModuleDescriptor, targetPackage);
    List<PsiPackageAccessibilityStatement> statementsToCreate = new SmartList<>();
    List<PsiPackageAccessibilityStatement> statementsToDelete = new SmartList<>();
    mergeModuleStatements(sourcePkgModuleStatements, targetPkgModuleStatements, targetPackage, statementsToCreate, statementsToDelete);
    if (statementsToCreate.isEmpty()) return;
    if (!dirContainsOnlyClasses(targetDirectory, Collections.emptyList())) {
      conflicts.put(targetModuleDescriptor.getNameIdentifier(), List.of(
        JavaRefactoringBundle.message("move.classes.or.packages.new.module.exports.conflict",
                                      StringUtil.htmlEmphasize(targetPackage.getQualifiedName()))));
    }
    statementsToDelete.stream().map(pkgStatement -> ModifyModuleStatementUsageInfo.createDeletionInfo(pkgStatement, targetModuleDescriptor))
      .forEach(usageInfos::add);
    statementsToCreate.stream().map(pkgStatement -> ModifyModuleStatementUsageInfo.createAdditionInfo(pkgStatement, targetModuleDescriptor))
      .forEach(usageInfos::add);
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
                                                                                   @NotNull PsiPackage psiPackage) {
    MultiMap<PsiPackage, PsiPackageAccessibilityStatement> exports = collectModuleStatements(moduleDescriptor.getExports());
    MultiMap<PsiPackage, PsiPackageAccessibilityStatement> opens = collectModuleStatements(moduleDescriptor.getOpens());
    return findModuleStatementsForPkg(psiPackage, exports, opens);
  }

  @NotNull
  private static List<PsiPackageAccessibilityStatement> findModuleStatementsForPkg(@NotNull PsiPackage psiPackage,
                                                                                   @NotNull MultiMap<PsiPackage, PsiPackageAccessibilityStatement> exports,
                                                                                   @NotNull MultiMap<PsiPackage, PsiPackageAccessibilityStatement> opens) {
    List<PsiPackageAccessibilityStatement> result = new SmartList<>();
    result.addAll(exports.get(psiPackage));
    result.addAll(opens.get(psiPackage));
    return result;
  }

  @NotNull
  private static MultiMap<PsiPackage, PsiPackageAccessibilityStatement> collectModuleStatements(@NotNull Iterable<PsiPackageAccessibilityStatement> statements) {
    MultiMap<PsiPackage, PsiPackageAccessibilityStatement> result = new MultiMap<>();
    for (PsiPackageAccessibilityStatement pkgStatement : statements) {
      PsiJavaCodeReferenceElement packageReference = pkgStatement.getPackageReference();
      if (packageReference == null) continue;
      PsiPackage psiPackage = ObjectUtils.tryCast(packageReference.resolve(), PsiPackage.class);
      if (psiPackage == null) continue;
      result.putValue(psiPackage, pkgStatement);
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
   * @param targetStatements corresponds to the statements in target directory
   * @param targetPackage corresponds the target package
   * @param statementsToDelete statements to be deleted in target module descriptor
   * @param statementsToCreate statements to be created in target module descriptor
   */
  private void mergeModuleStatements(@NotNull List<PsiPackageAccessibilityStatement> sourceStatements,
                                     @NotNull List<PsiPackageAccessibilityStatement> targetStatements,
                                     @NotNull PsiPackage targetPackage,
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
    statementsToCreate.addAll(createNewModuleStatements(targetPackage, allModuleRefNamesByRole));
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
  private List<PsiPackageAccessibilityStatement> createNewModuleStatements(@NotNull PsiPackage targetPackage,
                                                                           @NotNull Map<Role, Set<String>> allModuleRefNamesByRole) {
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
}
