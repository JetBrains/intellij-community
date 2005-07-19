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

  /**
   * @return true if this module contains passed in its dependencies
   */
  public abstract boolean isDependsOn(Module module);
}
