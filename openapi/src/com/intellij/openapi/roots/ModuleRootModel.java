/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Interface providing root information model for a given module.
 * @author dsl
 */
public interface ModuleRootModel {
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
   * @return list of order entries for this module
   */
  OrderEntry[] getOrderEntries();

  /**
   * Use this method to get JDK set for this module
   * @return either module-specific or inherited JDK
   * @see #isJdkInherited()
   */
  ProjectJdk getJdk();

  /**
   * Returns <code>true</code> if JDK for this module is inherited from a project
   * @return
   * @see ProjectRootManager#getProjectJdk()
   * @see ProjectRootManager#setProjectJdk(com.intellij.openapi.projectRoots.ProjectJdk)
   */
  boolean isJdkInherited();

  /**
   * Returns an array of content roots from all content entries. A helper method.
   * @see #getContentEntries()
   */
  VirtualFile[] getContentRoots();

  /**
   * Returns an array of content root urls from all content entries. A helper method.
   * @see #getContentEntries()
   */
  String[] getContentRootUrls();

  /**
   * Returns an array of exclude roots from all content entries. A helper method.
   * @see #getContentEntries()
   */
  VirtualFile[] getExcludeRoots();

  /**
   * Returns an array of exclude root urls from all content entries. A helper method.
   * @see #getContentEntries()
   */
  String[] getExcludeRootUrls();

  /**
   * Returns an array of source roots from all content entries. A helper method.
   * @see #getContentEntries()
   */
  VirtualFile[] getSourceRoots();

  /**
   * Returns an array of source root urls from all content entries. A helper method.
   * @see #getContentEntries()
   */
  String[] getSourceRootUrls();

  /**
   * Returns a compiler output path for production sources of the module, if it is valid.
   */
  VirtualFile getCompilerOutputPath();

  /**
   * Returns a compiler output path url for production sources of the module.
   * @return null if it have never been set.
   */
  String getCompilerOutputPathUrl();

  /**
   * Returns a compiler output path for test sources of the module, if it is valid.
   */
  VirtualFile getCompilerOutputPathForTests();

  /**
   * Returns a compiler output path url for test sources of the module.
   * @return null if it have never been set.
   */
  String getCompilerOutputPathForTestsUrl();

  /**
   * Returns an exploded directory path of the module, if it is valid.
   * @return null if not applicable or not set
   */
  VirtualFile getExplodedDirectory();

  /**
   * Returns an exploded directory path url
   * @return null if it have never been set or if not applicable for this module.
   */
  String getExplodedDirectoryUrl();

  <R> R processOrder(RootPolicy<R> policy, R initialValue);

  /**
   * Returns list of module names <i>this module</i> depends on.
   */
  String[] getDependencyModuleNames();


  /**
   * Returns list of javadoc paths for the module.
   */
  VirtualFile[] getJavadocPaths();

  String[] getJavadocUrls();
}
