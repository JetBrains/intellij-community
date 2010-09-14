package com.intellij.facet.frameworks.beans;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

@Tag("version")
public class Version {
  public static final Version[] EMPTY_ARRAY = new Version[0];
  
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public DownloadJar[] myJars;

  @Attribute("id")
  public String myId;

  @Attribute("ri")
  public String myRI; // optional attribute "reference implementation"

  public String getRI() {
    return myRI;
  }

  public DownloadJar[] getJars() {
    return myJars;
  }

  public String getId() {
    return myId;
  }

  @Override
  public String toString() {
    return myId;
  }
}