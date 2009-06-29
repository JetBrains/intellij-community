package com.intellij.packaging.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ArtifactPointerManager {
  public static ArtifactPointerManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ArtifactPointerManager.class);
  }

  public abstract ArtifactPointer create(@NotNull String name);

  public abstract ArtifactPointer create(@NotNull Artifact artifact);
}
