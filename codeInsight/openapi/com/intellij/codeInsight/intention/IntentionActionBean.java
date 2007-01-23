package com.intellij.codeInsight.intention;

import com.thoughtworks.xstream.annotations.XStreamImplicitCollection;

import java.util.List;

@XStreamImplicitCollection(value="categories",item="category")
public class IntentionActionBean {
  private String className;
  private List<String> categories;
  private String descriptionDirectoryName;


  public String getClassName() {
    return className;
  }

  public String[] getCategories() {
    return categories.toArray(new String[categories.size()]);
  }

  public String getDescriptionDirectoryName() {
    return descriptionDirectoryName;
  }
}
