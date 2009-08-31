package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.ComplexElementSubstitutionParameters;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.ui.ArtifactEditorContext;

import java.util.Collections;

/**
 * @author nik
 */
public class ArtifactRootNode extends CompositePackagingElementNode {
  public ArtifactRootNode(ArtifactEditorEx artifactEditor, ArtifactEditorContext context,
                          ComplexElementSubstitutionParameters substitutionParameters, ArtifactType artifactType) {
    super(artifactEditor.getRootElement(), context, null, null, substitutionParameters, Collections.<PackagingNodeSource>emptyList(),
          artifactType);
  }

  @Override
  public boolean isAutoExpandNode() {
    return true;
  }
}
