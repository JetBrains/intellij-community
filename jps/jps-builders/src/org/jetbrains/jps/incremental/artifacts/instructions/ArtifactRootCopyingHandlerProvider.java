/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsDirectoryCopyPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsModuleOutputPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

import java.io.File;

/**
 * Implement this class to customize the way files from an artifact layout are copied to the artifact output. The main entry point for the
 * external build system plugins. Implementations must be registered as Java services, by creating a file
 * META-INF/services/org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootCopyingHandlerProvider containing the qualified name
 * of the implementation class.
 */
public abstract class ArtifactRootCopyingHandlerProvider {

  /**
   * Override this method to customize how files from {@code root} are copied to the {@code artifact output}.
   * @param root file or directory which is configured in {@code artifact} to be copied to its output
   * @param targetDirectory target directory under the artifact output to which {@code root} will be copied.
   * @param contextElement element in the artifact layout to which {@code root} corresponds; it may be for example
   * {@link JpsDirectoryCopyPackagingElement} (in that case {@code root} will be its {@link JpsDirectoryCopyPackagingElement#getDirectoryPath() directory})
   * or {@link JpsModuleOutputPackagingElement} (in that case {@code root} will be the module output directory)
   */
  @Nullable
  public abstract FileCopyingHandler createCustomHandler(@NotNull JpsArtifact artifact,
                                                         @NotNull File root,
                                                         @NotNull File targetDirectory,
                                                         @NotNull JpsPackagingElement contextElement,
                                                         @NotNull JpsModel model,
                                                         @NotNull BuildDataPaths buildDataPaths);
}
