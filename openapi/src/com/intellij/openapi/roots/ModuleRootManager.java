/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

/**
 *  @author dsl
 */
public abstract class ModuleRootManager implements ModuleRootModel {
  public static ModuleRootManager getInstance(Module module) {
    return module.getComponent(ModuleRootManager.class);
  }


  /**
   * Use this method to get all files from this module's Order
   * @param type
   * @return
   */
  public abstract VirtualFile[] getFiles(OrderRootType type);

  /**
   * Use this method to get all urls from this module's Order
   * @param type
   * @return
   */
  public abstract String[] getUrls(OrderRootType type);

  /**
   * Returns file index for this module
   * @return
   */
  public abstract ModuleFileIndex getFileIndex();

  /**
   *
   * @return
   */
  public abstract ModifiableRootModel getModifiableModel();

  /**
   * Returns list of modules <i>this module</i> depends on.
   * @return
   */
  public abstract Module[] getDependencies();
}
