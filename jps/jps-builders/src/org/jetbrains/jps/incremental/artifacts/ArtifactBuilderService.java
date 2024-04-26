// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.util.Collections;
import java.util.List;

public final class ArtifactBuilderService extends BuilderService {
  @Override
  public @NotNull List<? extends BuildTargetType<?>> getTargetTypes() {
    return Collections.singletonList(ArtifactBuildTargetType.INSTANCE);
  }

  @Override
  public @NotNull List<? extends TargetBuilder<?,?>> createBuilders() {
    return Collections.singletonList(new IncArtifactBuilder());
  }
}
