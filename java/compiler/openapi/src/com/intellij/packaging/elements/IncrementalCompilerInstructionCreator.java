package com.intellij.packaging.elements;

import com.intellij.openapi.compiler.make.PackagingFileFilter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface IncrementalCompilerInstructionCreator {

  void addFileCopyInstruction(@NotNull VirtualFile file, @NotNull String outputFileName);

  void addDirectoryCopyInstructions(@NotNull VirtualFile directory);

  void addDirectoryCopyInstructions(@NotNull VirtualFile directory, @Nullable PackagingFileFilter filter);

  IncrementalCompilerInstructionCreator subFolder(@NotNull String directoryName);

  IncrementalCompilerInstructionCreator archive(@NotNull String archiveFileName);
}
