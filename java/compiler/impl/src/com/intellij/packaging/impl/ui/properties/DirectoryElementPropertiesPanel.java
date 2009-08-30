package com.intellij.packaging.impl.ui.properties;

import com.intellij.packaging.impl.elements.DirectoryPackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;

/**
 * @author nik
 */
public class DirectoryElementPropertiesPanel extends ElementWithManifestPropertiesPanel<DirectoryPackagingElement> {
  public DirectoryElementPropertiesPanel(DirectoryPackagingElement element, ArtifactEditorContext context) {
    super(element, context);
  }
}
