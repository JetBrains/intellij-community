// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  protected ArtifactBasedBuildTargetType(String typeId, boolean fileBased) {
    super(typeId, fileBased);
  }

  @Override
  public @NotNull List<T> computeAllTargets(@NotNull JpsModel model) {
    Collection<JpsArtifact> artifacts = JpsBuilderArtifactService.getInstance().getArtifacts(model, true);
    List<T> targets = new ArrayList<>(artifacts.size());
    for (JpsArtifact artifact : artifacts) {
      if (!StringUtil.isEmpty(artifact.getOutputPath())) {
        targets.add(createArtifactBasedTarget(artifact));
      }
    }
    return targets;
  }

  @Override
  public @NotNull BuildTargetLoader<T> createLoader(@NotNull JpsModel model) {
    return new Loader(model);
  }

  protected abstract T createArtifactBasedTarget(JpsArtifact artifact);

  private final class Loader extends BuildTargetLoader<T> {
    private final Map<String, JpsArtifact> myArtifacts;

    Loader(JpsModel model) {
      myArtifacts = new HashMap<>();
      for (JpsArtifact artifact : JpsBuilderArtifactService.getInstance().getArtifacts(model, true)) {
        myArtifacts.put(artifact.getName(), artifact);
      }
    }

    @Override
    public @Nullable T createTarget(@NotNull String targetId) {
      JpsArtifact artifact = myArtifacts.get(targetId);
      return artifact != null ? createArtifactBasedTarget(artifact) : null;
    }
  }
}
