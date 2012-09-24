package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModel;

import java.util.Collection;

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

  @NotNull
  public abstract Collection<BuildTarget<?>> computeAllTargets(@NotNull JpsModel model);

  @NotNull
  public abstract BuildTargetLoader createLoader(@NotNull JpsModel model);
}
