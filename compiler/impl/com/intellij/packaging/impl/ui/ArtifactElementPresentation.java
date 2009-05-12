package com.intellij.packaging.impl.ui;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArtifactElementPresentation extends PackagingElementPresentation {
  private final Artifact myArtifact;
  private final PackagingEditorContext myContext;
  private final String myName;

  public ArtifactElementPresentation(String artifactName, Artifact artifact, PackagingEditorContext context) {
    myName = artifactName;
    myArtifact = artifact;
    myContext = context;
  }

  public String getPresentableName() {
    return myName;
  }

  @Override
  public boolean canNavigateToSource() {
    return myArtifact != null;
  }

  @Override
  public void navigateToSource() {
    ProjectStructureConfigurable.getInstance(myContext.getProject()).select(myArtifact, true);
  }

  public void render(@NotNull PresentationData presentationData) {
    presentationData.setIcons(myArtifact != null ? myArtifact.getArtifactType().getIcon() : PlainArtifactType.ARTIFACT_ICON);
    presentationData.addText(myName, myArtifact != null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.ARTIFACT;
  }
}
