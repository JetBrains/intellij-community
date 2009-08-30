package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.packagingCompiler.DestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.ExplodedDestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.JarDestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.JarInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.IncrementalCompilerInstructionCreator;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class PackIntoArchiveInstructionCreator extends IncrementalCompilerInstructionCreatorBase {
  private final ArtifactsProcessingItemsBuilderContext myContext;
  private final DestinationInfo myJarDestination;
  private final JarInfo myJarInfo;
  private final String myPathInJar;

  public PackIntoArchiveInstructionCreator(ArtifactsProcessingItemsBuilderContext context, JarInfo jarInfo, String pathInJar,
                                           DestinationInfo jarDestination) {
    myContext = context;
    myJarInfo = jarInfo;
    myPathInJar = pathInJar;
    myJarDestination = jarDestination;
  }

  public void addFileCopyInstruction(@NotNull VirtualFile file, String outputFileName) {
    final String pathInJar = childPathInJar(outputFileName);
    myContext.addDestination(file, new JarDestinationInfo(pathInJar, myJarInfo, myJarDestination));
    myJarInfo.addContent(pathInJar, file);
  }

  private String childPathInJar(String fileName) {
    return myPathInJar.length() == 0 ? fileName : myPathInJar + "/" + fileName;
  }

  public IncrementalCompilerInstructionCreator subFolder(String directoryName) {
    return new PackIntoArchiveInstructionCreator(myContext, myJarInfo, childPathInJar(directoryName), myJarDestination);
  }

  public IncrementalCompilerInstructionCreator archive(String archiveFileName) {
    final JarInfo jarInfo = new JarInfo();
    if (myJarDestination instanceof ExplodedDestinationInfo) {
      myContext.registerJarFile(jarInfo, myJarDestination.getOutputPath());
    }
    final JarDestinationInfo destination = new JarDestinationInfo(childPathInJar(archiveFileName), myJarInfo, myJarDestination);
    jarInfo.addDestination(destination);
    return new PackIntoArchiveInstructionCreator(myContext, jarInfo, "", destination);
  }
}
