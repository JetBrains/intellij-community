// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collection;
import java.util.List;

public final class JavaProjectModelModificationServiceImpl extends JavaProjectModelModificationService {
  private final Project myProject;

  public JavaProjectModelModificationServiceImpl(Project project) {
    myProject = project;
  }

  @Override
  public Promise<Void> addDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope, boolean exported) {
    for (JavaProjectModelModifier modifier : getModelModifiers()) {
      Promise<Void> promise = modifier.addModuleDependency(from, to, scope, exported);
      if (promise != null) {
        return promise;
      }
    }
    return Promises.rejectedPromise();
  }

  @Override
  public Promise<Void> addDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope, boolean exported) {
    for (JavaProjectModelModifier modifier : getModelModifiers()) {
      Promise<Void> promise = modifier.addLibraryDependency(from, library, scope, exported);
      if (promise != null) {
        return promise;
      }
    }
    return Promises.rejectedPromise();
  }

  @Override
  public Promise<Void> addDependency(@NotNull Collection<? extends Module> from, @NotNull ExternalLibraryDescriptor libraryDescriptor, @NotNull DependencyScope scope) {
    for (JavaProjectModelModifier modifier : getModelModifiers()) {
      Promise<Void> promise = modifier.addExternalLibraryDependency(from, libraryDescriptor, scope);
      if (promise != null) {
        return promise;
      }
    }
    return Promises.rejectedPromise();
  }

  @Override
  public Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel languageLevel, boolean modifySource) {
    if (modifySource) {
      for (JavaProjectModelModifier modifier : getModelModifiers()) {
        Promise<Void> promise = modifier.changeLanguageLevel(module, languageLevel);
        if (promise != null) {
          return promise;
        }
      }
    }
    else {
      final LanguageLevel moduleLevel = LanguageLevelUtil.getCustomLanguageLevel(module);
      if (moduleLevel != null && JavaSdkUtil.isLanguageLevelAcceptable(myProject, module, languageLevel)) {
        final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        rootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(languageLevel);
        rootModel.commit();
      }
      else {
        LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(languageLevel);
        ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(EmptyRunnable.INSTANCE, RootsChangeRescanningInfo.TOTAL_RESCAN);
      }
      return Promises.resolvedPromise(null);
    }

    return Promises.rejectedPromise();
  }

  private List<JavaProjectModelModifier> getModelModifiers() {
    return JavaProjectModelModifier.EP_NAME.getExtensionList(myProject);
  }
}