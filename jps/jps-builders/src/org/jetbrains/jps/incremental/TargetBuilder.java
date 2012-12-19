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

  public abstract void build(@NotNull T target, @NotNull DirtyFilesHolder<R, T> holder, @NotNull BuildOutputConsumer outputConsumer,
                             @NotNull CompileContext context) throws ProjectBuildException, IOException;

}
