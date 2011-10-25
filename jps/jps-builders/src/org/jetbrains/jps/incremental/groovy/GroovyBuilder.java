package org.jetbrains.jps.incremental.groovy;

import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.Builder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/11
 */
public class GroovyBuilder extends Builder {
  public static final String BUILDER_NAME = "groovy";

  public String getName() {
    return BUILDER_NAME;
  }

  public ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    return ExitCode.OK;
  }

  public String getDescription() {
    return "Groovy builder";
  }
}
