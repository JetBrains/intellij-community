// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.project;

import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import org.jetbrains.annotations.NotNull;

public interface PackagingModel {
  @NotNull
  ModifiableArtifactModel getModifiableArtifactModel();

  @NotNull
  PackagingElementResolvingContext getPackagingElementResolvingContext();

  @NotNull
  ArtifactExternalDependenciesImporter getArtifactExternalDependenciesImporter();
}
