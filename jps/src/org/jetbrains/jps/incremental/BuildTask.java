package org.jetbrains.jps.incremental;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public abstract class BuildTask {
  public abstract void build(CompileContext context) throws ProjectBuildException;
}
