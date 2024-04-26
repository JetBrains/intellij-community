// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @see ModuleLevelBuilder
 * @see TargetBuilder
 */
public abstract class Builder {
  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getPresentableName();

  public void buildStarted(CompileContext context) {
  }

  public void buildFinished(CompileContext context) {
  }

  /**
   * Returns time required for processing a single {@link org.jetbrains.jps.builders.BuildTarget} by this builder. This information is used
   * during the first build to estimate build time for targets of different types to provide information about build progress. Subsequent
   * builds will use real build times remembered from previous builds so this method won't be used.
   * <p/>
   * The returned value is not absolute, the values returned by different {@link Builder}s are used only to compare estimated build time for
   * different {@link org.jetbrains.jps.builders.BuildTargetType}. The default value {@code 10} is used for simple builders (like 'Resources Builder'
   * which just copy resource files to the output).
   */
  public long getExpectedBuildTime() {
    return 10;
  }
}
