package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Project;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/15/12
 */
public abstract class CompileScope {

  @NotNull
  private final Project myProject;

  protected CompileScope(@NotNull Project project) {
    myProject = project;
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

  @NotNull
  public final Project getProject() {
    return myProject;
  }
}
