/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.compiler.ant;

import com.intellij.ExtensionPoints;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public abstract class ChunkBuildExtension {
  public static final ExtensionPointName<ChunkBuildExtension> EP_NAME = ExtensionPointName.create(ExtensionPoints.ANT_BUILD_GEN);

  public abstract boolean haveSelfOutputs(Module[] modules);

  @NotNull
  @NonNls
  public abstract String[] getTargets(final ModuleChunk chunk);

  public abstract void process(Project project, ModuleChunk chunk, GenerationOptions genOptions, CompositeGenerator generator);

  public void generateProperties(final PropertyFileGenerator generator, final Project project, final GenerationOptions options) {
  }

  public static boolean hasSelfOutput(ModuleChunk chunk) {
    final ChunkBuildExtension[] extensions = Extensions.getRootArea().getExtensionPoint(EP_NAME).getExtensions();
    final Module[] modules = chunk.getModules();
    for (ChunkBuildExtension extension : extensions) {
      if (!extension.haveSelfOutputs(modules)) return false;
    }
    return true;
  }

  public static String[] getAllTargets(ModuleChunk chunk) {
    List<String> allTargets = new ArrayList<String>();
    final ChunkBuildExtension[] extensions = Extensions.getRootArea().getExtensionPoint(EP_NAME).getExtensions();
    for (ChunkBuildExtension extension : extensions) {
      allTargets.addAll(Arrays.asList(extension.getTargets(chunk)));
    }
    if (allTargets.isEmpty()) {
      allTargets.add(BuildProperties.getCompileTargetName(chunk.getName()));
    }
    return allTargets.toArray(new String[allTargets.size()]);
  }

  public static void process(CompositeGenerator generator, ModuleChunk chunk, GenerationOptions genOptions) {
    final Project project = chunk.getProject();
    final ChunkBuildExtension[] extensions = Extensions.getRootArea().getExtensionPoint(EP_NAME).getExtensions();
    for (ChunkBuildExtension extension : extensions) {
      extension.process(project, chunk, genOptions, generator);
    }
  }

  public static void generateAllProperties(final PropertyFileGenerator propertyFileGenerator, final Project project,
                                        final GenerationOptions genOptions) {
    ChunkBuildExtension[] extensions = Extensions.getRootArea().getExtensionPoint(EP_NAME).getExtensions();
    for (ChunkBuildExtension extension : extensions) {
      extension.generateProperties(propertyFileGenerator, project, genOptions);
    }
  }
}