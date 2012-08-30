package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.io.File;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class AllProjectScope extends CompileScope {
  public AllProjectScope(Project project, JpsProject jpsProject, Set<JpsArtifact> artifacts, boolean forcedCompilation) {
    this(project, jpsProject, artifacts, forcedCompilation, true);
  }

  public AllProjectScope(Project project, JpsProject jpsProject, Set<JpsArtifact> artifacts, boolean forcedCompilation,
                         final boolean includeTests) {
    super(project, jpsProject, artifacts, forcedCompilation, includeTests);
  }

  public boolean isRecompilationForced(@NotNull BuildTarget target) {
    return myForcedCompilation;
  }

  public boolean isAffected(@NotNull BuildTarget target) {
    return true;
  }

  public boolean isAffected(BuildTarget target, @NotNull File file) {
    return true;
  }

}
