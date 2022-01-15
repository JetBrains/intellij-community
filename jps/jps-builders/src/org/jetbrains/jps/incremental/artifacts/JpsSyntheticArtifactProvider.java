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
package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.util.List;

/**
 * Provides artifacts which aren't defined in the project configuration, but can be built as the regular ones.
 * Implementations of this class are registered as Java services, by creating a file META-INF/services/org.jetbrains.jps.incremental.artifacts.JpsSyntheticArtifactProvider
 * containing the qualified name of your implementation class.
 */
public abstract class JpsSyntheticArtifactProvider {
  /**
   * Returns list of additional artifacts which can be built in the project defined by {@code model}. Note that these artifacts are built
   * only if they are included into the build scope (e.g. via {@link com.intellij.compiler.impl.BuildTargetScopeProvider}).
   */
  @NotNull
  public abstract List<JpsArtifact> createArtifacts(@NotNull JpsModel model);
}
