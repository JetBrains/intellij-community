package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.jps.incremental.artifacts.impl.JpsBuilderArtifactServiceImpl;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class JpsBuilderArtifactService {
  private static JpsBuilderArtifactService ourInstance = new JpsBuilderArtifactServiceImpl();

  public static JpsBuilderArtifactService getInstance() {
    return ourInstance;
  }

  public abstract Collection<JpsArtifact> getArtifacts(JpsModel model, boolean includeSynthetic);

  public abstract Collection<JpsArtifact> getSyntheticArtifacts(JpsModel model);
}
