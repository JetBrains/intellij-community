/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package com.intellij.compiler.ant.j2ee;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.CompositeGenerator;
import com.intellij.compiler.ant.ExplodedAndJarTargetParameters;
import com.intellij.compiler.ant.GenerationUtils;
import com.intellij.compiler.ant.taskdefs.AntCall;
import com.intellij.compiler.ant.taskdefs.Param;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.compiler.ant.taskdefs.Target;
import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class CompositeBuildTarget extends CompositeGenerator {
  public CompositeBuildTarget(ExplodedAndJarTargetParameters parameters,
                              final String targetName, final String targetDescription,
                              final String depends, @Nullable String jarPath) {

    final File moduleBaseDir = parameters.getChunk().getBaseDir();
    final Module containingModule = parameters.getContainingModule();
    final Target buildTarget = new Target(targetName, depends, targetDescription, null);
    final BuildConfiguration buildConfiguration = parameters.getBuildConfiguration();

    final String baseDirProperty = BuildProperties.getModuleChunkBasedirProperty(parameters.getChunk());
    if (buildConfiguration.isExplodedEnabled()) {
      final String explodedPath = buildConfiguration.getExplodedPath();

      String location = GenerationUtils.toRelativePath(VirtualFileManager.extractPath(explodedPath), moduleBaseDir, baseDirProperty,
                                                       parameters.getGenerationOptions(), !containingModule.isSavePathsRelative());
      add(new Property(parameters.getExplodedPathProperty(), location));

      final AntCall antCall = new AntCall(parameters.getBuildExplodedTargetName());
      buildTarget.add(antCall);
      antCall.add(new Param(parameters.getExplodedPathParameter(), BuildProperties.propertyRef(parameters.getExplodedPathProperty())));
    }

    if (jarPath == null) {
      jarPath = getJarPath(buildConfiguration);
    }

    if (jarPath != null) {
      String location = GenerationUtils.toRelativePath(VirtualFileManager.extractPath(jarPath), moduleBaseDir, baseDirProperty,
                                                       parameters.getGenerationOptions(), !containingModule.isSavePathsRelative());
      add(new Property(parameters.getJarPathProperty(), location));

      final AntCall antCall = new AntCall(parameters.getBuildJarTargetName());
      buildTarget.add(antCall);
      antCall.add(new Param(parameters.getJarPathParameter(), BuildProperties.propertyRef(parameters.getJarPathProperty())));
    }
    add(buildTarget);
  }

  @Nullable
  protected static String getJarPath(BuildConfiguration buildConfiguration){
    return buildConfiguration.isJarEnabled() ? buildConfiguration.getJarPath() : null;
  }
}