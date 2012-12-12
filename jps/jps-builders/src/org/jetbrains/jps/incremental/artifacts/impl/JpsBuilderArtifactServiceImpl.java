/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental.artifacts.impl;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.incremental.artifacts.JpsBuilderArtifactService;
import org.jetbrains.jps.incremental.artifacts.JpsSyntheticArtifactProvider;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.ex.JpsElementCollectionRole;
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
    JpsElementCollection<JpsArtifact> artifactsCollection = model.getProject().getContainer().getChild(SYNTHETIC_ARTIFACTS);
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
