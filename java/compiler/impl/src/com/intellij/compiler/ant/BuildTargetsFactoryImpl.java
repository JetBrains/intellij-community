/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package com.intellij.compiler.ant;

import com.intellij.compiler.ant.j2ee.BuildExplodedTarget;
import com.intellij.compiler.ant.j2ee.BuildJarTarget;
import com.intellij.compiler.ant.j2ee.CompositeBuildTarget;
import com.intellij.compiler.ant.taskdefs.Target;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class BuildTargetsFactoryImpl extends BuildTargetsFactory {

  public CompositeGenerator createCompositeBuildTarget(final ExplodedAndJarTargetParameters parameters, @NonNls final String targetName,
                                                       final String description, final String depends, @Nullable String jarPath) {
    return new CompositeBuildTarget(parameters, targetName, description, depends, jarPath);
  }

  public Target createBuildExplodedTarget(final ExplodedAndJarTargetParameters parameters, final BuildRecipe buildRecipe, final String description) {
    return new BuildExplodedTarget(parameters, buildRecipe, description);
  }

  public Target createBuildJarTarget(final ExplodedAndJarTargetParameters parameters, final BuildRecipe buildRecipe, final String description) {
    return new BuildJarTarget(parameters, buildRecipe, description);
  }


  public Generator createComment(final String comment) {
    return new Comment(comment);
  }

  //for test
  public GenerationOptions getDefaultOptions(Project project) {
    return new GenerationOptionsImpl(project, true, false, false, true, ArrayUtil.EMPTY_STRING_ARRAY);
  }
}