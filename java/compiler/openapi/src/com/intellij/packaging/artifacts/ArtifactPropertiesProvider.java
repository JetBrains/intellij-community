// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.artifacts;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ArtifactPropertiesProvider {
  public static final ExtensionPointName<ArtifactPropertiesProvider> EP_NAME =
    new ExtensionPointName<>("com.intellij.packaging.artifactPropertiesProvider");
  private final @NonNls String myId;

  protected ArtifactPropertiesProvider(@NotNull @NonNls String id) {
    myId = id;
  }

  public final @NonNls String getId() {
    return myId;
  }

  public boolean isAvailableFor(@NotNull ArtifactType type) {
    return true;
  }

  public abstract @NotNull ArtifactProperties<?> createProperties(@NotNull ArtifactType artifactType);

  public static @NotNull List<ArtifactPropertiesProvider> getProviders() {
    return EP_NAME.getExtensionList();
  }

  public static @Nullable ArtifactPropertiesProvider findById(@NotNull @NonNls String id) {
    for (ArtifactPropertiesProvider provider : getProviders()) {
      if (provider.getId().equals(id)) {
        return provider;
      }
    }
    return null;
  }
}
