package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.*;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.HashMap;

/**
 * @author nik
 */
public class ArtifactPointerManagerImpl extends ArtifactPointerManager {
  private final Project myProject;
  private final Map<String, ArtifactPointerImpl> myPointers = new HashMap<String, ArtifactPointerImpl>();

  public ArtifactPointerManagerImpl(Project project) {
    myProject = project;
    myProject.getMessageBus().connect().subscribe(ArtifactManager.TOPIC, new ArtifactAdapter() {
      @Override
      public void artifactRemoved(@NotNull Artifact artifact) {
        final ArtifactPointerImpl pointer = myPointers.remove(artifact.getName());
        if (pointer != null) {
          pointer.setArtifact(null);
        }
      }

      @Override
      public void artifactChanged(@NotNull Artifact original, Artifact modified) {
        final String oldName = original.getName();
        final ArtifactPointerImpl pointer = myPointers.get(oldName);
        if (pointer != null) {
          pointer.setArtifact(modified);
          final String newName = modified.getName();
          if (!newName.equals(oldName)) {
            pointer.setName(newName);
            myPointers.remove(oldName);
            myPointers.put(newName, pointer);
          }
        }
      }
    });
  }

  public void updateAllPointers() {
    for (ArtifactPointerImpl pointer : myPointers.values()) {
      pointer.getArtifact();
    }
  }

  public ArtifactPointer create(@NotNull String name) {
    ArtifactPointerImpl pointer = myPointers.get(name);
    if (pointer == null) {
      pointer = new ArtifactPointerImpl(myProject, name);
      myPointers.put(name, pointer);
    }
    return pointer;
  }

  public ArtifactPointer create(@NotNull Artifact artifact) {
    ArtifactPointerImpl pointer = myPointers.get(artifact.getName());
    if (pointer == null) {
      pointer = new ArtifactPointerImpl(myProject, artifact);
      myPointers.put(artifact.getName(), pointer);
    }
    return pointer;
  }
}
