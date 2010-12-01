package com.intellij.facet.frameworks.beans;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;

public class Artifacts {

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public Artifact[] myVersions;

  public Artifact[] getArtifacts() {
    return myVersions;
  }
}