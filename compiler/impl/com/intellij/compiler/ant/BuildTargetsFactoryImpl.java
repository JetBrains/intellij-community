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
import com.intellij.openapi.compiler.make.ModuleBuildProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;

public class BuildTargetsFactoryImpl extends BuildTargetsFactory {
  private ModuleChunk myChunk;
  private GenerationOptions myGenOptions;
  private ModuleBuildProperties myModuleBuildProperties;
  private String myExplodedPathProperty;
  private Function<String, String> myExplodedBuildTarget;
  private Function<String, String> myExplodedBuildPath;
  private String myJarPathProperty;
  private Function<String, String> myBuildJarTargetName;

  public void init(final ModuleChunk chunk,
                   final GenerationOptions genOptions,
                   final String explodedPathProperty,
                   final Function<String, String> explodedBuildTarget,
                   final Function<String, String> explodedBuildPath,
                   final String jarPathProperty,
                   final Function<String, String> buildJarTargetName) {
    myChunk = chunk;
    myGenOptions = genOptions;
    myExplodedPathProperty = explodedPathProperty;
    myExplodedBuildTarget = explodedBuildTarget;
    myExplodedBuildPath = explodedBuildPath;
    myJarPathProperty = jarPathProperty;
    myBuildJarTargetName = buildJarTargetName;
    myModuleBuildProperties = ModuleBuildProperties.getInstance(myChunk.getModules()[0]);
  }


  public CompositeGenerator createCompositeBuildTarget(@NonNls final String name, final String description, final Function<Module, String> depends, final String jarPath) {
    return new CompositeBuildTarget(myChunk, myGenOptions, myModuleBuildProperties, name, description) {
      protected String getDepends(final Module module) {
        return depends.fun(module);
      }

      protected String getExplodedBuildTarget(final String name) {
        return myExplodedBuildTarget.fun(name);
      }

      protected String getExplodedBuildPath(final String name) {
        return myExplodedBuildPath.fun(name);
      }

      protected String getJarBuildTarget(final String name) {
        return myBuildJarTargetName.fun(name);
      }

      protected String getExplodedPathProperty() {
        return myExplodedPathProperty;
      }

      protected String getJarPathProperty() {
        return myJarPathProperty;
      }


      protected String getJarPath(final ModuleBuildProperties moduleBuildProperties) {
        return jarPath != null ? jarPath : super.getJarPath(moduleBuildProperties);
      }

    };
  }

  public Target createBuildExplodedTarget(final String description) {
    return new BuildExplodedTarget(myChunk, myGenOptions, myModuleBuildProperties, myExplodedBuildTarget, description) {

      protected String getExplodedBuildPathProperty(final String name) {
        return myExplodedBuildPath.fun(name);
      }

      protected String getExplodedBuildPathProperty() {
        return myExplodedPathProperty;
      }
    };
  }

  public Target createBuildJarTarget(final String description) {
    return new BuildJarTarget(myChunk, myGenOptions, myModuleBuildProperties, myJarPathProperty, myBuildJarTargetName, description);
  }

  public Generator createComment(final String comment) {
    return new Comment(comment);
  }

  public String getModuleName() {
    return ModuleUtil.getModuleNameInReadAction(myModuleBuildProperties.getModule());
  }


  public ModuleBuildProperties getModuleBuildProperties() {
    return myModuleBuildProperties;
  }

  //for test
  public GenerationOptions getDefaultOptions(Project project) {
    return new GenerationOptionsImpl(project, true, false, false, true, ArrayUtil.EMPTY_STRING_ARRAY);
  }
}