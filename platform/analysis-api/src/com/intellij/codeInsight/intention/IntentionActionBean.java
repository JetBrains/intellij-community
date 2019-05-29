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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ResourceBundle;

public final class IntentionActionBean extends CustomLoadingExtensionPointBean {
  private static final Logger LOG = Logger.getInstance(IntentionActionBean.class);

  @Tag
  public String className;

  @Tag
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public String category;

  @Tag
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public String categoryKey;

  @Tag
  public String bundleName;

  @Tag
  public String descriptionDirectoryName;

  @Nullable
  public String[] getCategories() {
    if (categoryKey != null) {
      final String baseName = bundleName != null ? bundleName : ((IdeaPluginDescriptor)myPluginDescriptor).getResourceBundleBaseName();
      if (baseName == null) {
        LOG.error("No resource bundle specified for "+myPluginDescriptor);
        return null;
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
  public IntentionAction instantiate() {
    return instantiateExtension(className, ApplicationManager.getApplication().getPicoContainer());
  }

  public ClassLoader getMetadataClassLoader() {
    return myPluginDescriptor.getPluginClassLoader();
  }
}
