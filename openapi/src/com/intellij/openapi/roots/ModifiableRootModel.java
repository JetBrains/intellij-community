/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Model of roots that should be used by clients to modify
 * module rooots. The model can be obtained from <code>{@link ModuleRootManager}</code>
 *  @author dsl
 */
public interface ModifiableRootModel extends ModuleRootModel {
  /**
   * Use this method to obtain all content entries of a module. Entries are given in
   * lexicographical order of their paths.
   *
   * @return list of content entries for this module
   * @see ContentEntry
   */
  ContentEntry[] getContentEntries();

  /**
   * Use this method to obtain order of roots of a module. Order of entries is important
   * and can be modified by <code>{@link ModifiableRootModel#rearrangeOrderEntries(OrderEntry[])}</code>
   *
   * @return list of order entries for this module
   */
  OrderEntry[] getOrderEntries();

  /**
   * Adds a content with a given virtual file.
   * @param root root of a content
   * @return new content entry
   */
  ContentEntry addContentEntry(VirtualFile root);

  /**
   * Remove given content entry.
   * @param entry
   */
  void removeContentEntry(ContentEntry entry);

  /**
   * Append an order entry to order.
   * @param orderEntry
   */
  void addOrderEntry(OrderEntry orderEntry);

  /**
   * Creates an entry for a given library and adds it to order
   * @param library
   * @return newly created order entry for the library
   */
  LibraryOrderEntry addLibraryEntry(Library library);

  /**
   * Adds an entry for invalid library.
   * @param name
   * @param level
   * @return
   * @see com.intellij.openapi.roots.libraries.LibraryTableUtil
   */
  LibraryOrderEntry addInvalidLibrary(String name, String level);

  ModuleOrderEntry addModuleOrderEntry(Module module);

  ModuleOrderEntry addInvalidModuleEntry(String name);

  LibraryOrderEntry findLibraryOrderEntry(Library library);

  /**
   * Removes order entry from an order.
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
   * Returns a module that will be changed when this model is commited.
   */
  Module getModule();

  /**
   * Returns library table with module libraries.<br>
   * <b>Note:</b> returned library table does not support listeners.
   * @return library table to be modified
   */
  LibraryTable getModuleLibraryTable();

  <R> R processOrder(RootPolicy<R> policy, R initialValue);

  /**
   * Sets JDK for this module to a specific value
   * @param jdk
   */
  void setJdk(ProjectJdk jdk);

  /**
   * Makes this module inheriting JDK from its project
   */
  void inheritJdk();

  /**
   * @return JDK set for this module (either inherited or specific)
   */
  ProjectJdk getJdk();

  boolean isJdkInherited();

  /**
   * @deprecated Use {@link #getCompilerOutputPathUrl()} instead
   */
  String getCompilerOutputUrl();

  /**
   * @deprecated Use {@link #getCompilerOutputPathForTestsUrl()} instead
   */
  String getCompilerOutputUrlForTests();

  VirtualFile[] getOrderedRoots(OrderRootType type);

  void setCompilerOutputPath(VirtualFile file);

  void setCompilerOutputPath(String url);

  void setCompilerOutputPathForTests(VirtualFile file);

  void setCompilerOutputPathForTests(String url);

  void setExplodedDirectory(VirtualFile file);

  void setExplodedDirectory(String url);

  boolean isChanged();

  String[] getOrderedRootUrls(OrderRootType type);

  boolean isExcludeOutput();

  boolean isExcludeExplodedDirectory();

  void setExcludeOutput(boolean excludeOutput);

  void setExcludeExplodedDirectory(boolean excludeExplodedDir);

  Module[] getModuleDependencies();

  boolean isWritable();

  void setJavadocUrls(String[] urls);
}
