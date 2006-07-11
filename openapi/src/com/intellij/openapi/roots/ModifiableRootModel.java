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
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Model of roots that should be used by clients to modify module roots.
 *
 * @author dsl
 * @see ModuleRootManager#getModifiableModel()
 */
public interface ModifiableRootModel extends ModuleRootModel {
  /**
   * Adds the specified directory as a content root.
   *
   * @param root root of a content
   * @return new content entry
   */
  @NotNull
  ContentEntry addContentEntry(VirtualFile root);

  /**
   * Remove the specified content root.
   *
   * @param entry the content root to remove.
   */
  void removeContentEntry(ContentEntry entry);

  /**
   * Appends an order entry to the classpath.
   *
   * @param orderEntry the order entry to add.
   */
  void addOrderEntry(OrderEntry orderEntry);

  /**
   * Creates an entry for a given library and adds it to order
   *
   * @param library the library for which the entry is created.
   * @return newly created order entry for the library
   */
  LibraryOrderEntry addLibraryEntry(Library library);

  /**
   * Adds an entry for invalid library.
   *
   * @param name
   * @param level
   * @return
   */
  LibraryOrderEntry addInvalidLibrary(String name, String level);

  ModuleOrderEntry addModuleOrderEntry(Module module);

  ModuleOrderEntry addInvalidModuleEntry(String name);

  LibraryOrderEntry findLibraryOrderEntry(Library library);

  /**
   * Removes order entry from an order.
   *
   * @param orderEntry
   */
  void removeOrderEntry(OrderEntry orderEntry);

  /**
   * @param newOrder
   */
  void rearrangeOrderEntries(OrderEntry[] newOrder);

  void clear();

  /**
   * Commits changes to a <code>{@link ModuleRootManager}</code>.
   * Should be invoked in a write action. After <code>commit()<code>, the model
   * becomes read-only.
   */
  void commit();

  /**
   * Must be invoked for uncommited models that are no longer needed.
   */
  void dispose();

  /**
   * Returns library table with module libraries.<br>
   * <b>Note:</b> returned library table does not support listeners.
   *
   * @return library table to be modified
   */
  LibraryTable getModuleLibraryTable();

  /**
   * Sets JDK for this module to a specific value
   *
   * @param jdk
   */
  void setJdk(ProjectJdk jdk);

  /**
   * Makes this module inheriting JDK from its project
   */
  void inheritJdk();

  /**
   * Makes this module inheriting compiler output from its project
   * @param inherit wether or not compiler output is inherited
   */
  void inheritCompilerOutputPath(boolean inherit);

  /**
   * @deprecated Use {@link #getCompilerOutputPathUrl()} instead
   */
  String getCompilerOutputUrl();

  /**
   * @deprecated Use {@link #getCompilerOutputPathForTestsUrl()} instead
   */
  String getCompilerOutputUrlForTests();

  @NotNull VirtualFile[] getOrderedRoots(OrderRootType type);

  void setCompilerOutputPath(VirtualFile file);

  void setCompilerOutputPath(String url);

  void setCompilerOutputPathForTests(VirtualFile file);

  void setCompilerOutputPathForTests(String url);

  void setExplodedDirectory(VirtualFile file);

  void setExplodedDirectory(String url);

  boolean isChanged();

  @NotNull String[] getOrderedRootUrls(OrderRootType type);

  boolean isExcludeOutput();

  boolean isExcludeExplodedDirectory();

  void setExcludeOutput(boolean excludeOutput);

  void setExcludeExplodedDirectory(boolean excludeExplodedDir);

  @NotNull Module[] getModuleDependencies();

  boolean isWritable();

  void setJavadocUrls(String[] urls);
}
