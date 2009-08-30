package com.intellij.packaging.impl.ui.properties;

import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;

/**
 * @author nik
 */
public class ArchiveElementPropertiesPanel extends ElementWithManifestPropertiesPanel<ArchivePackagingElement> {
  public ArchiveElementPropertiesPanel(ArchivePackagingElement element, final ArtifactEditorContext context) {
    super(element, context);
  }
}
