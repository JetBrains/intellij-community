package com.intellij.packaging.elements;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface IncrementalCompilerInstructionCreator {

  void addFileCopyInstruction(@NotNull VirtualFile file, String outputFileName);

  void addDirectoryCopyInstructions(VirtualFile directory);

  IncrementalCompilerInstructionCreator subFolder(String directoryName);

  IncrementalCompilerInstructionCreator archive(String archiveFileName);
}
