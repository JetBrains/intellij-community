package org.jetbrains.jps.incremental.forms;

import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.Builder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/10/11
 */
public class FormsBuilder extends Builder {
  public static final String BUILDER_NAME = "forms";

  public ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {

    return null;
  }

  public String getDescription() {
    return "UI Designer Forms Builder";
  }
}
