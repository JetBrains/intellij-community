/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Target;
import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;

public abstract class BuildTargetsFactory {
  public static BuildTargetsFactory getInstance() {
    return ServiceManager.getService(BuildTargetsFactory.class);
  }

  public abstract void init(ModuleChunk chunk,
                            BuildConfiguration buildConfiguration,
                            GenerationOptions genOptions,
                            @NonNls String explodedPathProperty,
                            @NonNls Function<String, String> explodedBuildTarget,
                            @NonNls Function<String, String> explodedBuildPath,
                            @NonNls String jarPathProperty,
                            @NonNls Function<String, String> buildJarTargetName);

  public abstract CompositeGenerator createCompositeBuildTarget(@NonNls String name, String description, Function<Module, String> depends, String jarPath);

  public abstract Target createBuildExplodedTarget(String description);

  public abstract Target createBuildJarTarget(String description);

  public abstract Generator createComment(String comment);

  public abstract String getModuleName();

  public abstract BuildConfiguration getModuleBuildProperties();

  //for test
  public abstract GenerationOptions getDefaultOptions(Project project);
}