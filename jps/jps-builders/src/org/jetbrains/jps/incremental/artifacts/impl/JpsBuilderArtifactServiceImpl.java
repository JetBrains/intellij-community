package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.incremental.artifacts.JpsBuilderArtifactService;
import org.jetbrains.jps.incremental.artifacts.JpsSyntheticArtifactProvider;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.impl.JpsElementChildRoleBase;
import org.jetbrains.jps.model.impl.JpsElementCollectionImpl;
import org.jetbrains.jps.model.impl.JpsElementCollectionRole;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class JpsBuilderArtifactServiceImpl extends JpsBuilderArtifactService {
  private static final JpsElementCollectionRole<JpsArtifact> SYNTHETIC_ARTIFACTS = JpsElementCollectionRole.create(JpsElementChildRoleBase.<JpsArtifact>create("synthetic artifact"));

  @Override
  public Collection<JpsArtifact> getArtifacts(JpsModel model, boolean includeSynthetic) {
    List<JpsArtifact> artifacts = JpsArtifactService.getInstance().getArtifacts(model.getProject());
    if (!includeSynthetic) {
      return artifacts;
    }
    return ContainerUtil.concat(artifacts, getSyntheticArtifacts(model));
  }

  public List<JpsArtifact> getSyntheticArtifacts(final JpsModel model) {
    JpsElementCollectionImpl<JpsArtifact> artifactsCollection = model.getProject().getContainer().getChild(SYNTHETIC_ARTIFACTS);
    if (artifactsCollection == null) {
      List<JpsArtifact> artifactList = computeSyntheticArtifacts(model);
      artifactsCollection = model.getProject().getContainer().setChild(SYNTHETIC_ARTIFACTS);
      for (JpsArtifact artifact : artifactList) {
        artifactsCollection.addChild(artifact);
      }
    }
    return artifactsCollection.getElements();
  }

  private static List<JpsArtifact> computeSyntheticArtifacts(JpsModel model) {
    List<JpsArtifact> artifacts = new ArrayList<JpsArtifact>();
    for (JpsSyntheticArtifactProvider provider : JpsServiceManager.getInstance().getExtensions(JpsSyntheticArtifactProvider.class)) {
      artifacts.addAll(provider.createArtifacts(model));
    }
    return artifacts;
  }
}
