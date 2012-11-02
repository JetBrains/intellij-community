package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;

/**
 * @see ModuleLevelBuilder
 * @see TargetBuilder
 *
 * @author nik
 */
public abstract class Builder {
  @NotNull
  public abstract String getPresentableName();

  public void buildStarted(CompileContext context) {
  }

  public void buildFinished(CompileContext context) {
  }
}
