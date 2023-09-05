// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
  @RequiredElement @Attribute("presentableNameKey") public @NonNls String presentableNameKey;

  /**
   * A bundle name to find presentable name key
   */
  @Attribute("bundleName") public @NonNls String bundleName;

  /**
   * Version of provided storage
   */
  @RequiredElement @Attribute("version") public @NonNls int version;

  @SuppressWarnings("HardCodedStringLiteral")
  public @NotNull @Nls String getLocalizedPresentableName() {
    String resourceBundleBaseName = bundleName != null ? bundleName : myPluginDescriptor.getResourceBundleBaseName();
    if (resourceBundleBaseName == null) {
      Logger.getInstance(FileBasedIndexLayoutProviderBean.class).error("Can't find resource bundle name for " + myPluginDescriptor.getName());
      return "!" + presentableNameKey + "!";
    }
    ResourceBundle resourceBundle = DynamicBundle.getResourceBundle(myPluginDescriptor.getClassLoader(), resourceBundleBaseName);
    return AbstractBundle.message(resourceBundle, presentableNameKey);
  }

  private FileBasedIndexLayoutProvider myLayoutProvider;
  public synchronized @NotNull FileBasedIndexLayoutProvider getLayoutProvider() {
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
