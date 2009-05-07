package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactModel;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  protected void artifactsChanged() {
    myArtifactsMap = null;
    myArtifactsArray = null;
  }
}
