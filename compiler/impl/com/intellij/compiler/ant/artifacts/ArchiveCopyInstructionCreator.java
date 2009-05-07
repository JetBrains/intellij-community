package com.intellij.compiler.ant.artifacts;

import com.intellij.compiler.ant.Generator;
import com.intellij.compiler.ant.Tag;
import com.intellij.compiler.ant.taskdefs.ZipFileSet;
import com.intellij.packaging.elements.CopyInstructionCreator;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArchiveCopyInstructionCreator implements CopyInstructionCreator {
  private String myPrefix;

  public ArchiveCopyInstructionCreator(String prefix) {
    myPrefix = prefix;
  }

  @NotNull
  public Tag createDirectoryContentCopyInstruction(@NotNull String dirPath) {
    return new ZipFileSet(dirPath, myPrefix, true);
  }

  @NotNull
  public Tag createFileCopyInstruction(@NotNull String filePath, String outputFileName) {
    final String relativePath = myPrefix + "/" + outputFileName;
    return new ZipFileSet(filePath, relativePath, false);
  }

  @NotNull
  public CopyInstructionCreator subFolder(String directoryName) {
    return new ArchiveCopyInstructionCreator(myPrefix + "/" + directoryName);
  }

  public Generator createSubFolderCommand(String directoryName) {
    return null;
  }
}
