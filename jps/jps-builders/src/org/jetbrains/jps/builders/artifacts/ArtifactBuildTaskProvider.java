// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.BuildTask;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.List;

/**
 * Allows to perform additional tasks when artifacts are built. Implementations of this class are registered as Java services, by creating
 * a file META-INF/services/org.jetbrains.jps.builders.artifacts.ArtifactBuildTaskProvider containing the qualified name of your
 * implementation class.
 */
public abstract class ArtifactBuildTaskProvider {
  public enum ArtifactBuildPhase {
    PRE_PROCESSING, FINISHING_BUILD, POST_PROCESSING
  }

  /**
   * Returns list of tasks which need to be executed during {@code buildPhase} when {@code artifact} is building. Firstly tasks returned for
   * {@link ArtifactBuildPhase#PRE_PROCESSING PRE_PROCESSING} are executed, then files specified in the artifact layout are copied to the output directory.
   * If all files in the artifact output were up to date, i.e. no copying was performed, the build finishes. Otherwise all tasks returned for
   * {@link ArtifactBuildPhase#FINISHING_BUILD FINISHING_BUILD} are executed and then all tasks returned for
   * {@link ArtifactBuildPhase#POST_PROCESSING POST_PROCESSING} are executed.
   */
  public abstract @NotNull List<? extends BuildTask> createArtifactBuildTasks(@NotNull JpsArtifact artifact, @NotNull ArtifactBuildPhase buildPhase);
}
