/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.model.serialization.artifact;

import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
@Tag("artifacts")
public class ArtifactManagerState {
  private List<ArtifactState> myArtifacts = new ArrayList<>();

  @Property(surroundWithTag = false)
  @XCollection
  public List<ArtifactState> getArtifacts() {
    return myArtifacts;
  }

  public void setArtifacts(List<ArtifactState> artifacts) {
    myArtifacts = artifacts;
  }
}
