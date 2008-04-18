package com.intellij.facet;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ProjectFacetManager {

  public static ProjectFacetManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectFacetManager.class);
  }

  public abstract <C extends FacetConfiguration> C createDefaultConfiguration(@NotNull FacetType<?, C> facetType);

  public abstract <C extends FacetConfiguration> void setDefaultConfiguration(@NotNull FacetType<?, C> facetType, @NotNull C configuration);
}
