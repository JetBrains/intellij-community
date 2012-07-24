package org.jetbrains.jps.model.artifact;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsArtifactService {

  public static JpsArtifactService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsArtifactService.class);
  }

  public abstract List<JpsArtifact> getArtifacts(@NotNull JpsProject project);

  public abstract JpsArtifact addArtifact(@NotNull JpsProject project,
                                          @NotNull String name,
                                          @NotNull JpsCompositePackagingElement rootElement,
                                          @NotNull JpsArtifactType type);

  public abstract JpsArtifactReference createReference(@NotNull String artifactName);
}
