package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public class ModulesAndFilesScope extends CompileScope {

  private final Set<Module> myModules;
  private final Map<Module, Set<File>> myFiles;
  private final boolean myForcedCompilation;

  public ModulesAndFilesScope(Project project, Collection<Module> modules, Map<Module, Set<File>> files, boolean isForcedCompilation) {
    super(project);
    myFiles = files;
    myForcedCompilation = isForcedCompilation;
    myModules = new HashSet<Module>(modules);
  }

  public boolean isRecompilationForced(@NotNull Module module) {
    return myForcedCompilation && myModules.contains(module);
  }

  public boolean isAffected(@NotNull Module module) {
    if (myModules.contains(module) || myFiles.containsKey(module)) {
      return true;
    }
    return false;
  }

  public boolean isAffected(Module module, @NotNull File file) {
    if (myModules.contains(module)) {
      return true;
    }
    final Set<File> files = myFiles.get(module);
    return files != null && files.contains(file);
  }

}
