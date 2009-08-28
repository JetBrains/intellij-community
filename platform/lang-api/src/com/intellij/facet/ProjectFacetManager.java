package com.intellij.facet;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class ProjectFacetManager {

  public static ProjectFacetManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectFacetManager.class);
  }

  public abstract <F extends Facet> List<F> getFacets(@NotNull FacetTypeId<F> typeId, final Module[] modules);

  public abstract <F extends Facet> List<F> getFacets(@NotNull FacetTypeId<F> typeId);

  public abstract <C extends FacetConfiguration> C createDefaultConfiguration(@NotNull FacetType<?, C> facetType);

  public abstract <C extends FacetConfiguration> void setDefaultConfiguration(@NotNull FacetType<?, C> facetType, @NotNull C configuration);
}
