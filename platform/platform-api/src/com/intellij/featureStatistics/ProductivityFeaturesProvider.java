// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public abstract class ProductivityFeaturesProvider {
  public static final ExtensionPointName<ProductivityFeaturesProvider> EP_NAME = new ExtensionPointName<>("com.intellij.productivityFeaturesProvider");
  
  public abstract FeatureDescriptor[] getFeatureDescriptors();

  public abstract GroupDescriptor[] getGroupDescriptors();

  public abstract ApplicabilityFilter[] getApplicabilityFilters();

  /**
   * @return list of xml file urls with features configurations
   */
  public @NotNull Collection<String> getXmlFilesUrls() {
    return Collections.emptyList();
  }
}
