package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.io.File;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/15/12
 */
public abstract class CompileScope {
  @NotNull
  private final Project myProject;
  private final JpsProject myJpsProject;
  private final Set<JpsArtifact> myArtifacts;
  protected final boolean myForcedCompilation;

  protected CompileScope(@NotNull Project project,
                         JpsProject jpsProject,
                         Set<JpsArtifact> artifacts,
                         boolean forcedCompilation) {
    myProject = project;
    myJpsProject = jpsProject;
    myArtifacts = artifacts;
    myForcedCompilation = forcedCompilation;
  }

  public boolean isAffected(JpsArtifact artifact) {
    return myArtifacts.contains(artifact);
  }

  public boolean isRecompilationForced(JpsArtifact artifact) {
    return myForcedCompilation && myArtifacts.contains(artifact);
  }

  public abstract boolean isAffected(BuildTarget target, @NotNull File file);

  public abstract boolean isAffected(@NotNull BuildTarget target);

  public abstract boolean isRecompilationForced(@NotNull BuildTarget target);

  public final boolean isAffected(ModuleChunk chunk) {
    for (ModuleBuildTarget target : chunk.getTargets()) {
      if (isAffected(target)) {
        return true;
      }
    }
    return false;
  }

  public Set<JpsArtifact> getArtifacts() {
    return myArtifacts;
  }

  @NotNull
  public final Project getProject() {
    return myProject;
  }

  public JpsProject getJpsProject() {
    return myJpsProject;
  }
}
