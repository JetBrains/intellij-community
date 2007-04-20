/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Target;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public abstract class BuildTargetsFactory {
  public static BuildTargetsFactory getInstance() {
    return ServiceManager.getService(BuildTargetsFactory.class);
  }

  public abstract CompositeGenerator createCompositeBuildTarget(ExplodedAndJarTargetParameters parameters, @NonNls String targetName, 
                                                                String description, String depends, @Nullable String jarPath);

  public abstract Target createBuildExplodedTarget(ExplodedAndJarTargetParameters parameters, BuildRecipe buildRecipe, String description);

  public abstract Target createBuildJarTarget(ExplodedAndJarTargetParameters parameters, BuildRecipe buildRecipe, String description);

  public abstract Generator createComment(String comment);

  //for test
  public abstract GenerationOptions getDefaultOptions(Project project);
}