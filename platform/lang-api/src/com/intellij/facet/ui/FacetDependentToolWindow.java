// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.ui;

import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides tool window if any of the specified facets are present.
 *
 * @author Dmitry Avdeev
 */
public final class FacetDependentToolWindow extends ToolWindowEP {
  public static final ExtensionPointName<FacetDependentToolWindow> EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.facet.toolWindow");

  /**
   * Comma-delimited list of facet ids.
   */
  @RequiredElement
  @Attribute("facetIdList")
  public String facetIdList;

  public @NotNull String[] getFacetIds() {
    return facetIdList.split(",");
  }

  public @NotNull List<FacetType<?, ?>> getFacetTypes() {
    String @NotNull [] facetIds = getFacetIds();
    if (facetIds.length == 0) {
      return Collections.emptyList();
    }

    List<FacetType<?, ?>> result = new ArrayList<>(facetIds.length);
    FacetTypeRegistry facetTypeRegistry = FacetTypeRegistry.getInstance();
    for (String facetId : facetIds) {
      FacetType<?, ?> o = facetTypeRegistry.findFacetType(facetId);
      if (o != null) {
        result.add(o);
      }
    }
    return result.isEmpty() ? Collections.emptyList() : result;
  }
}
