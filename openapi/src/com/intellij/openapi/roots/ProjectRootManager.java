/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;

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
