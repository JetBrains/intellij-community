package org.jetbrains.jps.model.artifact;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
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

  public abstract <P extends JpsElement> JpsArtifact createArtifact(@NotNull String name, @NotNull JpsCompositePackagingElement rootElement,
                                                                    @NotNull JpsArtifactType<P> type, @NotNull P properties);

  public abstract List<JpsArtifact> getArtifacts(@NotNull JpsProject project);

  public abstract List<JpsArtifact> getSortedArtifacts(@NotNull JpsProject project);

  public abstract <P extends JpsElement> JpsArtifact addArtifact(@NotNull JpsProject project, @NotNull String name,
                                                                 @NotNull JpsCompositePackagingElement rootElement,
                                          @NotNull JpsArtifactType<P> type, @NotNull P properties);

  public abstract JpsArtifactReference createReference(@NotNull String artifactName);
}
