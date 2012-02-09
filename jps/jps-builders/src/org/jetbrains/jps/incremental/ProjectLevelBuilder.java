package org.jetbrains.jps.incremental;

/**
 * Use {@link BuilderService} to register implementations of this class
 * @author nik
 */
public abstract class ProjectLevelBuilder extends Builder {
  protected ProjectLevelBuilder() {
  }

  public abstract void build(CompileContext context) throws ProjectBuildException;

}
