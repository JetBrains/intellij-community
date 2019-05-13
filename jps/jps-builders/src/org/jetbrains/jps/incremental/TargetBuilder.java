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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.*;

import java.io.IOException;
import java.util.Collection;

/**
 * Produced the output of a single build target. Use {@link BuilderService} to register implementations of this class.
 *
 * @author nik
 * @see BuilderService#createBuilders()
 */
public abstract class TargetBuilder<R extends BuildRootDescriptor, T extends BuildTarget<R>> extends Builder {
  private final Collection<? extends BuildTargetType<? extends T>> myTargetTypes;

  protected TargetBuilder(Collection<? extends BuildTargetType<? extends T>> targetTypes) {
    myTargetTypes = targetTypes;
  }

  public Collection<? extends BuildTargetType<? extends T>> getTargetTypes() {
    return myTargetTypes;
  }

  /**
   * Builds a single build target.
   *
   * @param target         target to build.
   * @param holder         can be used to enumerate the source files from the inputs of this target that have been modified
   *                       or deleted since the previous compilation run.
   * @param outputConsumer receives the output files produced by the build. (All output files produced by the build need
   *                       to be reported here.)
   * @param context        compilation context (can be used to report compiler errors/warnings and to check whether the build
   *                       has been cancelled and needs to be stopped).
   */
  public abstract void build(@NotNull T target, @NotNull DirtyFilesHolder<R, T> holder, @NotNull BuildOutputConsumer outputConsumer,
                             @NotNull CompileContext context) throws ProjectBuildException, IOException;

}
