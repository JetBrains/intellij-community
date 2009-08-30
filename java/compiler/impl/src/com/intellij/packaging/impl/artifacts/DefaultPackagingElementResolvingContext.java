package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.facet.impl.DefaultFacetsProvider;
import org.jetbrains.annotations.NotNull;

/**
* @author nik
*/
class DefaultPackagingElementResolvingContext implements PackagingElementResolvingContext {
  private final Project myProject;
  private final DefaultModulesProvider myModulesProvider;

  public DefaultPackagingElementResolvingContext(Project project) {
    myProject = project;
    myModulesProvider = new DefaultModulesProvider(myProject);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public ArtifactModel getArtifactModel() {
    return ArtifactManager.getInstance(myProject);
  }

  @NotNull
  public ModulesProvider getModulesProvider() {
    return myModulesProvider;
  }

  @NotNull
  public FacetsProvider getFacetsProvider() {
    return DefaultFacetsProvider.INSTANCE;
  }
}
