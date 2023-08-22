/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
  @NotNull
  public abstract List<? extends BuildTask> createArtifactBuildTasks(@NotNull JpsArtifact artifact, @NotNull ArtifactBuildPhase buildPhase);
}
