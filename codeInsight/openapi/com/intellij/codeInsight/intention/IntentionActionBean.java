package com.intellij.codeInsight.intention;

import com.intellij.CommonBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;

import java.util.Locale;
import java.util.ResourceBundle;

public class IntentionActionBean implements PluginAware {
  private String className;
  private String category;
  private String categoryKey;
  private String bundleName;
  private String descriptionDirectoryName;
  private PluginDescriptor myPluginDescriptor;

  public String getClassName() {
    return className;
  }

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
