// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.safeDelete.ModuleInfoSafeDeleteUsageDetector;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Detector helps to find exports/opens associated with package
 * and create corresponding usage infos.
 */
public abstract class ModuleInfoUsageDetector {
  protected final Project myProject;
  protected final MultiMap<PsiDirectory, PsiClass> mySourceClassesByDir;

  protected ModuleInfoUsageDetector(@NotNull Project project, PsiElement @NotNull [] elements) {
    myProject = project;
    mySourceClassesByDir = groupClassesByDir(elements);
  }

  @NotNull
  public static ModuleInfoUsageDetector createModifyUsageInstance(@NotNull Project project, PsiElement @NotNull [] elementsToMove, @NotNull MoveDestination moveDestination) {
    return new ModuleInfoModifyUsageDetector(project, elementsToMove, moveDestination);
  }

  @NotNull
  public static ModuleInfoUsageDetector createSafeDeleteUsageInstance(@NotNull Project project, PsiElement @NotNull [] elementsToDelete) {
    return new ModuleInfoSafeDeleteUsageDetector(project, elementsToDelete);
  }

  public abstract void detectModuleStatementsUsed(@NotNull List<? super UsageInfo> usageInfos, @NotNull MultiMap<PsiElement, String> conflicts);

  /**
   * Handling the absent directories which haven't been created yet during find usages operation.
   * Sample: we have a class pack1.A, we want to move it to pack1.pack2 which doesn't exist.
   */
  @NotNull
  public abstract List<UsageInfo> createUsageInfosForNewlyCreatedDirs();

  @NotNull
  private static MultiMap<PsiDirectory, PsiClass> groupClassesByDir(PsiElement @NotNull [] elementsToMove) {
    PsiElement firstElement = ArrayUtil.getFirstElement(elementsToMove);
    if (firstElement == null || !PsiUtil.isLanguageLevel9OrHigher(firstElement)) return MultiMap.empty();
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
  protected static MultiMap<PsiJavaModule, PsiDirectory> groupDirsByModuleDescriptor(@NotNull Set<PsiDirectory> dirs) {
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
  protected static MultiMap<PsiPackage, PsiPackageAccessibilityStatement> collectModuleStatements(@NotNull Iterable<PsiPackageAccessibilityStatement> statements) {
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

  @NotNull
  protected static List<PsiPackageAccessibilityStatement> findModuleStatementsForPkg(@NotNull PsiPackage psiPackage,
                                                                                     @NotNull MultiMap<PsiPackage, PsiPackageAccessibilityStatement> exports,
                                                                                     @NotNull MultiMap<PsiPackage, PsiPackageAccessibilityStatement> opens) {
    List<PsiPackageAccessibilityStatement> result = new SmartList<>();
    result.addAll(exports.get(psiPackage));
    result.addAll(opens.get(psiPackage));
    return result;
  }

  protected static boolean dirContainsOnlyClasses(@NotNull PsiDirectory psiDirectory, @NotNull Collection<PsiClass> classes) {
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

  private static boolean classVisibleToOtherModules(@NotNull PsiClass psiClass) {
    return psiClass.hasModifierProperty(PsiModifier.PUBLIC) || psiClass.hasModifierProperty(PsiModifier.PROTECTED);
  }
}
