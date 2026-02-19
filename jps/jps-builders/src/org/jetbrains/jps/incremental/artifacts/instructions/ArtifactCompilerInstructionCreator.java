// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@ApiStatus.Internal
public interface ArtifactCompilerInstructionCreator {
  void addFileCopyInstruction(@NotNull File file, @NotNull String outputFileName);

  void addFileCopyInstruction(@NotNull File file, @NotNull String outputFileName, @NotNull FileCopyingHandler copyingHandler);

  void addDirectoryCopyInstructions(@NotNull File directory);

  void addDirectoryCopyInstructions(@NotNull File directory, @Nullable SourceFileFilter filter);

  void addDirectoryCopyInstructions(@NotNull File directory, @Nullable SourceFileFilter filter, @NotNull FileCopyingHandler copyingHandler);

  /**
   * Add instruction to extract directory from a jar file into the current place in the artifact layout.
   *
   * @param jarFile         jar file to extract
   * @param pathInJar       relative path to directory inside {@code jarFile} which need to be extracted. Use "/" to extract the whole jar contents
   */
  void addExtractDirectoryInstruction(@NotNull File jarFile, @NotNull String pathInJar);

  /**
   * Add instruction to extract directory from a jar file into the current place in the artifact layout.
   * @param jarFile jar file to extract
   * @param pathInJar relative path to directory inside {@code jarFile} which need to be extracted. Use "/" to extract the whole jar contents
   * @param pathInJarFilter a filter instance specifying which entries should be extracted. It should accept paths inside the jar file
 *                        relative to {@code pathInJar} root and return {@code true} if the entry should be extracted and {@code false} otherwise
   */
  void addExtractDirectoryInstruction(@NotNull File jarFile, @NotNull String pathInJar, @NotNull Condition<? super String> pathInJarFilter);

  ArtifactCompilerInstructionCreator subFolder(@NotNull String directoryName);

  ArtifactCompilerInstructionCreator archive(@NotNull String archiveFileName);

  ArtifactCompilerInstructionCreator subFolderByRelativePath(@NotNull String relativeDirectoryPath);

  ArtifactInstructionsBuilder getInstructionsBuilder();

  /**
   * @return target directory for instructions created by this instance or {@code null} if there is no such directory (e.g. because it corresponds
   * to an entry in a JAR file)
   */
  @Nullable
  File getTargetDirectory();
}
