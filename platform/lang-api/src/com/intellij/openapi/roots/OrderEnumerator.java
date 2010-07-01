/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Interface for convenient processing dependencies of a module or a project. Allows to process {@link OrderEntry}s and collect classes
 * and source roots.<p>
 *
 * Use {@link #orderEntries(com.intellij.openapi.module.Module)} or {@link ModuleRootModel#orderEntries()} to process dependencies of a module
 * and use {@link #orderEntries(com.intellij.openapi.project.Project)} to process dependencies of all modules in a project.<p>
 *
 * Note that all configuration methods modify {@link OrderEnumerator} instance instead of creating a new one.
 *
 * @since 10.0
 *
 *
 * @author nik
 */
public abstract class OrderEnumerator {
  /**
   * Skip test dependencies
   * @return this instance
   */
  public abstract OrderEnumerator productionOnly();

  /**
   * Skip runtime-only dependencies
   * @return this instance
   */
  public abstract OrderEnumerator compileOnly();

  /**
   * Skip compile-only dependencies
   * @return this instance
   */
  public abstract OrderEnumerator runtimeOnly();

  public abstract OrderEnumerator withoutSdk();
  public abstract OrderEnumerator withoutLibraries();
  public abstract OrderEnumerator withoutDepModules();
  public abstract OrderEnumerator withoutModuleSourceEntries();

  public OrderEnumerator librariesOnly() {
    return withoutSdk().withoutDepModules().withoutModuleSourceEntries();
  }

  public OrderEnumerator sdkOnly() {
    return withoutDepModules().withoutLibraries().withoutModuleSourceEntries();
  }

  /**
   * Recursively process modules on which the module depends
   * @return this instance
   */
  public abstract OrderEnumerator recursively();

  /**
   * Skip not exported dependencies. If this method is called after {@link #recursively()} direct non-exported dependencies won't be skipped 
   * @return this instance
   */
  public abstract OrderEnumerator exportedOnly();

  /**
   * Process only entries which satisfies the specified condition
   * @param condition filtering condition
   * @return this instance
   */
  public abstract OrderEnumerator satisfying(Condition<OrderEntry> condition);

  /**
   * Use <code>provider.getRootModel()</code> to process module dependencies
   * @param provider provider
   * @return this instance
   */
  public abstract OrderEnumerator using(@NotNull ModulesProvider provider);

  /**
   * @return {@link OrderRootsEnumerator} instance for processing classes roots
   */
  public abstract OrderRootsEnumerator classes();

  /**
   * @return {@link OrderRootsEnumerator} instance for processing source roots
   */
  public abstract OrderRootsEnumerator sources();

  /**
   * @return classes roots for all entries processed by this enumerator
   */
  public Collection<VirtualFile> getClassesRoots() {
    return classes().getRoots();
  }

  /**
   * @return source roots for all entries processed by this enumerator
   */
  public Collection<VirtualFile> getSourceRoots() {
    return sources().getRoots();
  }

  /**
   * @return list containing classes roots for all entries processed by this enumerator
   */
  public PathsList getPathsList() {
    return classes().getPathsList();
  }

  /**
   * Add classes roots for all entries processed by this enumerator
   * @param list list to append paths
   */
  public void collectPaths(PathsList list) {
    classes().collectPaths(list);
  }

  /**
   * @return list containing source roots for all entries processed by this enumerator
   */
  public PathsList getSourcePathsList() {
    return sources().getPathsList();
  }

  /**
   * Add source roots for all entries processed by this enumerator
   * @param list list to append paths
   */
  public void collectSourcePaths(PathsList list) {
    sources().collectPaths(list);
  }

  /**
   * Runs <code>processor.process()</code> for each entry processed by this enumerator.
   * @param processor processor
   */
  public abstract void forEach(@NotNull Processor<OrderEntry> processor);

  public abstract void forEachLibrary(@NotNull Processor<Library> processor);

  /**
   * Passes order entries to the specified visitor.
   *
   * @param policy       the visitor to accept.
   * @param initialValue the default value to be returned by the visit process.
   * @return the value returned by the visitor.
   * @see OrderEntry#accept(RootPolicy, Object)
   */
  public abstract <R> R process(@NotNull RootPolicy<R> policy, R initialValue);

  /**
   * Creates new enumerator instance to process dependencies of <code>module</code>
   * @param module module
   * @return new enumerator instance
   */
  public static OrderEnumerator orderEntries(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).orderEntries();
  }

  /**
   * Creates new enumerator instance to process dependencies of all modules in <code>project</code>. Only first level dependencies of
   * modules are processed so {@link #recursively()} option is ignored and {@link #withoutDepModules()} option is forced
   * @param project project
   * @return new enumerator instance
   */
  public static OrderEnumerator orderEntries(@NotNull Project project) {
    return ProjectRootManager.getInstance(project).orderEntries();
  }
}
