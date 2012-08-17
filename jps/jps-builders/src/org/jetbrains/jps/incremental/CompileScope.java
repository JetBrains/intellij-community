package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.JpsArtifact;
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
  private final Set<JpsArtifact> myArtifacts;
  protected final boolean myForcedCompilation;
  private boolean myIncludeTests;

  protected CompileScope(@NotNull Project project,
                         JpsProject jpsProject,
                         Set<JpsArtifact> artifacts,
                         boolean forcedCompilation,
                         boolean tests) {
    myProject = project;
    myJpsProject = jpsProject;
    myArtifacts = artifacts;
    myForcedCompilation = forcedCompilation;
    myIncludeTests = tests;
  }

  public boolean isAffected(JpsArtifact artifact) {
    return myArtifacts.contains(artifact);
  }

  public boolean isIncludeTests() {
    return myIncludeTests;
  }

  public boolean isRecompilationForced(JpsArtifact artifact) {
    return myForcedCompilation && myArtifacts.contains(artifact);
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
