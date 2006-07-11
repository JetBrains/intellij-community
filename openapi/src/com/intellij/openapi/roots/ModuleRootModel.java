/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface providing root information model for a given module.
 *
 * @author dsl
 */
public interface ModuleRootModel {
  /**
   * Returns the module to which the model belongs.
   *
   * @return the module instance.
   */
  @NotNull
  Module getModule();

  /**
   * Use this method to obtain all content entries of a module. Entries are given in
   * lexicographical order of their paths.
   *
   * @return list of content entries for this module
   * @see ContentEntry
   */
  ContentEntry[] getContentEntries();

  /**
   * Use this method to obtain order of roots of a module. Order of entries is important.
   *
   * @return list of order entries for this module
   */
  OrderEntry[] getOrderEntries();

  /**
   * Returns the JDK used by the module.
   *
   * @return either module-specific or inherited JDK
   * @see #isJdkInherited()
   */
  @Nullable
  ProjectJdk getJdk();

  /**
   * Returns <code>true</code> if JDK for this module is inherited from a project.
   *
   * @return true if the JDK is inherited, false otherwise
   * @see ProjectRootManager#getProjectJdk()
   * @see ProjectRootManager#setProjectJdk(com.intellij.openapi.projectRoots.ProjectJdk)
   */
  boolean isJdkInherited();


  /**
   * Returns <code>true</code> if compiler output for this module is inherited from a project
   * @return true if compiler output path is inherited, false otherwise
   */
  boolean isCompilerOutputPathInherited();

  /**
   * Returns an array of content roots from all content entries. A helper method.
   *
   * @return the array of content roots.
   * @see #getContentEntries()
   */
  @NotNull VirtualFile[] getContentRoots();

  /**
   * Returns an array of content root urls from all content entries. A helper method.
   *
   * @return the array of content root URLs.
   * @see #getContentEntries()
   */
  @NotNull String[] getContentRootUrls();

  /**
   * Returns an array of exclude roots from all content entries. A helper method.
   *
   * @return the array of excluded roots.
   * @see #getContentEntries()
   */
  @NotNull VirtualFile[] getExcludeRoots();

  /**
   * Returns an array of exclude root urls from all content entries. A helper method.
   *
   * @return the array of excluded root URLs.
   * @see #getContentEntries()
   */
  @NotNull String[] getExcludeRootUrls();

  /**
   * Returns an array of source roots from all content entries. A helper method.
   *
   * @return the array of source roots.
   * @see #getContentEntries()
   */
  @NotNull VirtualFile[] getSourceRoots();

  /**
   * Returns an array of source root urls from all content entries. A helper method.
   *
   * @return the array of source root URLs.
   * @see #getContentEntries()
   */
  @NotNull String[] getSourceRootUrls();

  /**
   * Returns a compiler output path for production sources of the module, if it is valid.
   *
   * @return the compile output path, or null if one is not valid.
   */
  @Nullable
  VirtualFile getCompilerOutputPath();

  /**
   * Returns a compiler output path url for production sources of the module.
   *
   * @return the compiler output path URL, or null if it has never been set.
   */
  @Nullable
  String getCompilerOutputPathUrl();

  /**
   * Returns a compiler output path for test sources of the module, if it is valid.
   *
   * @return the compile output path for the test sources, or null if one is not valid.
   */
  @Nullable
  VirtualFile getCompilerOutputPathForTests();

  /**
   * Returns a compiler output path url for test sources of the module.
   *
   * @return the compiler output path URL, or null if it has never been set.
   */
  String getCompilerOutputPathForTestsUrl();

  /**
   * Returns an exploded directory path of the module, if it is valid.
   *
   * @return exploded directory path of the module, or null if not applicable or not set.
   */
  @Nullable
  VirtualFile getExplodedDirectory();

  /**
   * Returns an exploded directory path url.
   *
   * @return exploded directory path url, or null if it has never been set
   *         or if not applicable for this module.
   */
  @Nullable
  String getExplodedDirectoryUrl();

  /**
   * Passes all order entries in the module to the specified visitor.
   *
   * @param policy       the visitor to accept.
   * @param initialValue the default value to be returned by the visit process.
   * @return the value returned by the visitor.
   * @see OrderEntry#accept(RootPolicy, Object)
   */
  <R> R processOrder(RootPolicy<R> policy, R initialValue);

  /**
   * Returns list of module names <i>this module</i> depends on.
   *
   * @return the list of module names this module depends on.
   */
  @NotNull String[] getDependencyModuleNames();

  /**
   * Returns the list of javadoc roots for the module.
   *
   * @return the array of javadoc roots.
   */
  @NotNull VirtualFile[] getJavadocPaths();

  /**
   * Returns the list of javadoc root URLs for the module.
   *
   * @return the array of javadoc root URLs.
   */
  @NotNull String[] getJavadocUrls();
}
