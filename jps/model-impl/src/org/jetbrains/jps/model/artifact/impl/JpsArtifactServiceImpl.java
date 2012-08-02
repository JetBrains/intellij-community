package org.jetbrains.jps.model.artifact.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactReference;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.impl.JpsElementCollectionImpl;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsArtifactServiceImpl extends JpsArtifactService {
  @Override
  public List<JpsArtifact> getArtifacts(@NotNull JpsProject project) {
    JpsElementCollectionImpl<JpsArtifact> collection = project.getContainer().getChild(JpsArtifactRole.ARTIFACT_COLLECTION_ROLE);
    return collection != null ? collection.getElements() : Collections.<JpsArtifact>emptyList();
  }

  @Override
  public JpsArtifact addArtifact(@NotNull JpsProject project, @NotNull String name, @NotNull JpsCompositePackagingElement rootElement,
                                 @NotNull JpsArtifactType type) {
    JpsArtifact artifact = createArtifact(name, rootElement, type);
    return project.getContainer().getOrSetChild(JpsArtifactRole.ARTIFACT_COLLECTION_ROLE).addChild(artifact);
  }

  @Override
  public JpsArtifact createArtifact(String name, JpsCompositePackagingElement rootElement, JpsArtifactType type) {
    return new JpsArtifactImpl(name, rootElement, type);
  }

  @Override
  public JpsArtifactReference createReference(@NotNull String artifactName) {
    return new JpsArtifactReferenceImpl(artifactName);
  }
}
