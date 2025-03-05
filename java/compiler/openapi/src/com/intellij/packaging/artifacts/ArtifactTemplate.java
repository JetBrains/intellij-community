// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.artifacts;

import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.packaging.elements.CompositePackagingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ArtifactTemplate {

  public abstract @NlsActions.ActionText String getPresentableName();

  public @Nullable NewArtifactConfiguration createArtifact() {
    final String name = "unnamed";
    return new NewArtifactConfiguration(null, name, null);
  }

  public void setUpArtifact(@NotNull ModifiableArtifact artifact, @NotNull NewArtifactConfiguration configuration) {
  }

  public static class NewArtifactConfiguration {
    private final CompositePackagingElement<?> myRootElement;
    private final String myArtifactName;
    private final ArtifactType myArtifactType;

    public NewArtifactConfiguration(CompositePackagingElement<?> rootElement, @NlsSafe String artifactName, ArtifactType artifactType) {
      myRootElement = rootElement;
      myArtifactName = artifactName;
      myArtifactType = artifactType;
    }

    public CompositePackagingElement<?> getRootElement() {
      return myRootElement;
    }

    public @NlsSafe String getArtifactName() {
      return myArtifactName;
    }

    public ArtifactType getArtifactType() {
      return myArtifactType;
    }
  }
}
