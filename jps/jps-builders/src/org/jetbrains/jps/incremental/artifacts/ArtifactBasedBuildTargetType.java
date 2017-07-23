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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.*;

public abstract class ArtifactBasedBuildTargetType<T extends ArtifactBasedBuildTarget> extends BuildTargetType<T> {
  protected ArtifactBasedBuildTargetType(String typeId, boolean based) {
    super(typeId, based);
  }

  @NotNull
  @Override
  public List<T> computeAllTargets(@NotNull JpsModel model) {
    Collection<JpsArtifact> artifacts = JpsBuilderArtifactService.getInstance().getArtifacts(model, true);
    List<T> targets = new ArrayList<>(artifacts.size());
    for (JpsArtifact artifact : artifacts) {
      if (!StringUtil.isEmpty(artifact.getOutputPath())) {
        targets.add(createArtifactBasedTarget(artifact));
      }
    }
    return targets;
  }

  @NotNull
  @Override
  public BuildTargetLoader<T> createLoader(@NotNull JpsModel model) {
    return new Loader(model);
  }

  protected abstract T createArtifactBasedTarget(JpsArtifact artifact);

  private class Loader extends BuildTargetLoader<T> {
    private final Map<String, JpsArtifact> myArtifacts;

    public Loader(JpsModel model) {
      myArtifacts = new HashMap<>();
      for (JpsArtifact artifact : JpsBuilderArtifactService.getInstance().getArtifacts(model, true)) {
        myArtifacts.put(artifact.getName(), artifact);
      }
    }

    @Nullable
    @Override
    public T createTarget(@NotNull String targetId) {
      JpsArtifact artifact = myArtifacts.get(targetId);
      return artifact != null ? createArtifactBasedTarget(artifact) : null;
    }
  }
}
