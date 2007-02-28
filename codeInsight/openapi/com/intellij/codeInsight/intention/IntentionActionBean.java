package com.intellij.codeInsight.intention;

import com.intellij.CommonBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.Locale;
import java.util.ResourceBundle;

public class IntentionActionBean implements PluginAware {

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

  private PluginDescriptor myPluginDescriptor;

  public String[] getCategories() {
    if (categoryKey != null) {
      final String baseName = bundleName != null ? bundleName : ((IdeaPluginDescriptor)myPluginDescriptor).getResourceBundleBaseName();
      final ResourceBundle bundle = ResourceBundle.getBundle(baseName, Locale.getDefault(), myPluginDescriptor.getPluginClassLoader());
      category = CommonBundle.message(bundle, categoryKey);
    }
    return category.split("/");
  }

  public String getDescriptionDirectoryName() {
    return descriptionDirectoryName;
  }

  public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }
}
