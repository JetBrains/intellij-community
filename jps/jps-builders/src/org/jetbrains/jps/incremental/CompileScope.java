package org.jetbrains.jps.incremental;

import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.Project;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class CompileScope {

  private final Project myProject;
  private final Collection<Module> myModules;

  public CompileScope(Project project) {
    this(project, project.getModules().values());
  }

  public CompileScope(Project project, Collection<Module> modules) {
    myProject = project;
    myModules = modules;
  }

  public Collection<Module> getAffectedModules() {
    return Collections.unmodifiableCollection(myModules);
  }

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
