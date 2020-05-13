// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.ui;

import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;

import java.util.List;

/**
 * Provides tool window if any of the specified facets are present.
 *
 * @author Dmitry Avdeev
 */
public class FacetDependentToolWindow extends ToolWindowEP {

  public static final ExtensionPointName<FacetDependentToolWindow> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.facet.toolWindow");

  /**
   * Comma-delimited list of facet ids.
   */
  @RequiredElement
  @Attribute("facetIdList")
  public String facetIdList;

  public String[] getFacetIds() {
    return facetIdList.split(",");
  }

  public List<FacetType> getFacetTypes() {
    return ContainerUtil.mapNotNull(getFacetIds(),
                                    (NullableFunction<String, FacetType>)facetId -> FacetTypeRegistry.getInstance().findFacetType(facetId));
  }
}
