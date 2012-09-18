package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.ArtifactRootsIndex;

/**
 * @author nik
 */
public abstract class BuildTargetType {
  private final String myTypeId;

  protected BuildTargetType(String typeId) {
    myTypeId = typeId;
  }

  public String getTypeId() {
    return myTypeId;
  }

  @Nullable
  public abstract BuildTarget createTarget(@NotNull String targetId,
                                           @NotNull ModuleRootsIndex rootsIndex,
                                           ArtifactRootsIndex artifactRootsIndex);
}
