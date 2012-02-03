package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.artifacts.Artifact;

import java.io.File;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class AllProjectScope extends CompileScope {

  private final boolean myIsForcedCompilation;

  public AllProjectScope(Project project, Set<Artifact> artifacts, boolean forcedCompilation) {
    super(project, artifacts);
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
