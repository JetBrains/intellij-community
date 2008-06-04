package com.intellij.facet.impl;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ProjectFacetManagerEx extends ProjectFacetManager {

  public static ProjectFacetManagerEx getInstanceEx(@NotNull Project project) {
    return (ProjectFacetManagerEx)getInstance(project);
  }

  public abstract void registerFacetLoadingError(@NotNull FacetLoadingErrorDescription errorDescription);
}
