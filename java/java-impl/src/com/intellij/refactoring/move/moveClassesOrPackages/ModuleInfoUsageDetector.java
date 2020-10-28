// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.PsiPackageAccessibilityStatement.Role;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Detector helps to find exports/opens associated with package
 * and create corresponding usage infos.
 */
class ModuleInfoUsageDetector {
  private final Project myProject;
  private final PsiElement[] myElementsToMove;
  private final PsiPackage myTargetPackage;

  ModuleInfoUsageDetector(@NotNull Project project, PsiElement @NotNull [] elementsToMove, @NotNull PsiPackage targetPackage) {
    myProject = project;
    myElementsToMove = elementsToMove;
    myTargetPackage = targetPackage;
  }

  void detectModuleStatementsUsed(@NotNull List<UsageInfo> usageInfos, @NotNull MultiMap<PsiElement, String> conflicts) {
    MultiMap<PsiPackage, PsiClass> classesBySourcePackages = groupClassesBySourcePackages(myElementsToMove);
    if (classesBySourcePackages.isEmpty()) return;
    MultiMap<PsiJavaModule, PsiPackage> packagesByModuleDescriptor = groupModuleDescriptorsByPkg(classesBySourcePackages.keySet());
    if (packagesByModuleDescriptor.isEmpty()) return;
    PsiDirectory firstTargetDir = ArrayUtil.getFirstElement(myTargetPackage.getDirectories(GlobalSearchScope.projectScope(myProject)));
    PsiJavaModule targetModuleDescriptor = JavaModuleGraphUtil.findDescriptorByElement(firstTargetDir);
    List<PsiPackageAccessibilityStatement> sourcePkgModuleStatements = new SmartList<>();
    for (var entry : packagesByModuleDescriptor.entrySet()) {
      PsiJavaModule moduleDescriptor = entry.getKey();
      Collection<PsiPackage> packages = entry.getValue();
      MultiMap<PsiPackage, PsiPackageAccessibilityStatement> exports = collectModuleStatements(moduleDescriptor.getExports());
      MultiMap<PsiPackage, PsiPackageAccessibilityStatement> opens = collectModuleStatements(moduleDescriptor.getOpens());
      for (PsiPackage psiPackage : packages) {
        List<PsiPackageAccessibilityStatement>  moduleStatements = findModuleStatementsForPkg(psiPackage, exports, opens);
        if (moduleStatements.isEmpty()) continue;
        Collection<PsiClass> classes = classesBySourcePackages.get(psiPackage);
        // if a package doesn't contain any other classes except moved ones then we need to delete a corresponding export statement
        if (pkgContainsOnlyClasses(psiPackage, classes)) {
          moduleStatements.forEach(statement -> usageInfos.add(ModifyModuleStatementUsageInfo.createDeletionInfo(statement, moduleDescriptor)));
        }
        // so far we don't take into account a motion between separate JPMS-modules
        if (moduleDescriptor == targetModuleDescriptor) {
          sourcePkgModuleStatements.addAll(moduleStatements);
        }
        else if (targetModuleDescriptor != null) {
          classes.forEach(c -> conflicts.put(c, List.of(
            JavaRefactoringBundle.message("move.classes.or.packages.different.modules.exports.conflict",
                                          RefactoringUIUtil.getDescription(c, false),
                                          StringUtil.htmlEmphasize(moduleDescriptor.getName()),
                                          StringUtil.htmlEmphasize(targetModuleDescriptor.getName())))));
        }
      }
    }
    if (targetModuleDescriptor == null) return;
    List<PsiPackageAccessibilityStatement> targetPkgModuleStatements = findModuleStatementsForPkg(targetModuleDescriptor, myTargetPackage);
    List<PsiPackageAccessibilityStatement> statementsToCreate = new SmartList<>();
    List<PsiPackageAccessibilityStatement> statementsToDelete = new SmartList<>();
    mergeModuleStatements(sourcePkgModuleStatements, targetPkgModuleStatements, myTargetPackage, statementsToCreate, statementsToDelete);
    if (statementsToCreate.isEmpty()) return;
    if (!pkgContainsOnlyClasses(myTargetPackage, Collections.emptyList())) {
      conflicts.put(targetModuleDescriptor.getNameIdentifier(), List.of(
        JavaRefactoringBundle.message("move.classes.or.packages.new.module.exports.conflict",
                                      StringUtil.htmlEmphasize(myTargetPackage.getQualifiedName()))));
    }
    statementsToDelete.stream().map(pkgStatement -> ModifyModuleStatementUsageInfo.createDeletionInfo(pkgStatement, targetModuleDescriptor))
      .forEach(usageInfos::add);
    statementsToCreate.stream().map(pkgStatement -> ModifyModuleStatementUsageInfo.createAdditionInfo(pkgStatement, targetModuleDescriptor))
      .forEach(usageInfos::add);
  }

  @NotNull
  private static MultiMap<PsiPackage, PsiClass> groupClassesBySourcePackages(PsiElement @NotNull [] elementsToMove) {
    MultiMap<PsiPackage, PsiClass> classesByPackage = new MultiMap<>();
    for (PsiElement element : elementsToMove) {
      if (!(element instanceof PsiClass)) continue;
      PsiClass psiClass = (PsiClass)element;
      // grant access only to public/protected classes
      if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC) && !psiClass.hasModifierProperty(PsiModifier.PROTECTED)) continue;
      PsiJavaFile javaFile = ObjectUtils.tryCast(psiClass.getContainingFile(), PsiJavaFile.class);
      if (javaFile == null) continue;
      PsiPackageStatement packageStatement = javaFile.getPackageStatement();
      if (packageStatement == null) continue;
      PsiPackage psiPackage = (PsiPackage)packageStatement.getPackageReference().resolve();
      if (psiPackage != null) {
        classesByPackage.putValues(psiPackage, List.of(javaFile.getClasses()));
      }
    }
    return classesByPackage;
  }

  @NotNull
  private MultiMap<PsiJavaModule, PsiPackage> groupModuleDescriptorsByPkg(@NotNull Set<PsiPackage> packages) {
    MultiMap<PsiJavaModule, PsiPackage> result = new MultiMap<>();
    for (PsiPackage psiPackage : packages) {
      PsiDirectory firstDir = ArrayUtil.getFirstElement(psiPackage.getDirectories(GlobalSearchScope.projectScope(myProject)));
      if (firstDir == null) continue;
      PsiJavaModule moduleDescriptor = JavaModuleGraphUtil.findDescriptorByElement(firstDir);
      if (moduleDescriptor != null) {
        result.putValue(moduleDescriptor, psiPackage);
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

  private boolean pkgContainsOnlyClasses(@NotNull PsiPackage psiPackage, @NotNull Collection<PsiClass> classes) {
    List<PsiClass> result = new SmartList<>(psiPackage.getClasses(GlobalSearchScope.projectScope(myProject)));
    result.removeAll(new HashSet<>(classes));
    return result.isEmpty();
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
   */
  private void mergeModuleStatements(@NotNull List<PsiPackageAccessibilityStatement> first,
                                     @NotNull List<PsiPackageAccessibilityStatement> second,
                                     @NotNull PsiPackage newPackage,
                                     @NotNull List<PsiPackageAccessibilityStatement> statementsToCreate,
                                     @NotNull List<PsiPackageAccessibilityStatement> statementsToDelete) {
    List<PsiPackageAccessibilityStatement> allModuleStatements = new SmartList<>(first);
    allModuleStatements.addAll(second);
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
    removeRedundantModuleStatements(second, statementsToDelete, allModuleRefNamesByRole);
    statementsToCreate.addAll(createNewModuleStatements(newPackage, allModuleRefNamesByRole));
  }

  private static void removeRedundantModuleStatements(@NotNull List<PsiPackageAccessibilityStatement> sourceStatements,
                                                      @NotNull List<PsiPackageAccessibilityStatement> statementsToDelete,
                                                      @NotNull Map<Role, Set<String>> allModuleRefNamesByRole) {
    Map<Role, PsiPackageAccessibilityStatement> moduleStatementsByRole = sourceStatements.stream()
      .collect(Collectors.toMap(statement -> statement.getRole(), statement -> statement, (stmt1, stmt2) -> stmt1, () -> new EnumMap<>(Role.class)));
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
  private List<PsiPackageAccessibilityStatement> createNewModuleStatements(@NotNull PsiPackage newPackage,
                                                                           @NotNull Map<Role, Set<String>> allModuleRefNamesByRole) {
    List<PsiPackageAccessibilityStatement> result = new SmartList<>();
    PsiElementFactory psiFactory = PsiElementFactory.getInstance(myProject);
    for (var entry : allModuleRefNamesByRole.entrySet()) {
      String moduleStatementText = formModuleStatementText(entry.getKey(), new TreeSet<>(entry.getValue()), newPackage.getQualifiedName());
      PsiPackageAccessibilityStatement moduleStatement =
        (PsiPackageAccessibilityStatement)psiFactory.createModuleStatementFromText(moduleStatementText, newPackage);
      result.add(moduleStatement);
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
