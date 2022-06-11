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
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.incremental.CompileContext;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface BuildTargetIndex extends BuildTargetRegistry {

  List<BuildTargetChunk> getSortedTargetChunks(@NotNull CompileContext context);

  /**
   * Returns {@code true} if target is {@link BuildTargetType#isFileBased() file-based} and has no source roots so it may be skipped during build.
   */
  boolean isDummy(@NotNull BuildTarget<?> target);

  /**
   * @deprecated use {@link #getDependencies(BuildTarget, CompileContext)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  Set<BuildTarget<?>> getDependenciesRecursively(@NotNull BuildTarget<?> target, @NotNull CompileContext context);

  @NotNull
  Collection<BuildTarget<?>> getDependencies(@NotNull BuildTarget<?> target, @NotNull CompileContext context);
}
