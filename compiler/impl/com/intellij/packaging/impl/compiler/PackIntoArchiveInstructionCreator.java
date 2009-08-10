package com.intellij.packaging.impl.compiler;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.IncrementalCompilerInstructionCreator;
import com.intellij.compiler.impl.packagingCompiler.*;

import java.util.List;

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

  public void addFileCopyInstruction(@NotNull VirtualFile file) {
    final PackagingProcessingItem item = myContext.getOrCreateProcessingItem(file);
    final String fileName = file.getName();
    final String pathInJar = childPathInJar(fileName);
    item.addDestination(new JarDestinationInfo(pathInJar, myJarInfo, myJarDestination));
    myJarInfo.addContent(pathInJar, file);
  }

  private String childPathInJar(String fileName) {
    return myPathInJar.length() == 0 ? fileName : myPathInJar + "/" + fileName;
  }

  public IncrementalCompilerInstructionCreator subFolder(String directoryName) {
    return new PackIntoArchiveInstructionCreator(myContext, myJarInfo, childPathInJar(directoryName), myJarDestination);
  }

  public IncrementalCompilerInstructionCreator archive(String archiveFileName, List<String> classpath) {
    final JarInfo jarInfo = new JarInfo(classpath);
    if (myJarDestination instanceof ExplodedDestinationInfo) {
      myContext.registerJarFile(jarInfo, myJarDestination.getOutputPath());
    }
    final JarDestinationInfo destination = new JarDestinationInfo(childPathInJar(archiveFileName), myJarInfo, myJarDestination);
    jarInfo.addDestination(destination);
    return new PackIntoArchiveInstructionCreator(myContext, jarInfo, "", destination);
  }
}
