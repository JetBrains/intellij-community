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
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author nik
 */
public interface ArtifactCompilerInstructionCreator {

  void addFileCopyInstruction(@NotNull File file, @NotNull String outputFileName);

  void addDirectoryCopyInstructions(@NotNull File directory);

  void addDirectoryCopyInstructions(@NotNull File directory, @Nullable SourceFileFilter filter);

  void addExtractDirectoryInstruction(@NotNull File jarFile, @NotNull String pathInJar);

  ArtifactCompilerInstructionCreator subFolder(@NotNull String directoryName);

  ArtifactCompilerInstructionCreator archive(@NotNull String archiveFileName);

  ArtifactCompilerInstructionCreator subFolderByRelativePath(@NotNull String relativeDirectoryPath);
}
