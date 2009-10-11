/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public abstract class ArtifactModelBase implements ArtifactModel {
  private Map<String, Artifact> myArtifactsMap;
  private Artifact[] myArtifactsArray;

  protected abstract List<? extends Artifact> getArtifactsList();

  @NotNull
  public Artifact[] getArtifacts() {
    if (myArtifactsArray == null) {
      final List<? extends Artifact> artifacts = getArtifactsList();
      myArtifactsArray = artifacts.toArray(new Artifact[artifacts.size()]);
    }
    return myArtifactsArray;
  }

  public Artifact findArtifact(@NotNull String name) {
    if (myArtifactsMap == null) {
      myArtifactsMap = new HashMap<String, Artifact>();
      for (Artifact artifact : getArtifactsList()) {
        myArtifactsMap.put(artifact.getName(), artifact);
      }
    }
    return myArtifactsMap.get(name);
  }

  @NotNull
  public Artifact getArtifactByOriginal(@NotNull Artifact artifact) {
    return artifact;
  }

  public Collection<? extends Artifact> getArtifactsByType(@NotNull ArtifactType type) {
    final List<Artifact> result = new ArrayList<Artifact>();
    for (Artifact artifact : getArtifacts()) {
      if (artifact.getArtifactType().equals(type)) {
        result.add(artifact);
      }
    }
    return result;
  }

  protected void artifactsChanged() {
    myArtifactsMap = null;
    myArtifactsArray = null;
  }
}
