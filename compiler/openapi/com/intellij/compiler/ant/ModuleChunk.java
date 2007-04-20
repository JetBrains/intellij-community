/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.ant;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 19, 2004
 */
public class ModuleChunk {
  private final Module[] myModules;
  private Module myMainModule;
  private ModuleChunk[] myDependentChunks;
  private File myBaseDir = null;

  public ModuleChunk(Module[] modules) {
    myModules = modules;
    myMainModule = myModules[0]; // todo: temporary, let user configure this
  }

  public String getName() {
    return myMainModule.getName();
  }

  public Module[] getModules() {
    return myModules;
  }

  @Nullable
  public String getOutputDirUrl() {
    return ModuleRootManager.getInstance(myMainModule).getCompilerOutputPathUrl();
  }

  public String getTestsOutputDirUrl() {
    return ModuleRootManager.getInstance(myMainModule).getCompilerOutputPathForTestsUrl();
  }

  public boolean isSavePathsRelative() {
    return myMainModule.isSavePathsRelative();
  }

  public boolean isJdkInherited() {
    return ModuleRootManager.getInstance(myMainModule).isJdkInherited();
  }

  @Nullable
  public ProjectJdk getJdk() {
    return ModuleRootManager.getInstance(myMainModule).getJdk();
  }

  public ModuleChunk[] getDependentChunks() {
    return myDependentChunks;
  }

  public void setDependentChunks(ModuleChunk[] dependentChunks) {
    myDependentChunks = dependentChunks;
  }

  public File getBaseDir() {
    if (myBaseDir != null) {
      return myBaseDir;
    }
    return new File(myMainModule.getModuleFilePath()).getParentFile();
  }

  public void setBaseDir(File baseDir) {
    myBaseDir = baseDir;
  }

  public void setMainModule(Module module) {
    myMainModule = module;
  }

  public Project getProject() {
    return myMainModule.getProject();
  }
}
