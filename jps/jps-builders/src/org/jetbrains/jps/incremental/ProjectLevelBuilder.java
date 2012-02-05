package org.jetbrains.jps.incremental;

/**
 * @author nik
 */
public abstract class ProjectLevelBuilder extends Builder {
  protected ProjectLevelBuilder() {
  }

  public abstract void build(CompileContext context) throws ProjectBuildException;

}
