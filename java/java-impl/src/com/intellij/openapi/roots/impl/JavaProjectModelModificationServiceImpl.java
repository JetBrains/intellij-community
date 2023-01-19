/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

public class JavaProjectModelModificationServiceImpl extends JavaProjectModelModificationService {
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

  private JavaProjectModelModifier @NotNull [] getModelModifiers() {
    return JavaProjectModelModifier.EP_NAME.getExtensions(myProject);
  }
}