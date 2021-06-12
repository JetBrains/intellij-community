// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.importing;

import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectsWorkspaceImpl;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

@ApiStatus.Experimental
public class JpsDependencyContributor implements ExternalProjectsWorkspaceImpl.Contributor {

  @Override
  public @Nullable ProjectCoordinate findProjectId(Module module,
                                                   IdeModifiableModelsProvider modelsProvider) {
    if (Registry.is("external.system.map.jps.to.gav", false)) {
      GAVStateComponent gavStateComponent = module.getProject().getService(GAVStateComponent.class);
      return gavStateComponent.getMapping().get(module.getName());
    }
    return null;
  }
}
