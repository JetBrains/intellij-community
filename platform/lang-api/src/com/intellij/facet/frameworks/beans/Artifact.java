/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.facet.frameworks.beans;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

/**
 * @deprecated this class will be removed from open API in IDEA 11. Use {@link com.intellij.util.download.DownloadableFileService} instead
 */
@Tag("artifact")
public class Artifact {
  public static final Artifact[] EMPTY_ARRAY = new Artifact[0];
  
  @Property(surroundWithTag = false)
  @XCollection
  public ArtifactItem[] myItems;

  @Attribute("version")
  public String myVersion;

  @Attribute("name")
  public String myName;

  @Deprecated
  @Attribute("group")
  public String myGroup;

  @Property(surroundWithTag = false)
  public RequiredFrameworkVersion myRequiredFrameworkVersion;

  @Attribute("urlPrefix")
  public String myUrlPrefix;

  public RequiredFrameworkVersion getRequiredFrameworkVersion() {
    return myRequiredFrameworkVersion;
  }

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