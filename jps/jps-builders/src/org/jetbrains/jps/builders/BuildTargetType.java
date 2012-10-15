package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModel;

import java.util.List;

/**
 * @author nik
 */
public abstract class BuildTargetType<T extends BuildTarget<?>> {
  private final String myTypeId;

  protected BuildTargetType(String typeId) {
    myTypeId = typeId;
  }

  public final String getTypeId() {
    return myTypeId;
  }

  @NotNull
  public abstract List<T> computeAllTargets(@NotNull JpsModel model);

  @NotNull
  public abstract BuildTargetLoader<T> createLoader(@NotNull JpsModel model);
}
