package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ProjectLevelBuilderService {
  @NotNull
  public abstract ProjectLevelBuilder createBuilder();
}
