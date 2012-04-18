package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.artifacts.Artifact;

import java.io.File;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/15/12
 */
public abstract class CompileScope {
  @NotNull
  private final Project myProject;
  private final Set<Artifact> myArtifacts;

  protected CompileScope(@NotNull Project project, Set<Artifact> artifacts) {
    myProject = project;
    myArtifacts = artifacts;
  }

  public boolean isAffected(Artifact artifact) {
    return myArtifacts.contains(artifact);
  }

  public abstract boolean isAffected(Module module, @NotNull File file);

  public abstract boolean isAffected(@NotNull Module module);

  public abstract boolean isRecompilationForced(@NotNull Module module);

  public final boolean isAffected(ModuleChunk chunk) {
    for (Module module : chunk.getModules()) {
      if (isAffected(module)) {
        return true;
      }
    }
    return false;
  }

  public Set<Artifact> getArtifacts() {
    return myArtifacts;
  }

  @NotNull
  public final Project getProject() {
    return myProject;
  }
}
