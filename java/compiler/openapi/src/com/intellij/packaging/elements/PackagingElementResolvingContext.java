package com.intellij.packaging.elements;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.packaging.artifacts.ArtifactModel;

/**
 * @author nik
 */
public interface PackagingElementResolvingContext {
  @NotNull
  Project getProject();

  @NotNull
  ArtifactModel getArtifactModel();

  @NotNull
  ModulesProvider getModulesProvider();

  @NotNull
  FacetsProvider getFacetsProvider();
}
