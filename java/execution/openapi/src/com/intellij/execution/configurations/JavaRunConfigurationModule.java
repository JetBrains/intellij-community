/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author spleaner
 */
public class JavaRunConfigurationModule extends RunConfigurationModule {

  private final boolean myClassesInLibraries;

  public JavaRunConfigurationModule(@NotNull Project project, boolean classesInLibs) {
    super(project);

    myClassesInLibraries = classesInLibs;
  }

  @Nullable
  public PsiClass findClass(final String qualifiedName) {
    if (qualifiedName == null) return null;
    return JavaExecutionUtil.findMainClass(getProject(), qualifiedName, getSearchScope());
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
    final Set<Module> modules = new THashSet<>();
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
        ExecutionBundle.message("class.not.found.in.module.error.message", className, getModuleName()));
    }
    return psiClass;
  }

  public PsiClass checkModuleAndClassName(final String className, final String expectedClassMessage) throws RuntimeConfigurationException {
    checkForWarning();
    return checkClassName(className, expectedClassMessage);
  }


  public PsiClass checkClassName(final String className, final String errorMessage) throws RuntimeConfigurationException {
    if (className == null || className.length() == 0) {
      throw new RuntimeConfigurationError(errorMessage);
    }
    return findNotNullClass(className);
  }
}
