package com.intellij.facet.ui;

import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class FacetDependentToolWindow extends ToolWindowEP {

  public static final ExtensionPointName<FacetDependentToolWindow> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.facet.toolWindow");

  /** comma-delimited list of facet ids */
  @Attribute("facetIdList")
  public String facetIdList;

  public String[] getFacetIds() {
    return facetIdList.split(",");
  }

  public List<FacetType> getFacetTypes() {
    return ContainerUtil.mapNotNull(getFacetIds(), new NullableFunction<String, FacetType>() {
      @Override
      public FacetType fun(String facetId) {
        return FacetTypeRegistry.getInstance().findFacetType(facetId);
      }
    });
  }
}
