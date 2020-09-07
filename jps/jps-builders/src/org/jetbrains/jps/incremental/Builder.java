/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @see ModuleLevelBuilder
 * @see TargetBuilder
 */
public abstract class Builder {
  @NotNull
  public abstract @Nls(capitalization = Nls.Capitalization.Sentence) String getPresentableName();

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
