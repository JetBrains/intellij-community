package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.artifacts.ArtifactListener;
import com.intellij.packaging.impl.elements.ArtifactRootElementImpl;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * @author nik
 */
public class ArtifactModelImpl extends ArtifactModelBase implements ModifiableArtifactModel {
  private final List<ArtifactImpl> myArtifacts;
  private final ArtifactManagerImpl myArtifactManager;
  private final Map<ArtifactImpl, ArtifactImpl> myArtifact2ModifiableCopy = new HashMap<ArtifactImpl, ArtifactImpl>();
  private final EventDispatcher<ArtifactListener> myDispatcher = EventDispatcher.create(ArtifactListener.class);

  public ArtifactModelImpl(ArtifactManagerImpl artifactManager) {
    myArtifactManager = artifactManager;
    myArtifacts = new ArrayList<ArtifactImpl>();
  }

  public void addArtifacts(List<ArtifactImpl> artifacts) {
    for (ArtifactImpl artifact : artifacts) {
      myArtifacts.add(artifact);
    }
    artifactsChanged();
  }

  protected List<? extends Artifact> getArtifactsList() {
    return computeArtifactsList();
  }

  @NotNull
  public ModifiableArtifact addArtifact(final String name) {
    final ArtifactImpl artifact = new ArtifactImpl(name, true, new ArtifactRootElementImpl(), null);
    myArtifacts.add(artifact);
    myArtifact2ModifiableCopy.put(artifact, artifact);
    artifactsChanged();
    myDispatcher.getMulticaster().artifactAdded(artifact);
    return artifact;
  }

  public void addListener(@NotNull ArtifactListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(@NotNull ArtifactListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeArtifact(@NotNull Artifact artifact) {
    myArtifacts.remove(artifact);
    myArtifact2ModifiableCopy.remove(artifact);
    artifactsChanged();
    myDispatcher.getMulticaster().artifactRemoved(artifact);
  }

  @NotNull
  public ModifiableArtifact getOrCreateModifiableArtifact(@NotNull Artifact artifact) {
    final ArtifactImpl artifactImpl = (ArtifactImpl)artifact;
    ArtifactImpl modifiableCopy = myArtifact2ModifiableCopy.get(artifactImpl);
    if (modifiableCopy == null) {
      modifiableCopy = artifactImpl.createCopy();
      myArtifact2ModifiableCopy.put(artifactImpl, modifiableCopy);
      artifactsChanged();
    }
    return modifiableCopy;
  }

  @NotNull
  public Artifact getModifiableOrOriginal(@NotNull Artifact artifact) {
    final ArtifactImpl copy = myArtifact2ModifiableCopy.get(artifact);
    return copy != null ? copy : artifact;
  }

  public boolean isModified() {
    return !myArtifacts.equals(myArtifactManager.getArtifactsList()) || !myArtifact2ModifiableCopy.isEmpty();
  }

  public void commit() {
    myArtifactManager.commit(computeArtifactsList());
  }

  private List<ArtifactImpl> computeArtifactsList() {
    final List<ArtifactImpl> list = new ArrayList<ArtifactImpl>();
    for (ArtifactImpl artifact : myArtifacts) {
      final ArtifactImpl copy = myArtifact2ModifiableCopy.get(artifact);
      if (copy != null) {
        list.add(copy);
      }
      else {
        list.add(artifact);
      }
    }
    return list;
  }
}
