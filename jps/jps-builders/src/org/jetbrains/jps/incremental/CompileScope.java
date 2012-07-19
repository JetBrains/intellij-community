package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;

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
  private final Set<Artifact> myArtifacts;

  protected CompileScope(@NotNull Project project, JpsProject jpsProject, Set<Artifact> artifacts) {
    myProject = project;
    myJpsProject = jpsProject;
    myArtifacts = artifacts;
  }

  public boolean isAffected(Artifact artifact) {
    return myArtifacts.contains(artifact);
  }

  public abstract boolean isAffected(String moduleName, @NotNull File file);

  public abstract boolean isAffected(@NotNull String moduleName);

  public abstract boolean isRecompilationForced(@NotNull String moduleName);

  public final boolean isAffected(ModuleChunk chunk) {
    for (JpsModule module : chunk.getModules()) {
      if (isAffected(module.getName())) {
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

  public JpsProject getJpsProject() {
    return myJpsProject;
  }
}
