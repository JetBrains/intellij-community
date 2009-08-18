package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.packagingCompiler.ExplodedDestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.JarInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.IncrementalCompilerInstructionCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class CopyToDirectoryInstructionCreator extends IncrementalCompilerInstructionCreatorBase {
  private final ArtifactsProcessingItemsBuilderContext myContext;
  private final String myOutputPath;
  private final @Nullable VirtualFile myOutputFile;

  public CopyToDirectoryInstructionCreator(ArtifactsProcessingItemsBuilderContext context, String outputPath, @Nullable VirtualFile outputFile) {
    myContext = context;
    myOutputPath = outputPath;
    myOutputFile = outputFile;
  }

  public void addFileCopyInstruction(@NotNull VirtualFile file) {
    final String fileName = file.getName();
    myContext.addDestination(file, new ExplodedDestinationInfo(myOutputPath + "/" + fileName, outputChild(fileName)));
  }

  public IncrementalCompilerInstructionCreator subFolder(String directoryName) {
    return new CopyToDirectoryInstructionCreator(myContext, myOutputPath + "/" + directoryName, outputChild(directoryName));
  }

  public IncrementalCompilerInstructionCreator archive(String archiveFileName) {
    String jarOutputPath = myOutputPath + "/" + archiveFileName;
    final JarInfo jarInfo = new JarInfo();
    VirtualFile outputFile = outputChild(archiveFileName);
    myContext.registerJarFile(jarInfo, jarOutputPath);
    final ExplodedDestinationInfo destination = new ExplodedDestinationInfo(jarOutputPath, outputFile);
    jarInfo.addDestination(destination);
    return new PackIntoArchiveInstructionCreator(myContext, jarInfo, "", destination);
  }

  @Nullable
  private VirtualFile outputChild(String name) {
    return myOutputFile != null ? myOutputFile.findChild(name) : null;
  }
}
