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

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Use methods of this interface to register files produced by a builder in the build system. This will allow other builders to process
 * generated files and also update the source-to-output mapping. The build system deletes output files corresponding to changed or deleted
 * source files before the next build starts. Also all output files registered in the mapping are cleared on forced recompilation (rebuild).
 *
 * @author nik
 */
public interface BuildOutputConsumer {
  /**
   * Notifies the build system that {@code outputFile} was produced from {@code sourcePaths}.
   */
  void registerOutputFile(@NotNull File outputFile, @NotNull Collection<String> sourcePaths) throws IOException;

  /**
   * Notifies the build system that the entire contents of {@code outputDir} was produced from {@code sourcePaths}. Note that
   * if one of {@code sourcePaths} changes after the build is finished the {@code outputDir} will be deleted completely before
   * the next build starts so don't use this method if {@code outputDir} contains source files or files produced by other builders.
   */
  void registerOutputDirectory(@NotNull File outputDir, @NotNull Collection<String> sourcePaths) throws IOException;
}
