package org.jetbrains.jps.incremental;

/**
 * @see ProjectLevelBuilderService
 * @author nik
 */
public abstract class ProjectLevelBuilder extends Builder {
  protected ProjectLevelBuilder() {
  }

  public abstract void build(CompileContext context) throws ProjectBuildException;

}
