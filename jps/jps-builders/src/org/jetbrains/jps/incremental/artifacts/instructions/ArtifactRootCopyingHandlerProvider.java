// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.ApiStatus;
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
@ApiStatus.Internal
public abstract class ArtifactRootCopyingHandlerProvider {
  /**
   * Override this method to customize how files from {@code root} are copied to the {@code artifact output}.
   *
   * @param root            file or directory which is configured in {@code artifact} to be copied to its output
   * @param targetDirectory target directory under the artifact output to which {@code root} will be copied.
   * @param contextElement  element in the artifact layout to which {@code root} corresponds; it may be for example
   *                        {@link JpsDirectoryCopyPackagingElement} (in that case {@code root} will be its {@link JpsDirectoryCopyPackagingElement#getDirectoryPath() directory})
   *                        or {@link JpsModuleOutputPackagingElement} (in that case {@code root} will be the module output directory)
   */
  public abstract @Nullable FileCopyingHandler createCustomHandler(@NotNull JpsArtifact artifact,
                                                                   @NotNull File root,
                                                                   @NotNull File targetDirectory,
                                                                   @NotNull JpsPackagingElement contextElement,
                                                                   @NotNull JpsModel model,
                                                                   @NotNull BuildDataPaths buildDataPaths);
}
