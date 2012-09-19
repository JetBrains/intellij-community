package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class CompileScopeImpl extends CompileScope {
  private final Collection<? extends BuildTargetType> myTypes;
  private final Collection<BuildTarget> myTargets;
  private final Map<BuildTarget, Set<File>> myFiles;

  public CompileScopeImpl(boolean forcedCompilation,
                          Collection<? extends BuildTargetType> types, Collection<BuildTarget> targets, Map<BuildTarget, Set<File>> files) {
    super(forcedCompilation);
    myTypes = types;
    myTargets = targets;
    myFiles = files;
  }

  @Override
  public boolean isAffected(@NotNull BuildTarget target) {
    return myTypes.contains(target.getTargetType()) || myTargets.contains(target) || myFiles.containsKey(target);
  }

  @Override
  public boolean isRecompilationForced(@NotNull BuildTarget target) {
    return myForcedCompilation && (myTypes.contains(target.getTargetType()) || myTargets.contains(target));
  }

  @Override
  public boolean isAffected(BuildTarget target, @NotNull File file) {
    if (myFiles.isEmpty()) {//optimization
      return true;
    }
    if (myTypes.contains(target.getTargetType()) || myTargets.contains(target)) {
      return true;
    }
    Set<File> files = myFiles.get(target);
    return files != null && files.contains(file);
  }
}
