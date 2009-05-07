package com.intellij.packaging.elements;

import com.intellij.compiler.ant.Tag;
import com.intellij.compiler.ant.Generator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface CopyInstructionCreator {

  @NotNull
  Tag createDirectoryContentCopyInstruction(@NotNull String dirPath);

  @NotNull
  Tag createFileCopyInstruction(@NotNull String filePath, String outputFileName);

  @NotNull
  CopyInstructionCreator subFolder(String directoryName);

  @Nullable
  Generator createSubFolderCommand(String directoryName);
}
