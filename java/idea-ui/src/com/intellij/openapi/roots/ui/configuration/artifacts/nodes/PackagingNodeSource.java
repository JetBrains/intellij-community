package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public class PackagingNodeSource {
  private final ComplexPackagingElement<?> mySourceElement;
  private final PackagingElementNode<?> mySourceParentNode;
  private final PackagingElement<?> mySourceParentElement;
  private final Collection<PackagingNodeSource> myParentSources;

  public PackagingNodeSource(@NotNull ComplexPackagingElement<?> sourceElement,
                             @NotNull PackagingElementNode<?> sourceParentNode,
                             @NotNull PackagingElement<?> sourceParentElement,
                             @Nullable Collection<PackagingNodeSource> parentSources) {
    mySourceElement = sourceElement;
    mySourceParentNode = sourceParentNode;
    mySourceParentElement = sourceParentElement;
    myParentSources = parentSources;
  }

  @NotNull
  public ComplexPackagingElement<?> getSourceElement() {
    return mySourceElement;
  }

  @Nullable
  public Artifact getSourceArtifact() {
    if (mySourceElement instanceof ArtifactPackagingElement) {
      return ((ArtifactPackagingElement)mySourceElement).findArtifact(mySourceParentNode.getContext());
    }
    return null;
  }

  @NotNull
  public PackagingElementNode<?> getSourceParentNode() {
    return mySourceParentNode;
  }

  public PackagingElement<?> getSourceParentElement() {
    return mySourceParentElement;
  }

  public Collection<PackagingNodeSource> getParentSources() {
    return myParentSources;
  }

  public String getPresentableName() {
    return mySourceElement.createPresentation(mySourceParentNode.getContext()).getPresentableName();
  }
}
