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

  @Override
  public boolean equals(Object obj) {
    return obj instanceof BuildTargetType && ((BuildTargetType)obj).myTypeId.equals(myTypeId);
  }

  @Override
  public int hashCode() {
    return myTypeId.hashCode();
  }

  @NotNull
  public abstract List<T> computeAllTargets(@NotNull JpsModel model);

  @NotNull
  public abstract BuildTargetLoader<T> createLoader(@NotNull JpsModel model);
}
