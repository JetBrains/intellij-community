// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl.invalid;

import com.intellij.facet.Facet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.NlsContexts;

public final class InvalidFacet extends Facet<InvalidFacetConfiguration> {
  public InvalidFacet(InvalidFacetType invalidFacetType,
                      Module module,
                      String name,
                      InvalidFacetConfiguration configuration,
                      Facet underlyingFacet) {
    super(invalidFacetType, module, name, configuration, underlyingFacet);
  }

  public @NlsContexts.DialogMessage String getErrorMessage() {
    return getConfiguration().getErrorMessage();
  }
}
