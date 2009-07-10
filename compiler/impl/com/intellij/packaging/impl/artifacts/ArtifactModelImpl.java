package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.packaging.artifacts.*;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class ArtifactModelImpl extends ArtifactModelBase implements ModifiableArtifactModel {
  private final List<ArtifactImpl> myOriginalArtifacts;
  private final ArtifactManagerImpl myArtifactManager;
  private final Map<ArtifactImpl, ArtifactImpl> myArtifact2ModifiableCopy = new HashMap<ArtifactImpl, ArtifactImpl>();
  private final Map<ArtifactImpl, ArtifactImpl> myModifiable2Original = new HashMap<ArtifactImpl, ArtifactImpl>();
  private final EventDispatcher<ArtifactListener> myDispatcher = EventDispatcher.create(ArtifactListener.class);

  public ArtifactModelImpl(ArtifactManagerImpl artifactManager) {
    myArtifactManager = artifactManager;
    myOriginalArtifacts = new ArrayList<ArtifactImpl>();
  }

  public void addArtifacts(List<ArtifactImpl> artifacts) {
    for (ArtifactImpl artifact : artifacts) {
      myOriginalArtifacts.add(artifact);
    }
    artifactsChanged();
  }

  protected List<? extends Artifact> getArtifactsList() {
    final List<ArtifactImpl> list = new ArrayList<ArtifactImpl>();
    for (ArtifactImpl artifact : myOriginalArtifacts) {
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

  @NotNull
  public ModifiableArtifact addArtifact(@NotNull final String name, @NotNull ArtifactType artifactType) {
    final String outputPath = ArtifactUtil.getDefaultArtifactOutputPath(name, myArtifactManager.getProject());

    final ArtifactImpl artifact = new ArtifactImpl(generateUniqueName(name), artifactType, false, artifactType.createRootElement(), 
                                                   outputPath, true);
    myOriginalArtifacts.add(artifact);
    myArtifact2ModifiableCopy.put(artifact, artifact);
    myModifiable2Original.put(artifact, artifact);

    artifactsChanged();
    myDispatcher.getMulticaster().artifactAdded(artifact);
    return artifact;
  }

  private String generateUniqueName(String baseName) {
    String name = baseName;
    int i = 2;
    while (true) {
      if (findArtifact(name) == null) {
        return name;
      }
      name = baseName + i++;
    }
  }

  public void addListener(@NotNull ArtifactListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(@NotNull ArtifactListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeArtifact(@NotNull Artifact artifact) {
    final ArtifactImpl artifactImpl = (ArtifactImpl)artifact;
    ArtifactImpl original = myModifiable2Original.remove(artifactImpl);
    if (original != null) {
      myOriginalArtifacts.remove(original);
    }
    else {
      original = artifactImpl;
    }
    myArtifact2ModifiableCopy.remove(original);
    myOriginalArtifacts.remove(original);
    artifactsChanged();
    myDispatcher.getMulticaster().artifactRemoved(original);
  }

  @NotNull
  public ModifiableArtifact getOrCreateModifiableArtifact(@NotNull Artifact artifact) {
    final ArtifactImpl artifactImpl = (ArtifactImpl)artifact;
    if (myModifiable2Original.containsKey(artifactImpl)) {
      return artifactImpl;
    }

    ArtifactImpl modifiableCopy = myArtifact2ModifiableCopy.get(artifactImpl);
    if (modifiableCopy == null) {
      modifiableCopy = artifactImpl.createCopy();
      myArtifact2ModifiableCopy.put(artifactImpl, modifiableCopy);
      myModifiable2Original.put(modifiableCopy, artifactImpl);
      artifactsChanged();
    }
    return modifiableCopy;
  }

  @NotNull
  public ArtifactImpl getArtifactByOriginal(@NotNull Artifact artifact) {
    final ArtifactImpl artifactImpl = (ArtifactImpl)artifact;
    final ArtifactImpl copy = myArtifact2ModifiableCopy.get(artifactImpl);
    return copy != null ? copy : artifactImpl;
  }

  public boolean isModified() {
    return !myOriginalArtifacts.equals(myArtifactManager.getArtifactsList()) || !myArtifact2ModifiableCopy.isEmpty();
  }

  public void commit() {
    myArtifactManager.commit(this);
  }

  public boolean isChanged(@NotNull ArtifactImpl artifact) {
    final ArtifactImpl modifiable = myArtifact2ModifiableCopy.get(artifact);
    return modifiable != null && !modifiable.equals(artifact);
  }

  public List<ArtifactImpl> getOriginalArtifacts() {
    return myOriginalArtifacts;
  }
}
