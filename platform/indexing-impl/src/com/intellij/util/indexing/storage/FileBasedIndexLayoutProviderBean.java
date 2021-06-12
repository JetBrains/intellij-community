// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.storage;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ResourceBundle;

@ApiStatus.Internal
public final class FileBasedIndexLayoutProviderBean implements PluginAware {
  /**
   * A class which implements {@link FileBasedIndexLayoutProvider}
   */
  @RequiredElement
  @Attribute("providerClass")
  public String providerClass;

  /**
   * Unique storage id which is supposed to be used only for debug reasons
   */
  @RequiredElement
  @Attribute("id")
  public String id;

  /**
   * A property key which refers to storage presentable name
   */
  @RequiredElement
  @NonNls
  @Attribute("presentableNameKey")
  public String presentableNameKey;

  /**
   * A bundle name to find presentable name key
   */
  @RequiredElement
  @NonNls
  @Attribute("bundleName")
  public String bundleName;

  /**
   * Version of provided storage
   */
  @RequiredElement
  @NonNls
  @Attribute("version")
  public int version;

  public @NotNull @Nls String getLocalizedPresentableName() {
    ResourceBundle resourceBundle = DynamicBundle.INSTANCE.getResourceBundle(bundleName, myPluginDescriptor.getPluginClassLoader());
    return AbstractBundle.message(resourceBundle, presentableNameKey);
  }

  private FileBasedIndexLayoutProvider myLayoutProvider;
  public @NotNull synchronized FileBasedIndexLayoutProvider getLayoutProvider() {
    if (myLayoutProvider == null) {
      myLayoutProvider = ApplicationManager.getApplication().instantiateClass(providerClass, myPluginDescriptor);
    }
    return myLayoutProvider;
  }

  private volatile PluginDescriptor myPluginDescriptor;
  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }
}
