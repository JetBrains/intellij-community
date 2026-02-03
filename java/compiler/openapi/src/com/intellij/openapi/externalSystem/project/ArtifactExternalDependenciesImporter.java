// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.project;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface ArtifactExternalDependenciesImporter {
  @Nullable
  ManifestFileConfiguration getManifestFile(@NotNull Artifact artifact, @NotNull PackagingElementResolvingContext context);

  void applyChanges(ModifiableArtifactModel artifactModel, PackagingElementResolvingContext context);
}
