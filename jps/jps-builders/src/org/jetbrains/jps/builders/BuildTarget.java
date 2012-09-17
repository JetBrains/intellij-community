package org.jetbrains.jps.builders;

import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.ArtifactRootsIndex;

import java.io.PrintWriter;
import java.util.Collection;

/**
 * @author nik
 */
public abstract class BuildTarget {
  private final BuildTargetType myTargetType;

  protected BuildTarget(BuildTargetType targetType) {
    myTargetType = targetType;
  }

  public abstract String getId();

  public final BuildTargetType getTargetType() {
    return myTargetType;
  }

  public abstract Collection<? extends BuildTarget> computeDependencies();

  public void writeConfiguration(PrintWriter out, ModuleRootsIndex index, ArtifactRootsIndex rootsIndex) {
  }

  public abstract BuildRootDescriptor findRootDescriptor(String rootId, ModuleRootsIndex index, ArtifactRootsIndex artifactRootsIndex);

  @Override
  public String toString() {
    return myTargetType.getTypeId() + " '" + getId() + "'";
  }
}
