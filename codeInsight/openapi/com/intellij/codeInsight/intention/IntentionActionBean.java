package com.intellij.codeInsight.intention;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.thoughtworks.xstream.annotations.XStreamImplicitCollection;

import java.util.List;

@XStreamImplicitCollection(value="categories",item="category")
public class IntentionActionBean implements PluginAware {
  private String className;
  private List<String> categories;
  private String descriptionDirectoryName;
  private PluginDescriptor myPluginDescriptor;


  public String getClassName() {
    return className;
  }

  public String[] getCategories() {
    return categories.toArray(new String[categories.size()]);
  }

  public String getDescriptionDirectoryName() {
    return descriptionDirectoryName;
  }

  public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }
}
