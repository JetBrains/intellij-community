// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.impl.invalid;

import com.intellij.facet.Facet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.NlsContexts;

public class InvalidFacet extends Facet<InvalidFacetConfiguration> {
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
