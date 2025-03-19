// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.project.ArtifactExternalDependenciesImporter;
import com.intellij.openapi.roots.ui.configuration.artifacts.ManifestFilesInfo;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArtifactExternalDependenciesImporterImpl implements ArtifactExternalDependenciesImporter {
  private final ManifestFilesInfo myManifestFiles = new ManifestFilesInfo();

  @Override
  public @Nullable ManifestFileConfiguration getManifestFile(@NotNull Artifact artifact,
                                                             @NotNull PackagingElementResolvingContext context) {
    return myManifestFiles.getManifestFile(artifact.getRootElement(), artifact.getArtifactType(), context);
  }

  @Override
  public void applyChanges(ModifiableArtifactModel artifactModel, final PackagingElementResolvingContext context) {
    myManifestFiles.saveManifestFiles();
  }
}
