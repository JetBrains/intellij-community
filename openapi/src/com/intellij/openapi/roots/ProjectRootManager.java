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

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class ProjectRootManager implements ModificationTracker {
  public static ProjectRootManager getInstance(Project project){
    return project.getComponent(ProjectRootManager.class);
  }

  public abstract ProjectFileIndex getFileIndex();

  public abstract void addModuleRootListener(ModuleRootListener listener);
  public abstract void removeModuleRootListener(ModuleRootListener listener);
  public abstract void dispatchPendingEvent(ModuleRootListener listener);

  /**
   * This method is not needed anymore. Remove it!!
   *
   * @deprecated
   * @param type
   * @return
   */
  public abstract VirtualFile[] getRootFiles(ProjectRootType type);


  public abstract VirtualFile[] getContentRoots();

  //Q: do we need this method at all? Now is used in tree views only (they should probably be changed to include modules)
  public abstract VirtualFile[] getContentSourceRoots();

  /**
   * @deprecated
   * @return the class path with substituted output paths
   */
  public abstract VirtualFile[] getFullClassPath();

  /**
   * @deprecated
   * @return
   */
  public abstract ProjectJdk getJdk();

  public abstract ProjectJdk getProjectJdk();

  public abstract String getProjectJdkName();

  public abstract void setProjectJdk(ProjectJdk jdk);

  public abstract void setProjectJdkName(String name);

  public abstract void multiCommit(ModifiableRootModel[] rootModels);

  public abstract void multiCommit(ModifiableModuleModel moduleModel, ModifiableRootModel[] rootModels);

  public abstract void checkCircularDependency(ModifiableRootModel[] rootModels, ModifiableModuleModel moduleModel) throws ModuleCircularDependencyException;

}
