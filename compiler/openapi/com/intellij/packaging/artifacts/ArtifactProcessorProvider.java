package com.intellij.packaging.artifacts;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class ArtifactProcessorProvider {
  public static final ExtensionPointName<ArtifactProcessorProvider> EP_NAME = ExtensionPointName.create("com.intellij.packaging.artifactProcessorProvider");

  @Nullable
  public abstract ArtifactProcessor createProcessor(@NotNull Artifact artifact, @NotNull PackagingElementResolvingContext context);
}
