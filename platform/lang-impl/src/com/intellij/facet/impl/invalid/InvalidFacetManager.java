// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.impl.invalid;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class InvalidFacetManager {
  public static InvalidFacetManager getInstance(@NotNull Project project) {
    return project.getService(InvalidFacetManager.class);
  }

  public abstract boolean isIgnored(@NotNull InvalidFacet facet);
  public abstract void setIgnored(@NotNull InvalidFacet facet, boolean ignored);

  public abstract List<InvalidFacet> getInvalidFacets();
}
