package com.intellij.packaging.elements;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author nik
 */
public interface IncrementalCompilerInstructionCreator {

  void addFileCopyInstruction(@NotNull VirtualFile file);

  void addDirectoryCopyInstructions(VirtualFile directory);

  IncrementalCompilerInstructionCreator subFolder(String directoryName);

  IncrementalCompilerInstructionCreator archive(String archiveFileName);
}
