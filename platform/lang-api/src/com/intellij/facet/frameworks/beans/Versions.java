package com.intellij.facet.frameworks.beans;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;

public class Versions {

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public Version[] myVersions;

  public Version[] getVersions() {
    return myVersions;
  }
}