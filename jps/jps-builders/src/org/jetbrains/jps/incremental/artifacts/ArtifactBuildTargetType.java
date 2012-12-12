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
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.*;

/**
 * @author nik
 */
public class ArtifactBuildTargetType extends BuildTargetType<ArtifactBuildTarget> {
  public static final ArtifactBuildTargetType INSTANCE = new ArtifactBuildTargetType();

  public ArtifactBuildTargetType() {
    super("artifact");
  }

  @NotNull
  @Override
  public List<ArtifactBuildTarget> computeAllTargets(@NotNull JpsModel model) {
    Collection<JpsArtifact> artifacts = JpsBuilderArtifactService.getInstance().getArtifacts(model, true);
    List<ArtifactBuildTarget> targets = new ArrayList<ArtifactBuildTarget>(artifacts.size());
    for (JpsArtifact artifact : artifacts) {
      if (!StringUtil.isEmpty(artifact.getOutputPath())) {
        targets.add(new ArtifactBuildTarget(artifact));
      }
    }
    return targets;
  }

  @NotNull
  @Override
  public Loader createLoader(@NotNull JpsModel model) {
    return new Loader(model); 
  }

  private static class Loader extends BuildTargetLoader<ArtifactBuildTarget> {
    private final Map<String, JpsArtifact> myArtifacts;

    public Loader(JpsModel model) {
      myArtifacts = new HashMap<String, JpsArtifact>();
      for (JpsArtifact artifact : JpsBuilderArtifactService.getInstance().getArtifacts(model, true)) {
        myArtifacts.put(artifact.getName(), artifact);
      }
    }

    @Nullable
    @Override
    public ArtifactBuildTarget createTarget(@NotNull String targetId) {
      JpsArtifact artifact = myArtifacts.get(targetId);
      return artifact != null ? new ArtifactBuildTarget(artifact) : null;
    }
  }
}
