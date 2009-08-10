package com.intellij.packaging.impl.compiler;

import com.intellij.packaging.elements.IncrementalCompilerInstructionCreator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.compiler.impl.packagingCompiler.PackagingProcessingItem;
import com.intellij.compiler.impl.packagingCompiler.ExplodedDestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.JarInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    final PackagingProcessingItem item = myContext.getOrCreateProcessingItem(file);
    final String fileName = file.getName();
    item.addDestination(new ExplodedDestinationInfo(myOutputPath + "/" + fileName, outputChild(fileName)));
  }

  public IncrementalCompilerInstructionCreator subFolder(String directoryName) {
    return new CopyToDirectoryInstructionCreator(myContext, myOutputPath + "/" + directoryName, outputChild(directoryName));
  }

  public IncrementalCompilerInstructionCreator archive(String archiveFileName, List<String> classpath) {
    String jarOutputPath = myOutputPath + "/" + archiveFileName;
    final JarInfo jarInfo = new JarInfo(classpath);
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
