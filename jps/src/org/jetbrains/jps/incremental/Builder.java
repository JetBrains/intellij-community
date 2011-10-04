package org.jetbrains.jps.incremental;

import org.jetbrains.jps.ModuleChunk;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public abstract class Builder {
  public static enum ExitCode {
    OK, ABORT, ADDITIONAL_PASS_REQUIRED
  }

  public abstract ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException;

  public abstract String getDescription();

  public void cleanupResources() {

  }
}
