// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.importing;

import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

/**
 * {@link ProjectResolverPolicy} allows to define requirements for the project data produced by {@link ExternalSystemProjectResolver}
 */
@ApiStatus.Experimental
public interface ProjectResolverPolicy extends Serializable {
  /**
   * Indicates if {@link ExternalSystemProjectResolver} have to provide all available project models for the target project
   * or can resolve only subset of them.
   */
  boolean isPartialDataResolveAllowed();
}
