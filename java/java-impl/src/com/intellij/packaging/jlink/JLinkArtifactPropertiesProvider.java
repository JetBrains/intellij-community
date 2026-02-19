// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.jlink;

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

public final class JLinkArtifactPropertiesProvider extends ArtifactPropertiesProvider {
  public JLinkArtifactPropertiesProvider() {
    super("jlink-properties");
  }

  @Override
  public @NotNull ArtifactProperties<?> createProperties(@NotNull ArtifactType artifactType) {
    return new JLinkArtifactProperties();
  }

  @Override
  public boolean isAvailableFor(@NotNull ArtifactType type) {
    return type instanceof JLinkArtifactType;
  }
}
