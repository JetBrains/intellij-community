package com.intellij.facet.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author Dmitry Avdeev
 */
public class FacetDependentToolWindow extends ToolWindowEP {

  public static final ExtensionPointName<FacetDependentToolWindow> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.facet.toolWindow");

  @Attribute("facetId")
  public String facetId;
}
