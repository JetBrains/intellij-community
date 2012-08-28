package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;

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

  public abstract BuildTarget createTarget(@NotNull String targetId);
}
