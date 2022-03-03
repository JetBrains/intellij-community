// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class JavaRunConfigurationModule extends RunConfigurationModule {
  private final boolean myClassesInLibraries;

  public JavaRunConfigurationModule(@NotNull Project project, boolean classesInLibs) {
    super(project);

    myClassesInLibraries = classesInLibs;
  }

  public @Nullable PsiClass findClass(final String qualifiedName) {
    if (qualifiedName == null) return null;
    Project project = getProject();
    GlobalSearchScope searchScope = getSearchScope();
    PsiClass mainClass = JavaExecutionUtil.findMainClass(project, qualifiedName, searchScope);
    if (mainClass == null && !PsiNameHelper.getInstance(project).isQualifiedName(qualifiedName)) {
      return findClass(StringUtil.getShortName(qualifiedName), StringUtil.getPackageName(qualifiedName), project, searchScope);
    }
    return mainClass;
  }

  private static PsiClass findClass(String shortName, String packageName, Project project, GlobalSearchScope searchScope) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
    if (aPackage != null) {
      int dollarIdx = shortName.indexOf("$");
      String topLevelClassName = dollarIdx > 0 && dollarIdx < shortName.length() - 1 ? shortName.substring(0, dollarIdx) : shortName;
      PsiClass topLevelClass = ContainerUtil.find(aPackage.getClasses(searchScope), aClass -> topLevelClassName.equals(aClass.getName()));
      if (topLevelClass != null && !topLevelClassName.equals(shortName)) {
        String innerClassName = shortName.substring(dollarIdx + 1);
        return ClassUtil.findPsiClass(PsiManager.getInstance(project), innerClassName, topLevelClass, true);
      }
      return topLevelClass;
    }

    if (packageName.isEmpty()) {
      assert false : "Default package doesn't exist";
      return null;
    }

    PsiClass topClass = findClass(StringUtil.getShortName(packageName), StringUtil.getPackageName(packageName), project, searchScope);
    if (topClass != null) {
      return topClass.findInnerClassByName(shortName, true);
    }
    return null;
  }

  @NotNull
  public GlobalSearchScope getSearchScope() {
    Module module = getModule();
    if (module != null) {
      return myClassesInLibraries ? module.getModuleRuntimeScope(true) : GlobalSearchScope.moduleWithDependenciesScope(module);
    }
    else {
      return myClassesInLibraries ? GlobalSearchScope.allScope(getProject()) : GlobalSearchScope.projectScope(getProject());
    }
  }

  public static Collection<Module> getModulesForClass(@NotNull Project project, @Nullable String className) {
    if (project.isDefault()) {
      return Arrays.asList(ModuleManager.getInstance(project).getModules());
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final PsiClass[] possibleClasses = className == null ? PsiClass.EMPTY_ARRAY : JavaPsiFacade.getInstance(project).findClasses(className, GlobalSearchScope.projectScope(project));
    final Set<Module> modules = new HashSet<>();
    for (PsiClass aClass : possibleClasses) {
      Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
      if (module != null) {
        modules.add(module);
      }
    }
    if (modules.isEmpty()) {
      return Arrays.asList(ModuleManager.getInstance(project).getModules());
    }
    else {
      final Set<Module> result = new HashSet<>();
      for (Module module : modules) {
        ModuleUtilCore.collectModulesDependsOn(module, result);
      }
      return result;
    }
  }

  public PsiClass findNotNullClass(final String className) throws RuntimeConfigurationWarning {
    final PsiClass psiClass = findClass(className);
    if (psiClass == null) {
      throw new RuntimeConfigurationWarning(
        ExecutionBundle.message("class.not.found.in.module.error.message", className, getModuleName())) {
        @Override
        public boolean shouldShowInDumbMode() {
          return false;
        }
      };
    }
    return psiClass;
  }

  public PsiClass checkModuleAndClassName(final String className, final @NlsContexts.DialogMessage String expectedClassMessage) throws RuntimeConfigurationException {
    checkForWarning();
    return checkClassName(className, expectedClassMessage);
  }

  public PsiClass checkClassName(final String className, final @NlsContexts.DialogMessage String errorMessage) throws RuntimeConfigurationException {
    if (className == null || className.length() == 0) {
      throw new RuntimeConfigurationError(errorMessage);
    }
    return findNotNullClass(className);
  }
}
