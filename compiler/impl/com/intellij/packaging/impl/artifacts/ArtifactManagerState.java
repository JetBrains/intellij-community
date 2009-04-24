package com.intellij.packaging.impl.artifacts;

import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.AbstractCollection;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class ArtifactManagerState {
  private List<ArtifactState> myArtifacts = new ArrayList<ArtifactState>();

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public List<ArtifactState> getArtifacts() {
    return myArtifacts;
  }

  public void setArtifacts(List<ArtifactState> artifacts) {
    myArtifacts = artifacts;
  }
}
