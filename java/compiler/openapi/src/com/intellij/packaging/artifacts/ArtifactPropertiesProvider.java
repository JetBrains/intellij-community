package com.intellij.packaging.artifacts;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public abstract class ArtifactPropertiesProvider {
  public static final ExtensionPointName<ArtifactPropertiesProvider> EP_NAME = ExtensionPointName.create("com.intellij.packaging.artifactPropertiesProvider");
  private final String myId;

  protected ArtifactPropertiesProvider(@NotNull @NonNls String id) {
    myId = id;
  }

  public final String getId() {
    return myId;
  }

  public boolean isAvailableFor(@NotNull ArtifactType type) {
    return true;
  } 

  @NotNull 
  public abstract ArtifactProperties<?> createProperties(@NotNull ArtifactType artifactType);

  public static ArtifactPropertiesProvider[] getProviders() {
    return Extensions.getExtensions(EP_NAME);
  }

  @Nullable
  public static ArtifactPropertiesProvider findById(@NotNull @NonNls String id) {
    for (ArtifactPropertiesProvider provider : getProviders()) {
      if (provider.getId().equals(id)) {
        return provider;
      }
    }
    return null;
  }
}
