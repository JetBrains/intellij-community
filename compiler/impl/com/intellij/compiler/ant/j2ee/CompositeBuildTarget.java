/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package com.intellij.compiler.ant.j2ee;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.AntCall;
import com.intellij.compiler.ant.taskdefs.Param;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.compiler.ant.taskdefs.Target;
import com.intellij.openapi.compiler.make.BuildConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@SuppressWarnings({"AbstractMethodCallInConstructor"})
public abstract class CompositeBuildTarget extends CompositeGenerator {
  public CompositeBuildTarget(final ModuleChunk chunk,
                              final GenerationOptions genOptions,
                              final Module module,
                              final BuildConfiguration buildConfiguration,
                              final String name,
                              final String description) {

    final File moduleBaseDir = chunk.getBaseDir();
    final String moduleName = module.getName();
    final Target buildTarget = new Target(name, getDepends(module), description, null);

    if (buildConfiguration.isExplodedEnabled()) {
      final String explodedPath = buildConfiguration.getExplodedPath();

      String location = GenerationUtils.toRelativePath(VirtualFileManager.extractPath(explodedPath), moduleBaseDir, BuildProperties.getModuleChunkBasedirProperty(chunk), genOptions, !module.isSavePathsRelative());
      add(new Property(getExplodedBuildPath(moduleName), location));

      final AntCall antCall = new AntCall(getExplodedBuildTarget(moduleName));
      buildTarget.add(antCall);
      antCall.add(new Param(getExplodedPathProperty(), BuildProperties.propertyRef(getExplodedBuildPath(moduleName))));
    }
    final String jarPath = getJarPath(buildConfiguration);
    if (jarPath != null) {
      String location = GenerationUtils.toRelativePath(VirtualFileManager.extractPath(jarPath), moduleBaseDir, BuildProperties.getModuleChunkBasedirProperty(chunk), genOptions, !module.isSavePathsRelative());
      add(new Property(BuildProperties.getJarPathProperty(moduleName), location));

      final AntCall antCall = new AntCall(getJarBuildTarget(moduleName));
      buildTarget.add(antCall);
      antCall.add(new Param(getJarPathProperty(), BuildProperties.propertyRef(BuildProperties.getJarPathProperty(moduleName))));
    }
    add(buildTarget);
  }

  @Nullable
  protected String getJarPath(BuildConfiguration buildConfiguration){
    return buildConfiguration.isJarEnabled() ? buildConfiguration.getJarPath() : null;
  }

  protected abstract String getDepends(Module module);

  protected abstract String getExplodedBuildTarget(String name);

  protected abstract String getExplodedBuildPath(String name);

  protected abstract String getJarBuildTarget(String name);

  protected abstract String getExplodedPathProperty();

  protected abstract String getJarPathProperty();
}