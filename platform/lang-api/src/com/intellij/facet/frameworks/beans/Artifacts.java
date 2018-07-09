/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.facet.frameworks.beans;

import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;

public class Artifacts {
  @Property(surroundWithTag = false)
  @XCollection
  public Artifact[] myVersions;

  public Artifact[] getArtifacts() {
    return myVersions;
  }
}