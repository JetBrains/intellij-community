// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class ArtifactPointerManagerImpl extends ArtifactPointerManager {
  private static final Logger LOG = Logger.getInstance(ArtifactPointerManagerImpl.class);

  private final Map<String, ArtifactPointerImpl> myNameToPointers = new HashMap<>();
  private final Lock myLock = new ReentrantLock();
  private final @NotNull Project myProject;

  public ArtifactPointerManagerImpl(@NotNull Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(ArtifactManager.TOPIC, new ArtifactListener() {
      @Override
      public void artifactRemoved(@NotNull Artifact artifact) {
        invalidatePointer(artifact);
      }

      @Override
      public void artifactAdded(@NotNull Artifact artifact) {
        myLock.lock();
        try {
          ArtifactPointerImpl pointer = myNameToPointers.get(artifact.getName());
          if (pointer != null) {
            pointer.setArtifact(artifact);
          }
        }
        finally {
          myLock.unlock();
        }
      }

      @Override
      public void artifactChanged(@NotNull Artifact artifact, @NotNull String oldName) {
        myLock.lock();
        try {
          if (!oldName.equals(artifact.getName())) {
            ArtifactPointerImpl artifactPointer = myNameToPointers.remove(oldName);
            if (artifactPointer != null) {
              Artifact artifactFromPointer = artifactPointer.getArtifactNoResolve();
              LOG.assertTrue(artifactFromPointer == null || artifactFromPointer.equals(artifact));
              String newName = artifact.getName();
              artifactPointer.setName(newName);
              myNameToPointers.put(newName, artifactPointer);
            }
          }
        }
        finally {
          myLock.unlock();
        }
      }
    });
  }

  private void invalidatePointer(@NotNull Artifact artifact) {
    myLock.lock();
    try {
      ArtifactPointerImpl pointer = myNameToPointers.get(artifact.getName());
      if (pointer != null) {
        pointer.invalidateArtifact();
      }
    }
    finally {
      myLock.unlock();
    }
  }

  @Override
  public ArtifactPointer createPointer(@NotNull String name) {
    myLock.lock();
    try {
      return myNameToPointers.computeIfAbsent(name, n -> new ArtifactPointerImpl(n, myProject));
    }
    finally {
      myLock.unlock();
    }
  }

  @Override
  public ArtifactPointer createPointer(@NotNull Artifact artifact) {
    myLock.lock();
    try {
      ArtifactPointerImpl pointer =
        myNameToPointers.computeIfAbsent(artifact.getName(), name -> new ArtifactPointerImpl(name, myProject));
      pointer.setArtifact(artifact);
      return pointer;
    }
    finally {
      myLock.unlock();
    }
  }

  @TestOnly
  public @Unmodifiable @NotNull List<Map.Entry<String, ArtifactPointerImpl>> getResolvedPointers() {
    myLock.lock();
    try {
      return ContainerUtil.filter(myNameToPointers.entrySet(), entry -> entry.getValue().getArtifactNoResolve() != null);
    }
    finally {
      myLock.unlock();
    }
  }

  @Override
  public ArtifactPointer createPointer(@NotNull Artifact artifact, @NotNull ArtifactModel artifactModel) {
    return createPointer(artifactModel.getOriginalArtifact(artifact));
  }

  public void disposePointers(@NotNull List<? extends Artifact> artifacts) {
    for (Artifact artifact : artifacts) {
      invalidatePointer(artifact);
    }
  }
}
