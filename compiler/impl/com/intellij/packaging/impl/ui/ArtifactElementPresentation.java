package com.intellij.packaging.impl.ui;

import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurable;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArtifactElementPresentation extends PackagingElementPresentation {
  private final Artifact myArtifact;
  private final String myName;

  public ArtifactElementPresentation(String artifactName, Artifact artifact) {
    myName = artifactName;
    myArtifact = artifact;
  }

  public String getPresentableName() {
    return myName;
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.setIcon(ArtifactsStructureConfigurable.ARTIFACT_ICON);
    renderer.append("'" + myName + "' output", myArtifact != null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  @Override
  public double getWeight() {
    return PackagingElementWeights.ARTIFACT;
  }
}
