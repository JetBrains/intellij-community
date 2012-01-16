package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;

import java.io.File;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class ModulesScope extends CompileScope {

  private final Set<Module> myModules;
  private final boolean myForcedCompilation;

  public ModulesScope(Project project, Set<Module> modules, boolean isForcedCompilation) {
    super(project);
    myModules = modules;
    myForcedCompilation = isForcedCompilation;
  }

  public boolean isRecompilationForced(@NotNull Module module) {
    return myForcedCompilation && isAffected(module);
  }

  public boolean isAffected(@NotNull Module module) {
    return myModules.contains(module);
  }

  public boolean isAffected(Module module, @NotNull File file) {
    return true; // for speed reasons
  }

}
