// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.util.List;

public final class ArtifactBuilderService extends BuilderService {
  @Override
  public @NotNull List<? extends BuildTargetType<?>> getTargetTypes() {
    return List.of(ArtifactBuildTargetType.INSTANCE);
  }

  @Override
  public @NotNull List<? extends TargetBuilder<?, ?>> createBuilders() {
    return List.of(new IncArtifactBuilder());
  }
}
