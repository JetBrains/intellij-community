package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
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
  private final Set<BuildTarget> myTargets;

  public ModulesScope(Project project,
                      JpsProject jpsProject,
                      Set<JpsModule> modules,
                      Set<JpsArtifact> artifacts,
                      boolean isForcedCompilation,
                      boolean includeTests) {
    super(project, jpsProject, artifacts, isForcedCompilation);
    myTargets = new HashSet<BuildTarget>();
    for (JpsModule module : modules) {
      myTargets.add(new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
      if (includeTests) {
        myTargets.add(new ModuleBuildTarget(module, JavaModuleBuildTargetType.TEST));
      }
    }
  }

  public boolean isRecompilationForced(@NotNull BuildTarget target) {
    return myForcedCompilation && isAffected(target);
  }

  public boolean isAffected(@NotNull BuildTarget target) {
    return myTargets.contains(target);
  }

  public boolean isAffected(BuildTarget target, @NotNull File file) {
    return true; // for speed reasons
  }

}
