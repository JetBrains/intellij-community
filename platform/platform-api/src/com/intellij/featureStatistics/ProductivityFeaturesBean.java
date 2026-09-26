// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;

/**
 * Contributes a productivity feature XML file to {@link ProductivityFeaturesRegistry}.
 * The registry reads the file through the class loader of the module that declares the extension.
 * Use {@link ProductivityFeaturesProvider} when the contribution needs code, for example an {@link ApplicabilityFilter}.
 */
public final class ProductivityFeaturesBean {
  @ApiStatus.Internal
  public static final ExtensionPointName<ProductivityFeaturesBean> EP_NAME = new ExtensionPointName<>("com.intellij.productivityFeatures");

  /**
   * The resource path of the feature XML file, relative to the class loader root.
   * Example: {@code WebStormProductivityFeatures.xml}.
   */
  @Attribute("file")
  @RequiredElement
  public String file;
}
