package org.jetbrains.jps.builders.impl;

import org.jetbrains.jps.builders.BuildTarget;

import java.util.Set;

/**
 * @author nik
 */
public class BuildTargetChunk {
  private Set<BuildTarget<?>> myTargets;

  public BuildTargetChunk(Set<BuildTarget<?>> targets) {
    myTargets = targets;
  }

  public Set<BuildTarget<?>> getTargets() {
    return myTargets;
  }

  @Override
  public String toString() {
    return myTargets.toString();
  }
}
