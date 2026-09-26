// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

import java.util.Collection;
import java.util.List;

public abstract class ArtifactLayoutCustomizationService {
  public abstract @Nullable List<JpsPackagingElement> getCustomizedLayout(@NotNull JpsArtifact artifact,
                                                                          @NotNull Collection<JpsArtifact> parentArtifacts);

}
