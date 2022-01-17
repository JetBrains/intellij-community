// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ResourceBundle;

/**
 * Provides type name for instances of given class.
 * <p/>
 * Register via {@code com.intellij.typeName} extension point.
 */
public final class TypeNameEP implements PluginAware {
  public static final ExtensionPointName<TypeNameEP> EP_NAME = new ExtensionPointName<>("com.intellij.typeName");

  private PluginDescriptor pluginDescriptor;

  @Attribute("className")
  @RequiredElement
  public String className;

  /**
   * Use {@link #resourceKey} for i18n.
   */
  @Attribute("name")
  @Nls(capitalization = Nls.Capitalization.Title)
  public String name;

  /**
   * If unspecified, plugin's {@code <resource-bundle>} will be used.
   */
  @Attribute("resourceBundle")
  public String resourceBundle;

  @Attribute("resourceKey")
  @Nls(capitalization = Nls.Capitalization.Title)
  public String resourceKey;

  private final NullableLazyValue<String> myName = new NullableLazyValue<>() {
    @Override
    protected String compute() {
      if (name != null) {
        return name;
      }
      if (resourceKey != null) {
        String bundleName = resourceBundle;
        if (bundleName == null && pluginDescriptor != null) {
          bundleName = pluginDescriptor.getResourceBundleBaseName();
        }
        if (bundleName != null) {
          ResourceBundle bundle = DynamicBundle.INSTANCE.getResourceBundle(bundleName, pluginDescriptor.getClassLoader());
          return BundleBase.message(bundle, resourceKey);
        }
      }
      return null;
    }
  };

  public NullableLazyValue<String> getTypeName() {
    return myName;
  }

  @Override
  @Transient
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
