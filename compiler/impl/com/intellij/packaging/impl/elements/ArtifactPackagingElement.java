package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.Generator;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.ui.ArtifactElementPresentation;
import com.intellij.packaging.impl.ui.DelegatedPackagingElementPresentation;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactPackagingElement extends ComplexPackagingElement<ArtifactPackagingElement> {
  private String myArtifactName;

  public ArtifactPackagingElement() {
    super(ArtifactElementType.ARTIFACT_ELEMENT_TYPE);
  }

  public ArtifactPackagingElement(String artifactName) {
    super(ArtifactElementType.ARTIFACT_ELEMENT_TYPE);
    myArtifactName = artifactName;
  }

  public List<? extends PackagingElement<?>> getSubstitution(@NotNull PackagingElementResolvingContext context) {
    final Artifact artifact = findArtifact(context);
    if (artifact != null) {
      final List<PackagingElement<?>> elements = new ArrayList<PackagingElement<?>>();
      elements.addAll(artifact.getRootElement().getChildren());
      return elements;
    }
    return null;
  }

  @Override
  public List<? extends Generator> computeCopyInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                           @NotNull CopyInstructionCreator creator,
                                                           @NotNull ArtifactGenerationContext generationContext) {
    final Artifact artifact = findArtifact(resolvingContext);
    if (artifact != null) {
      final String outputPath = BuildProperties.propertyRef(generationContext.getArtifactOutputProperty(artifact));
      return Collections.singletonList(creator.createDirectoryContentCopyInstruction(outputPath));
    }
    return Collections.emptyList();
  }

  public PackagingElementPresentation createPresentation(PackagingEditorContext context) {
    return new DelegatedPackagingElementPresentation(new ArtifactElementPresentation(myArtifactName, findArtifact(context), context));
  }

  public ArtifactPackagingElement getState() {
    return this;
  }

  public void loadState(ArtifactPackagingElement state) {
    myArtifactName = state.getArtifactName();
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof ArtifactPackagingElement && myArtifactName != null
           && myArtifactName.equals(((ArtifactPackagingElement)element).getArtifactName());
  }

  @Attribute("artifact-name")
  public String getArtifactName() {
    return myArtifactName;
  }

  @Nullable
  public Artifact findArtifact(@NotNull PackagingElementResolvingContext context) {
    return context.getArtifactModel().findArtifact(myArtifactName);
  }

  public void setArtifactName(String artifactName) {
    myArtifactName = artifactName;
  }
}
