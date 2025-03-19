// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.List;

/**
 * Provides artifacts which aren't defined in the project configuration, but can be built as the regular ones.
 * Implementations of this class are registered as Java services, by creating a file META-INF/services/org.jetbrains.jps.incremental.artifacts.JpsSyntheticArtifactProvider
 * containing the qualified name of your implementation class.
 */
public abstract class JpsSyntheticArtifactProvider {
  /**
   * Returns list of additional artifacts which can be built in the project defined by {@code model}. Note that these artifacts are built
   * only if they are included into the build scope (e.g. via {@link com.intellij.compiler.impl.BuildTargetScopeProvider}).
   */
  public abstract @NotNull List<JpsArtifact> createArtifacts(@NotNull JpsModel model);
}
