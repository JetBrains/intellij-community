// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.run;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

public final class ArtifactChooser extends ElementsChooser<ArtifactPointer> {
  private static final Comparator<ArtifactPointer> ARTIFACT_COMPARATOR = (o1, o2) -> {
    return o1.getArtifactName().compareToIgnoreCase(o2.getArtifactName());
  };

  private static final ElementProperties INVALID_ARTIFACT_PROPERTIES = new ElementProperties() {
    @Override
    public Icon getIcon() {
      return AllIcons.Nodes.Artifact;
    }

    @Override
    public Color getColor() {
      return JBColor.RED;
    }
  };

  public ArtifactChooser(List<ArtifactPointer> pointers) {
    super(pointers, false);

    for (ArtifactPointer pointer : pointers) {
      if (pointer.getArtifact() == null) {
        setElementProperties(pointer, INVALID_ARTIFACT_PROPERTIES);
      }
    }
    sort(ARTIFACT_COMPARATOR);
  }

  @Override
  protected String getItemText(@NotNull ArtifactPointer value) {
    return value.getArtifactName();
  }

  @Override
  protected Icon getItemIcon(@NotNull ArtifactPointer value) {
    final Artifact artifact = value.getArtifact();
    return artifact != null ? artifact.getArtifactType().getIcon() : null;
  }
}
