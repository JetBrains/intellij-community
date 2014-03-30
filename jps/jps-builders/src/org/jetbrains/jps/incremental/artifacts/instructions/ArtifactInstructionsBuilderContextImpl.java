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
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class ArtifactInstructionsBuilderContextImpl implements ArtifactInstructionsBuilderContext {
  private final Set<JpsArtifact> myParentArtifacts;
  private final JpsModel myModel;
  private final BuildDataPaths myDataPaths;

  public ArtifactInstructionsBuilderContextImpl(JpsModel model, BuildDataPaths dataPaths) {
    myModel = model;
    myDataPaths = dataPaths;
    myParentArtifacts = new HashSet<JpsArtifact>();
  }

  @Override
  public JpsModel getModel() {
    return myModel;
  }

  @Override
  public BuildDataPaths getDataPaths() {
    return myDataPaths;
  }

  @Override
  public boolean enterArtifact(JpsArtifact artifact) {
    return myParentArtifacts.add(artifact);
  }

  @Override
  @NotNull
  public Set<JpsArtifact> getParentArtifacts() {
    return myParentArtifacts;
  }

  @Override
  public void leaveArtifact(JpsArtifact artifact) {
    myParentArtifacts.remove(artifact);
  }
}
