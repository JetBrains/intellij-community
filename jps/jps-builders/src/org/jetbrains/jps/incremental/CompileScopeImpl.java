package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author nik
 */
public class CompileScopeImpl extends CompileScope {
  protected final boolean myForcedCompilation;
  private final Collection<? extends BuildTargetType<?>> myTypes;
  private final Collection<BuildTarget<?>> myTargets;
  private final Map<BuildTarget<?>, Set<File>> myFiles;
  private final Map<BuildTarget<?>, Set<File>> myAppendedFiles = Collections.synchronizedMap(new THashMap<BuildTarget<?>, Set<File>>());

  public CompileScopeImpl(boolean forcedCompilation, Collection<? extends BuildTargetType<?>> types, Collection<BuildTarget<?>> targets,
                          Map<BuildTarget<?>, Set<File>> files) {
    myForcedCompilation = forcedCompilation;
    myTypes = types;
    myTargets = targets;
    myFiles = files;
  }

  @Override
  public boolean isAffected(@NotNull BuildTarget<?> target) {
    return myTypes.contains(target.getTargetType()) || myTargets.contains(target) || myFiles.containsKey(target) || isAffectedByAssociatedModule(target);
  }

  @Override
  public boolean isRecompilationForced(@NotNull BuildTarget<?> target) {
    return myForcedCompilation && (myTypes.contains(target.getTargetType()) || myTargets.contains(target) || isAffectedByAssociatedModule(target));
  }

  @Override
  public boolean isAffected(BuildTarget<?> target, @NotNull File file) {
    if (myFiles.isEmpty() && myAppendedFiles.isEmpty()) {//optimization
      return true;
    }
    if (myTypes.contains(target.getTargetType()) || myTargets.contains(target) || isAffectedByAssociatedModule(target)) {
      return true;
    }
    final Set<File> files = myFiles.get(target);
    if (files != null && files.contains(file)) {
      return true;
    }
    synchronized (myAppendedFiles) {
      final Set<File> appended = myAppendedFiles.get(target);
      return appended != null && appended.contains(file);
    }
  }

  @Override
  public void expandScope(BuildTarget<?> target, @NotNull File file) {
    synchronized (myAppendedFiles) {
      Set<File> files = myAppendedFiles.get(target);
      if (files == null) {
        files = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
        myAppendedFiles.put(target, files);
      }
      files.add(file);
    }
  }

  private boolean isAffectedByAssociatedModule(BuildTarget<?> target) {
    final JpsModule module = target instanceof ModuleBasedTarget ? ((ModuleBasedTarget)target).getModule() : null;
    if (module != null) {
      // this target is associated with module
      for (JavaModuleBuildTargetType moduleType : JavaModuleBuildTargetType.ALL_TYPES) {
        if (myTypes.contains(moduleType) || myTargets.contains(new ModuleBuildTarget(module, moduleType))) {
          return true;
        }
      }
    }
    return false;
  }

}
