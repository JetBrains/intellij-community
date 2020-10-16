// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public abstract class ArtifactPointerManager {
  public static ArtifactPointerManager getInstance(@NotNull Project project) {
    return project.getService(ArtifactPointerManager.class);
  }

  public abstract ArtifactPointer createPointer(@NotNull @NlsSafe String name);

  public abstract ArtifactPointer createPointer(@NotNull Artifact artifact);

  public abstract ArtifactPointer createPointer(@NotNull Artifact artifact, @NotNull ArtifactModel artifactModel);
}
