package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class DestinationInfo {
  private VirtualFile myOutputFile;
  private String myOutputPath;
  private String myOutputFilePath;

  protected DestinationInfo(final @NotNull String outputPath, final @Nullable VirtualFile outputFile, @NotNull String outputFilePath) {
    myOutputFilePath = outputFilePath;
    myOutputFile = outputFile;
    myOutputPath = outputPath;
  }

  @NotNull
  public String getOutputPath() {
    return myOutputPath;
  }

  @Nullable
  public VirtualFile getOutputFile() {
    return myOutputFile;
  }

  @NotNull
  public String getOutputFilePath() {
    return myOutputFilePath;
  }

  public void update() {
    if (myOutputFile != null && !myOutputFile.isValid()) {
      myOutputFile = null;
    }
    if (myOutputFile == null) {
      myOutputFile = LocalFileSystem.getInstance().findFileByPath(myOutputFilePath);
    }
  }
}
