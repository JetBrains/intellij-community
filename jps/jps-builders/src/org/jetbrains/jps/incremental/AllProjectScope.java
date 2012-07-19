package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.model.JpsProject;

import java.io.File;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class AllProjectScope extends CompileScope {

  private final boolean myIsForcedCompilation;

  public AllProjectScope(Project project, JpsProject jpsProject, Set<Artifact> artifacts, boolean forcedCompilation) {
    super(project, jpsProject, artifacts);
    myIsForcedCompilation = forcedCompilation;
  }

  public boolean isRecompilationForced(@NotNull String moduleName) {
    return myIsForcedCompilation;
  }

  public boolean isAffected(@NotNull String moduleName) {
    return true;
  }

  public boolean isAffected(String moduleName, @NotNull File file) {
    return true;
  }

}
