// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing;

import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemWorkspaceContributor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
final class JpsDependencyContributor implements ExternalSystemWorkspaceContributor {
  @Override
  public @Nullable ProjectCoordinate findProjectId(Module module) {
    if (Registry.is("external.system.map.jps.to.gav", false)) {
      GAVStateComponent gavStateComponent = module.getProject().getService(GAVStateComponent.class);
      return gavStateComponent.getMapping().get(module.getName());
    }
    return null;
  }
}
