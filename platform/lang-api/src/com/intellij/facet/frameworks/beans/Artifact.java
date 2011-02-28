package com.intellij.facet.frameworks.beans;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

@Tag("artifact")
public class Artifact {
  public static final Artifact[] EMPTY_ARRAY = new Artifact[0];
  
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public ArtifactItem[] myItems;

  @Attribute("version")
  public String myVersion;

  @Attribute("name")
  public String myName;

  @Attribute("group")
  public String myGroup;

  @Attribute("urlPrefix")
  public String myUrlPrefix;

  public String getName() {
    return myName;
  }

  public String getGroup() {
    return myGroup;
  }

  public ArtifactItem[] getItems() {
    return myItems;
  }

  public String getVersion() {
    return myVersion;
  }

  public String getUrlPrefix() {
    return myUrlPrefix;
  }

  @Override
  public String toString() {
    return myVersion;
  }
}