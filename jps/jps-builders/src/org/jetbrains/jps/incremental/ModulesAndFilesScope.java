package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.artifacts.Artifact;

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

  private final Set<String> myModules;
  private final Map<String, Set<File>> myFiles;
  private final boolean myForcedCompilation;

  public ModulesAndFilesScope(Project project, Collection<Module> modules, Map<String, Set<File>> files, Set<Artifact> artifacts,
                              boolean isForcedCompilation) {
    super(project, artifacts);
    myFiles = files;
    myForcedCompilation = isForcedCompilation;
    myModules = new HashSet<String>();
    for (Module module : modules) {
      myModules.add(module.getName());
    }
  }

  public boolean isRecompilationForced(@NotNull String moduleName) {
    return myForcedCompilation && myModules.contains(moduleName);
  }

  public boolean isAffected(@NotNull String moduleName) {
    if (myModules.contains(moduleName) || myFiles.containsKey(moduleName)) {
      return true;
    }
    return false;
  }

  public boolean isAffected(String moduleName, @NotNull File file) {
    if (myModules.contains(moduleName)) {
      return true;
    }
    final Set<File> files = myFiles.get(moduleName);
    return files != null && files.contains(file);
  }

}
