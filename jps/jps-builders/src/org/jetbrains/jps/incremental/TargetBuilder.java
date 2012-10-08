package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.DirtyFilesHolder;

import java.io.IOException;
import java.util.Collection;

/**
 * Use {@link BuilderService} to register implementations of this class
 * @author nik
 */
public abstract class TargetBuilder<R extends BuildRootDescriptor, T extends BuildTarget<R>> extends Builder {
  private final Collection<? extends BuildTargetType<? extends T>> myTargetTypes;

  protected TargetBuilder(Collection<? extends BuildTargetType<? extends T>> targetTypes) {
    myTargetTypes = targetTypes;
  }

  public Collection<? extends BuildTargetType<? extends T>> getTargetTypes() {
    return myTargetTypes;
  }

  public abstract void build(@NotNull T target, @NotNull CompileContext context, DirtyFilesHolder<R, T> holder)
    throws ProjectBuildException, IOException;

}
