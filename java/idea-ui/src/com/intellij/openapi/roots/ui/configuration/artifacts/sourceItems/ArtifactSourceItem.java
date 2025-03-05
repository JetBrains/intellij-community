// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.impl.artifacts.JarArtifactType;
import com.intellij.packaging.impl.ui.ArtifactElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.SourceItemWeights;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ArtifactSourceItem extends PackagingSourceItem {
  private final Artifact myArtifact;

  public ArtifactSourceItem(@NotNull Artifact artifact) {
    myArtifact = artifact;
  }

  @Override
  public @NotNull SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    final ArtifactPointer pointer = ArtifactPointerManager.getInstance(context.getProject()).createPointer(myArtifact, context.getArtifactModel());
    return new DelegatedSourceItemPresentation(new ArtifactElementPresentation(pointer, context)) {
      @Override
      public int getWeight() {
        return SourceItemWeights.ARTIFACT_WEIGHT;
      }
    };
  }

  @Override
  public @NotNull List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
    final Project project = context.getProject();
    final ArtifactPointer pointer = ArtifactPointerManager.getInstance(project).createPointer(myArtifact, context.getArtifactModel());
    return Collections.singletonList(PackagingElementFactory.getInstance().createArtifactElement(pointer, project));
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ArtifactSourceItem && myArtifact.equals(((ArtifactSourceItem)obj).myArtifact);
  }

  @Override
  public @NotNull PackagingElementOutputKind getKindOfProducedElements() {
    return myArtifact.getArtifactType() instanceof JarArtifactType ? PackagingElementOutputKind.JAR_FILES : PackagingElementOutputKind.OTHER;
  }

  public Artifact getArtifact() {
    return myArtifact;
  }

  @Override
  public int hashCode() {
    return myArtifact.hashCode();
  }
}
