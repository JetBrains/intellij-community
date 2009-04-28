package com.intellij.packaging.impl.elements;

import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurable;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

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
        return "<output root>";
      }

      @Override
      public void render(@NotNull ColoredTreeCellRenderer renderer) {
        renderer.setIcon(ArtifactsStructureConfigurable.ARTIFACT_ICON);
        renderer.append(getPresentableName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    };
  }

  public Object getState() {
    return null;
  }

  public void loadState(Object state) {
  }

  public void rename(@NotNull String newName) {
  }

  @Override
  public String getName() {
    return "";
  }
}
