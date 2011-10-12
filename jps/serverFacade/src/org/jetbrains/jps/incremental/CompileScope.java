package org.jetbrains.jps.incremental;

import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Project;

import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public abstract class CompileScope {

  private final Project myProject;

  protected CompileScope(Project project) {
    myProject = project;
  }

  public abstract Collection<Module> getAffectedModules();

  public boolean isAffected(ModuleChunk chunk) {
    final Set<Module> modules = chunk.getModules();
    for (Module module : getAffectedModules()) {
      if (modules.contains(module)) {
        return true;
      }
    }
    return false;
  }

  public Project getProject() {
    return myProject;
  }
}
