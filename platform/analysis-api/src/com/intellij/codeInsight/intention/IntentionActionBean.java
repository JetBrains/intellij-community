// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.ResourceBundle;

public final class IntentionActionBean extends CustomLoadingExtensionPointBean<IntentionAction> {
  @Tag
  @RequiredElement
  public String className;

  @Tag
  public String language;

  @Tag public @Nls(capitalization = Nls.Capitalization.Sentence) String category;

  @Tag public @Nls(capitalization = Nls.Capitalization.Sentence) String categoryKey;

  @Tag
  public String bundleName;

  @Tag
  public String descriptionDirectoryName;

  @Override
  protected @Nullable String getImplementationClassName() {
    return className;
  }

  public String @Nullable [] getCategories() {
    if (categoryKey == null) {
      return category == null ? null : category.split("/");
    }

    String baseName = bundleName != null ? bundleName : getPluginDescriptor().getResourceBundleBaseName();
    if (baseName == null) {
      Logger.getInstance(IntentionActionBean.class).error("No resource bundle specified for " + getPluginDescriptor());
      return null;
    }

    ResourceBundle bundle = DynamicBundle.getResourceBundle(getLoaderForClass(), baseName);
    String[] keys = categoryKey.split("/");
    if (keys.length > 1) {
      String[] result = new String[keys.length];
      for (int i = 0; i < keys.length; i++) {
        result[i] = AbstractBundle.message(bundle, keys[i]);
      }
      return result;
    }

    return AbstractBundle.message(bundle, categoryKey).split("/");
  }

  public String getDescriptionDirectoryName() {
    return descriptionDirectoryName;
  }
}
