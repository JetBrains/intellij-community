/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
      if (explodedPath != null) {
        String location = GenerationUtils.toRelativePath(VirtualFileManager.extractPath(explodedPath), moduleBaseDir, baseDirProperty,
                                                         parameters.getGenerationOptions());
        add(new Property(parameters.getExplodedPathProperty(), location));
      }

      final AntCall antCall = new AntCall(parameters.getBuildExplodedTargetName());
      buildTarget.add(antCall);
      antCall.add(new Param(parameters.getExplodedPathParameter(), BuildProperties.propertyRef(parameters.getExplodedPathProperty())));
    }

    if (jarPath == null) {
      jarPath = getJarPath(buildConfiguration);
    }

    if (jarPath != null) {
      String location = GenerationUtils.toRelativePath(VirtualFileManager.extractPath(jarPath), moduleBaseDir, baseDirProperty,
                                                       parameters.getGenerationOptions());
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
