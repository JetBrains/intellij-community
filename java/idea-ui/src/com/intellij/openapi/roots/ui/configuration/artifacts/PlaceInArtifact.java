// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.PlaceInProjectStructure;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlaceInArtifact extends PlaceInProjectStructure {
  private final Artifact myArtifact;
  private final ArtifactsStructureConfigurableContext myContext;
  private final String myParentPath;
  private final PackagingElement<?> myPackagingElement;

  public PlaceInArtifact(Artifact artifact, ArtifactsStructureConfigurableContext context, @Nullable String parentPath,
                         @Nullable PackagingElement<?> packagingElement) {
    myArtifact = artifact;
    myContext = context;
    myParentPath = parentPath;
    myPackagingElement = packagingElement;
  }

  @Override
  public @NotNull ProjectStructureElement getContainingElement() {
    return myContext.getOrCreateArtifactElement(myArtifact);
  }

  @Override
  public String getPlacePath() {
    if (myParentPath != null && myPackagingElement != null) {
      //todo use id of element?
      return myParentPath + "/" + myPackagingElement.getType().getId();
    }
    return null;
  }

  @Override
  public @NotNull ActionCallback navigate() {
    final Artifact artifact = myContext.getArtifactModel().getArtifactByOriginal(myArtifact);
    return myContext.getProjectStructureConfigurable().select(myArtifact, true).doWhenDone(() -> {
      final ArtifactEditorEx artifactEditor = (ArtifactEditorEx)myContext.getOrCreateEditor(artifact);
      if (myParentPath != null && myPackagingElement != null) {
        artifactEditor.getLayoutTreeComponent().selectNode(myParentPath, myPackagingElement);
      }
    });
  }
}
