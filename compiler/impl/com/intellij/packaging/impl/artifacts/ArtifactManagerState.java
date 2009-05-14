package com.intellij.packaging.impl.artifacts;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
@Tag("artifacts")
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
