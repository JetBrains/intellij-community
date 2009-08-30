package com.intellij.packaging.impl.run;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactChooser extends ElementsChooser<ArtifactPointer> {
  private static final Comparator<ArtifactPointer> ARTIFACT_COMPARATOR = new Comparator<ArtifactPointer>() {
    public int compare(ArtifactPointer o1, ArtifactPointer o2) {
      return o1.getName().compareToIgnoreCase(o2.getName());
    }
  };
  private static final ElementProperties INVALID_ARTIFACT_PROPERTIES = new ElementProperties() {
    public Icon getIcon() {
      return PlainArtifactType.ARTIFACT_ICON;
    }

    public Color getColor() {
      return Color.RED;
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
    return value.getName();
  }

  @Override
  protected Icon getItemIcon(ArtifactPointer value) {
    final Artifact artifact = value.getArtifact();
    return artifact != null ? artifact.getArtifactType().getIcon() : null;
  }
}
