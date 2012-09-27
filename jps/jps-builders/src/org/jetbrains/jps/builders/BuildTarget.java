package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.model.JpsModel;

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

  public abstract Collection<BuildTarget<?>> computeDependencies();

  public void writeConfiguration(PrintWriter out, BuildRootIndex buildRootIndex) {
  }

  @NotNull
  public abstract List<R> computeRootDescriptors(JpsModel model, ModuleRootsIndex index);

  @Nullable
  public abstract BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex);

  @NotNull
  public abstract String getPresentableName();

  @Override
  public String toString() {
    return getPresentableName();
  }
}
