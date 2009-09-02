package com.intellij.packaging.impl.ui;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.packaging.ui.TreeNodePresentation;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArtifactElementPresentation extends TreeNodePresentation {
  private final Artifact myArtifact;
  private final ArtifactEditorContext myContext;
  private final String myName;

  public ArtifactElementPresentation(ArtifactPointer artifactPointer, ArtifactEditorContext context) {
    myName = artifactPointer != null ? artifactPointer.getName() : "<unknown>";
    myArtifact = artifactPointer != null ? artifactPointer.findArtifact(context.getArtifactModel()) : null;
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
    myContext.selectArtifact(myArtifact);
  }

  public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes, SimpleTextAttributes commentAttributes) {
    presentationData.setIcons(myArtifact != null ? myArtifact.getArtifactType().getIcon() : PlainArtifactType.ARTIFACT_ICON);
    presentationData.addText(myName, myArtifact != null ? mainAttributes : SimpleTextAttributes.ERROR_ATTRIBUTES);
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.ARTIFACT;
  }
}
