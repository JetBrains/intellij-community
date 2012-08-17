package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class ModulesScope extends CompileScope {
  private final Set<String> myModules;

  public ModulesScope(Project project,
                      JpsProject jpsProject,
                      Set<JpsModule> modules,
                      Set<JpsArtifact> artifacts,
                      boolean isForcedCompilation,
                      boolean includeTests) {
    super(project, jpsProject, artifacts, isForcedCompilation, includeTests);
    myModules = new HashSet<String>();
    for (JpsModule module : modules) {
      myModules.add(module.getName());
    }
  }

  public boolean isRecompilationForced(@NotNull String moduleName) {
    return myForcedCompilation &&
           isAffected(moduleName);
  }

  public boolean isAffected(@NotNull String moduleName) {
    return myModules.contains(moduleName);
  }

  public boolean isAffected(String moduleName, @NotNull File file) {
    return true; // for speed reasons
  }

}
