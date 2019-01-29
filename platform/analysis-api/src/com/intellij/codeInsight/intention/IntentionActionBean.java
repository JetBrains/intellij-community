// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ResourceBundle;

public class IntentionActionBean extends CustomLoadingExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.IntentionActionBean");

  @Tag("className")
  public String className;
  @Tag("category")
  public String category;
  @Tag("categoryKey")
  public String categoryKey;
  @Tag("bundleName")
  public String bundleName;
  @Tag("descriptionDirectoryName")
  public String descriptionDirectoryName;

  @Nullable
  public String[] getCategories() {
    if (categoryKey != null) {
      final String baseName = bundleName != null ? bundleName : ((IdeaPluginDescriptor)myPluginDescriptor).getResourceBundleBaseName();
      if (baseName == null) {
        LOG.error("No resource bundle specified for "+myPluginDescriptor);
      }
      final ResourceBundle bundle = AbstractBundle.getResourceBundle(baseName, myPluginDescriptor.getPluginClassLoader());

      final String[] keys = categoryKey.split("/");
      if (keys.length > 1) {
        return ContainerUtil.map2Array(keys, String.class, s -> CommonBundle.message(bundle, s));
      }

      category = CommonBundle.message(bundle, categoryKey);
    }
    return category == null ? null : category.split("/");
  }

  public String getDescriptionDirectoryName() {
    return descriptionDirectoryName;
  }

  @NotNull
  public IntentionAction instantiate() throws ClassNotFoundException {
    return (IntentionAction)instantiateExtension(className, ApplicationManager.getApplication().getPicoContainer());
  }

  public ClassLoader getMetadataClassLoader() {
    return myPluginDescriptor.getPluginClassLoader();
  }
}
