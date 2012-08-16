package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.module.JpsModule;

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

  public ModulesAndFilesScope(Project project, JpsProject jpsProject, Collection<JpsModule> modules, Map<String, Set<File>> files,
                              Set<JpsArtifact> artifacts, boolean isForcedCompilation) {
    super(project, jpsProject, artifacts, isForcedCompilation, true);
    myFiles = files;
    myModules = new HashSet<String>();
    for (JpsModule module : modules) {
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
