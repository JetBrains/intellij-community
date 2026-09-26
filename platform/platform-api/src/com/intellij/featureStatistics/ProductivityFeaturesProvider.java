// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * Contributes productivity features to {@link ProductivityFeaturesRegistry} through code.
 * <p>
 * A plugin registers a provider through the {@code com.intellij.productivityFeaturesProvider} extension point
 * when it needs an {@link ApplicabilityFilter} or builds its descriptors programmatically.
 * The registry loads the files from {@link #getXmlFilesUrls()} through the class loader of the module that declares the extension,
 * so the provider class and the XML files must live in the same module.
 * <p>
 * To contribute only an XML file, declare a {@link ProductivityFeaturesBean} through the {@code com.intellij.productivityFeatures}
 * extension point instead. It needs no class.
 */
public abstract class ProductivityFeaturesProvider {
  public static final ExtensionPointName<ProductivityFeaturesProvider> EP_NAME = new ExtensionPointName<>("com.intellij.productivityFeaturesProvider");

  public FeatureDescriptor[] getFeatureDescriptors() {
    return new FeatureDescriptor[0];
  }

  public GroupDescriptor[] getGroupDescriptors() {
    return new GroupDescriptor[0];
  }

  public ApplicabilityFilter[] getApplicabilityFilters() {
    return new ApplicabilityFilter[0];
  }

  /**
   * @return list of xml file paths with features configurations
   */
  public @NotNull Collection<String> getXmlFilesUrls() {
    return Collections.emptyList();
  }
}
