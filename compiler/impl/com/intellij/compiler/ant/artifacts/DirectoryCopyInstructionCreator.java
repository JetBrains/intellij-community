package com.intellij.compiler.ant.artifacts;

import com.intellij.compiler.ant.Generator;
import com.intellij.compiler.ant.Tag;
import com.intellij.compiler.ant.taskdefs.Copy;
import com.intellij.compiler.ant.taskdefs.FileSet;
import com.intellij.compiler.ant.taskdefs.Mkdir;
import com.intellij.packaging.elements.CopyInstructionCreator;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DirectoryCopyInstructionCreator implements CopyInstructionCreator {
  private String myOutputDirectory;

  public DirectoryCopyInstructionCreator(String outputDirectory) {
    myOutputDirectory = outputDirectory;
  }

  public String getOutputDirectory() {
    return myOutputDirectory;
  }

  @NotNull
  public Tag createDirectoryContentCopyInstruction(@NotNull String dirPath) {
    final Copy copy = new Copy(myOutputDirectory);
    copy.add(new FileSet(dirPath));
    return copy;
  }

  @NotNull
  public Tag createFileCopyInstruction(@NotNull String filePath, String outputFileName) {
    return new Copy(filePath, myOutputDirectory + "/" + outputFileName);
  }

  @NotNull
  public CopyInstructionCreator subFolder(String directoryName) {
    return new DirectoryCopyInstructionCreator(myOutputDirectory + "/" + directoryName);
  }

  public Generator createSubFolderCommand(String directoryName) {
    return new Mkdir(myOutputDirectory + "/" + directoryName);
  }
}
