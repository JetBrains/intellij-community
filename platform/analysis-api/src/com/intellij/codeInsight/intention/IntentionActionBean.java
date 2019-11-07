// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.ResourceBundle;

public final class IntentionActionBean extends CustomLoadingExtensionPointBean<IntentionAction> {
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
  @Override
  protected String getImplementationClassName() {
    return className;
  }

  @Nullable
  public String[] getCategories() {
    if (categoryKey != null) {
      final String baseName = bundleName != null ? bundleName : getPluginDescriptor().getResourceBundleBaseName();
      if (baseName == null) {
        LOG.error("No resource bundle specified for " + getPluginDescriptor());
        return null;
      }

      final ResourceBundle bundle = AbstractBundle.getResourceBundle(baseName, getLoaderForClass());

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
}
