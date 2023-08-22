// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class ArtifactBySourceFileFinder {
  public static ArtifactBySourceFileFinder getInstance(@NotNull Project project) {
    return project.getService(ArtifactBySourceFileFinder.class);
  }

  public abstract Collection<? extends Artifact> findArtifacts(@NotNull VirtualFile sourceFile);
}
