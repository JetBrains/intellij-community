package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/15/12
 */
public abstract class CompileScope {
  protected final boolean myForcedCompilation;

  protected CompileScope(boolean forcedCompilation) {
    myForcedCompilation = forcedCompilation;
  }

  public abstract boolean isAffected(BuildTarget target, @NotNull File file);

  public abstract boolean isAffected(@NotNull BuildTarget target);

  public abstract boolean isRecompilationForced(@NotNull BuildTarget target);
}
