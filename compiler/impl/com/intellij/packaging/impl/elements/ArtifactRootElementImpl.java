package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.packaging.elements.ArtifactGenerationContext;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.CopyInstructionCreator;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class ArtifactRootElementImpl extends ArtifactRootElement<Object> {
  public ArtifactRootElementImpl() {
    super(PackagingElementFactoryImpl.ARTIFACT_ROOT_ELEMENT_TYPE);
  }

  public PackagingElementPresentation createPresentation(PackagingEditorContext context) {
    return new PackagingElementPresentation() {
      @Override
      public String getPresentableName() {
        return CompilerBundle.message("packaging.element.text.output.root");
      }

      @Override
      public void render(@NotNull PresentationData presentationData) {
        presentationData.setIcons(PlainArtifactType.ARTIFACT_ICON);
        presentationData.addText(getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }

      @Override
      public int getWeight() {
        return 0;
      }
    };
  }

  public Object getState() {
    return null;
  }

  public void loadState(Object state) {
  }

  @Override
  public boolean canBeRenamed() {
    return false;
  }

  public void rename(@NotNull String newName) {
  }

  public List<? extends Generator> computeCopyInstructions(@NotNull PackagingElementResolvingContext resolvingContext, @NotNull CopyInstructionCreator creator,
                                                   @NotNull ArtifactGenerationContext generationContext) {
    throw new UnsupportedOperationException("'computeGenerators' not implemented in " + getClass().getName());
  }

  @Override
  public String getName() {
    return "";
  }
}
