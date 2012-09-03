package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
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
  private final Set<BuildTarget> myTargets;
  private final Map<BuildTarget, Set<File>> myFiles;

  public ModulesAndFilesScope(Project project, JpsProject jpsProject, Collection<JpsModule> targets, Map<BuildTarget, Set<File>> files,
                              Set<JpsArtifact> artifacts, boolean isForcedCompilation) {
    super(project, jpsProject, artifacts, isForcedCompilation, true);
    myFiles = files;
    myTargets = new HashSet<BuildTarget>();
    for (JpsModule module : targets) {
      myTargets.add(new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
      myTargets.add(new ModuleBuildTarget(module, JavaModuleBuildTargetType.TEST));
    }
  }

  public boolean isRecompilationForced(@NotNull BuildTarget target) {
    return myForcedCompilation && myTargets.contains(target);
  }

  public boolean isAffected(@NotNull BuildTarget target) {
    if (myTargets.contains(target) || myFiles.containsKey(target)) {
      return true;
    }
    return false;
  }

  public boolean isAffected(BuildTarget target, @NotNull File file) {
    if (myTargets.contains(target)) {
      return true;
    }
    final Set<File> files = myFiles.get(target);
    return files != null && files.contains(file);
  }

}
