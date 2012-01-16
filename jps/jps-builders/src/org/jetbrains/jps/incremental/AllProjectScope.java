package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class AllProjectScope extends CompileScope {

  private final boolean myIsForcedCompilation;

  public AllProjectScope(Project project, boolean forcedCompilation) {
    super(project);
    myIsForcedCompilation = forcedCompilation;
  }

  public boolean isRecompilationForced(@NotNull Module module) {
    return myIsForcedCompilation;
  }

  public boolean isAffected(@NotNull Module module) {
    return true;
  }

  public boolean isAffected(Module module, @NotNull File file) {
    return true;
  }

}
