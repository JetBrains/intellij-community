// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * Provides icon description tooltips for {@link SimpleColoredComponent} renderers.
 * <p/>
 * Create {@code icon.<icon-path>.tooltip} key in given resource bundle where {@code <icon-path>} is the icon path with leading slash
 * and {@code .svg} removed and slashes replaced with dots (e.g., {@code /nodes/class.svg} -> {@code icon.nodes.class.tooltip}).
 */
public final class IconDescriptionBundleEP {
  public static final ExtensionPointName<IconDescriptionBundleEP> EP_NAME = new ExtensionPointName<>("com.intellij.iconDescriptionBundle");

  /**
   * Path to resource bundle.
   */
  @Attribute("resourceBundle")
  @RequiredElement
  public String resourceBundle;
}
