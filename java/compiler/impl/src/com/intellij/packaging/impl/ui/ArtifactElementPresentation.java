// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.packaging.ui.TreeNodePresentation;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ArtifactElementPresentation extends TreeNodePresentation {
  private final ArtifactPointer myArtifactPointer;
  private final ArtifactEditorContext myContext;

  public ArtifactElementPresentation(ArtifactPointer artifactPointer, ArtifactEditorContext context) {
    myArtifactPointer = artifactPointer;
    myContext = context;
  }

  @Override
  public @NlsContexts.Label String getPresentableName() {
    return myArtifactPointer != null ? myArtifactPointer.getArtifactName(myContext.getArtifactModel()) :
           JavaCompilerBundle.message("label.unknown.value");
  }

  @Override
  public boolean canNavigateToSource() {
    return findArtifact() != null;
  }

  @Override
  public void navigateToSource() {
    final Artifact artifact = findArtifact();
    if (artifact != null) {
      myContext.selectArtifact(artifact);
    }
  }

  @Override
  public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    final Artifact artifact = findArtifact();
    Icon icon = artifact != null ? artifact.getArtifactType().getIcon() : AllIcons.Nodes.Artifact;
    presentationData.setIcon(icon);
    presentationData.addText(getPresentableName(), artifact != null ? mainAttributes : SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  private @Nullable Artifact findArtifact() {
    return myArtifactPointer != null ? myArtifactPointer.findArtifact(myContext.getArtifactModel()) : null;
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.ARTIFACT;
  }
}
