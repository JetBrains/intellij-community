/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootDescriptor;
import org.jetbrains.jps.model.artifact.JpsArtifact;


public abstract class ArtifactBasedBuildTarget extends BuildTarget<ArtifactRootDescriptor> {
  private final JpsArtifact myArtifact;

  protected ArtifactBasedBuildTarget(@NotNull BuildTargetType<? extends BuildTarget<ArtifactRootDescriptor>> targetType, @NotNull JpsArtifact artifact) {
    super(targetType);
    myArtifact = artifact;
  }

  @Override
  public @NotNull String getId() {
    return myArtifact.getName();
  }

  public JpsArtifact getArtifact() {
    return myArtifact;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myArtifact.equals(((ArtifactBasedBuildTarget)o).myArtifact);
  }

  @Override
  public int hashCode() {
    return myArtifact.hashCode();
  }
}
