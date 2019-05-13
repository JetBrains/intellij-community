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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;

/**
 * Provides list of files under {@link BuildTarget#computeRootDescriptors source roots} of a target which were modified or deleted since the
 * previous build.
 *
 * @see org.jetbrains.jps.incremental.TargetBuilder#build
 * @see org.jetbrains.jps.incremental.ModuleLevelBuilder#build
 * @author nik
 */
public interface DirtyFilesHolder<R extends BuildRootDescriptor, T extends BuildTarget<R>> {
  void processDirtyFiles(@NotNull FileProcessor<R, T> processor) throws IOException;

  boolean hasDirtyFiles() throws IOException;

  boolean hasRemovedFiles();

  @NotNull
  Collection<String> getRemovedFiles(@NotNull T target);
}
