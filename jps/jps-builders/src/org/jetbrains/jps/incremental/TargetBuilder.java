package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;

import java.util.Collection;

/**
 * Use {@link BuilderService} to register implementations of this class
 * @author nik
 */
public abstract class TargetBuilder<B extends BuildTarget<?>> extends Builder {
  private final Collection<? extends BuildTargetType<? extends B>> myTargetTypes;

  protected TargetBuilder(Collection<? extends BuildTargetType<? extends B>> targetTypes) {
    myTargetTypes = targetTypes;
  }

  public Collection<? extends BuildTargetType<? extends B>> getTargetTypes() {
    return myTargetTypes;
  }

  public abstract void build(@NotNull B target, @NotNull CompileContext context) throws ProjectBuildException;

}
