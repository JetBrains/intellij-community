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
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.util.Collection;

/**
 * Register implementation of this extension to support custom dependency management system for {@link JavaProjectModelModificationService}.
 * The default implementation which modify IDEA's project model directly is registered as the last extension so it'll be executed if all other
 * extensions refuse to handle modification by returning {@code null}.
 *
 * @see JavaProjectModelModificationService
 *
 * @author nik
 */
public abstract class JavaProjectModelModifier {
  public static final ExtensionPointName<JavaProjectModelModifier> EP_NAME = ExtensionPointName.create("com.intellij.projectModelModifier");

  /**
   * Implementation of this method should add dependency from module {@code from} to module {@code to} with scope {@code scope} accordingly
   * to this dependencies management system. If it takes some time to propagate changes in the external project configuration to IDEA's
   * project model the method may schedule this work for asynchronous execution and return {@link Promise} instance which will be fulfilled
   * when the work is done.
   * @return {@link Promise} instance if dependencies between these modules can be handled by this dependencies management system or
   * {@code null} otherwise
   */
  @Nullable
  @SuppressWarnings("deprecation")
  public Promise<Void> addModuleDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope, boolean exported) {
    return addModuleDependency(from, to, scope);
  }

  /**
   * Implementation of this method should add dependency from module {@code from} to {@code library} with scope {@code scope} accordingly
   * to this dependencies management system. If it takes some time to propagate changes in the external project configuration to IDEA's
   * project model the method may schedule this work for asynchronous execution and return {@link Promise} instance which will be fulfilled
   * when the work is done.
   *
   * @return {@link Promise} instance if dependencies between these modules can be handled by this dependencies management system or
   * {@code null} otherwise
   */
  @Nullable
  @SuppressWarnings("deprecation")
  public Promise<Void> addLibraryDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope, boolean exported) {
    return addLibraryDependency(from, library, scope);
  }

  /**
   * Implementation of this method should add dependency from modules {@code modules} to an external library with scope {@code scope} accordingly
   * to this dependencies management system. If it takes some time to propagate changes in the external project configuration to IDEA's
   * project model the method may schedule this work for asynchronous execution and return {@link Promise} instance which will be fulfilled
   * when the work is done.
   *
   * @return {@link Promise} instance if dependencies of these modules can be handled by this dependencies management system or
   * {@code null} otherwise
   */
  @Nullable
  public abstract Promise<Void> addExternalLibraryDependency(@NotNull Collection<Module> modules,
                                                             @NotNull ExternalLibraryDescriptor descriptor,
                                                             @NotNull DependencyScope scope);

  /**
   * Implementation of this method should set language level for module {@code module} to the specified value accordingly
   * to this dependencies management system. If it takes some time to propagate changes in the external project configuration to IDEA's
   * project model the method may schedule this work for asynchronous execution and return {@link Promise} instance which will be fulfilled
   * when the work is done.
   *
   * @return {@link Promise} instance if language level can be set by this dependencies management system or {@code null} otherwise
   */
  @Nullable
  public abstract Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel level);

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated implement {@link #addModuleDependency(Module, Module, DependencyScope, boolean)} (to be removed in IDEA 2019) */
  @SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
  public Promise<Void> addModuleDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope) {
    throw new UnsupportedOperationException("#addModuleDependency(Module, Module, DependencyScope) called on " + this);
  }

  /** @deprecated implement {@link #addLibraryDependency(Module, Library, DependencyScope, boolean)} (to be removed in IDEA 2019) */
  @SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
  public Promise<Void> addLibraryDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope) {
    throw new UnsupportedOperationException("#addLibraryDependency(Module, Library, DependencyScope) called on " + this);
  }
  //</editor-fold>
}