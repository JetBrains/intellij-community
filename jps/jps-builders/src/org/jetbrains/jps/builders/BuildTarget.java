package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public abstract class BuildTarget<R extends BuildRootDescriptor> {
  private final BuildTargetType<?> myTargetType;

  protected BuildTarget(BuildTargetType<?> targetType) {
    myTargetType = targetType;
  }

  public abstract String getId();

  public final BuildTargetType<?> getTargetType() {
    return myTargetType;
  }

  public abstract Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry);

  public void writeConfiguration(PrintWriter out, BuildDataPaths dataPaths, BuildRootIndex buildRootIndex) {
  }

  @NotNull
  public abstract List<R> computeRootDescriptors(JpsModel model,
                                                 ModuleExcludeIndex index,
                                                 IgnoredFileIndex ignoredFileIndex,
                                                 BuildDataPaths dataPaths);

  @Nullable
  public abstract BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex);

  @NotNull
  public abstract String getPresentableName();

  @NotNull
  public abstract Collection<File> getOutputDirs(CompileContext context);

  @Override
  public String toString() {
    return getPresentableName();
  }
}
