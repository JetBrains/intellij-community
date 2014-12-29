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
 * @author nik
 */
public abstract class ArtifactBuildTaskProvider {
  public enum ArtifactBuildPhase {
    PRE_PROCESSING("pre-processing"), FINISHING_BUILD("finishing"), POST_PROCESSING("post-processing");
    private final String myPresentableName;

    ArtifactBuildPhase(String presentableName) {
      myPresentableName = presentableName;
    }

    public String getPresentableName() {
      return myPresentableName;
    }
  }

  @NotNull
  public abstract List<? extends BuildTask> createArtifactBuildTasks(@NotNull JpsArtifact artifact, @NotNull ArtifactBuildPhase buildPhase);
}
