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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.JavaProjectModelModifier;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collection;

/**
 * @author nik
 */
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
  public Promise<Void> addDependency(@NotNull Collection<Module> from, @NotNull ExternalLibraryDescriptor libraryDescriptor, @NotNull DependencyScope scope) {
    for (JavaProjectModelModifier modifier : getModelModifiers()) {
      Promise<Void> promise = modifier.addExternalLibraryDependency(from, libraryDescriptor, scope);
      if (promise != null) {
        return promise;
      }
    }
    return Promises.rejectedPromise();
  }

  @Override
  public Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel languageLevel) {
    for (JavaProjectModelModifier modifier : getModelModifiers()) {
      Promise<Void> promise = modifier.changeLanguageLevel(module, languageLevel);
      if (promise != null) {
        return promise;
      }
    }
    return Promises.rejectedPromise();
  }

  @NotNull
  private JavaProjectModelModifier[] getModelModifiers() {
    return JavaProjectModelModifier.EP_NAME.getExtensions(myProject);
  }
}