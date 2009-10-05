package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.impl.artifacts.JarArtifactType;
import com.intellij.packaging.impl.ui.ArtifactElementPresentation;
import com.intellij.packaging.ui.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactSourceItem extends PackagingSourceItem {
  private final Artifact myArtifact;

  public ArtifactSourceItem(@NotNull Artifact artifact) {
    myArtifact = artifact;
  }

  public SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    final ArtifactPointer pointer = ArtifactPointerManager.getInstance(context.getProject()).create(myArtifact);
    return new DelegatedSourceItemPresentation(new ArtifactElementPresentation(pointer, context)) {
      @Override
      public int getWeight() {
        return SourceItemWeights.ARTIFACT_WEIGHT;
      }
    };
  }

  @NotNull
  public List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
    return Collections.singletonList(PackagingElementFactory.getInstance().createArtifactElement(myArtifact, context.getProject()));
  }

  public boolean equals(Object obj) {
    return obj instanceof ArtifactSourceItem && myArtifact.equals(((ArtifactSourceItem)obj).myArtifact);
  }

  @NotNull
  @Override
  public PackagingElementOutputKind getKindOfProducedElements() {
    return myArtifact.getArtifactType() instanceof JarArtifactType ? PackagingElementOutputKind.JAR_FILES : PackagingElementOutputKind.OTHER;
  }

  public int hashCode() {
    return myArtifact.hashCode();
  }
}
