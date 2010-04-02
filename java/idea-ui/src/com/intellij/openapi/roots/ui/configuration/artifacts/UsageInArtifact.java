package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElementUsage;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElement;

import javax.swing.*;

/**
 * @author nik
 */
public class UsageInArtifact extends ProjectStructureElementUsage {
  private final Artifact myOriginalArtifact;
  private final ArtifactsStructureConfigurableContext myContext;
  private final ProjectStructureElement mySourceElement;
  private final ProjectStructureElement myContainingElement;
  private final String myParentPath;
  private final PackagingElement<?> myPackagingElement;

  public UsageInArtifact(Artifact originalArtifact, ArtifactsStructureConfigurableContext context, ProjectStructureElement sourceElement,
                         ArtifactProjectStructureElement containingElement,
                         String parentPath, PackagingElement<?> packagingElement) {
    myOriginalArtifact = originalArtifact;
    myContext = context;
    mySourceElement = sourceElement;
    myContainingElement = containingElement;
    myParentPath = parentPath;
    myPackagingElement = packagingElement;
  }

  @Override
  public ProjectStructureElement getSourceElement() {
    return mySourceElement;
  }

  @Override
  public ProjectStructureElement getContainingElement() {
    return myContainingElement;
  }

  @Override
  public void navigate() {
    final Artifact artifact = myContext.getArtifactModel().getArtifactByOriginal(myOriginalArtifact);
    ProjectStructureConfigurable.getInstance(myContext.getProject()).select(myOriginalArtifact, true).doWhenDone(new Runnable() {
      public void run() {
        final ArtifactEditorEx artifactEditor = (ArtifactEditorEx)myContext.getOrCreateEditor(artifact);
        artifactEditor.getLayoutTreeComponent().selectNode(myParentPath, myPackagingElement);
      }
    });
  }

  @Override
  public String getPresentableName() {
    return myOriginalArtifact.getName();
  }

  @Override
  public int hashCode() {
    return myOriginalArtifact.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof UsageInArtifact && ((UsageInArtifact)obj).myOriginalArtifact.equals(myOriginalArtifact);
  }

  @Override
  public Icon getIcon() {
    return myOriginalArtifact.getArtifactType().getIcon();
  }
}
