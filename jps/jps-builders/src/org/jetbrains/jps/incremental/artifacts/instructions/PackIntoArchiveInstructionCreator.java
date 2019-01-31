// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author nik
 */
public class PackIntoArchiveInstructionCreator extends ArtifactCompilerInstructionCreatorBase {
  private final DestinationInfo myJarDestination;
  private final JarInfo myJarInfo;
  private final String myPathInJar;

  public PackIntoArchiveInstructionCreator(ArtifactInstructionsBuilderImpl builder, JarInfo jarInfo,
                                           String pathInJar, DestinationInfo jarDestination) {
    super(builder);
    myJarInfo = jarInfo;
    myPathInJar = pathInJar;
    myJarDestination = jarDestination;
  }

  @Nullable
  @Override
  protected DestinationInfo createDirectoryDestination() {
    return new JarDestinationInfo(myPathInJar, myJarInfo, myJarDestination);
  }

  @Override
  protected JarDestinationInfo createFileDestination(@NotNull String pathInJar) {
    return new JarDestinationInfo(childPathInJar(pathInJar), myJarInfo, myJarDestination);
  }

  @Override
  protected void onAdded(ArtifactRootDescriptor descriptor) {
    myJarInfo.addContent(StringUtil.trimStart(((JarDestinationInfo)descriptor.getDestinationInfo()).getPathInJar(), "/"), descriptor);
  }

  private String childPathInJar(String fileName) {
    return myPathInJar.length() == 0 ? fileName : myPathInJar + "/" + fileName;
  }

  @Override
  public PackIntoArchiveInstructionCreator subFolder(@NotNull String directoryName) {
    return new PackIntoArchiveInstructionCreator(myInstructionsBuilder, myJarInfo, childPathInJar(directoryName), myJarDestination);
  }

  @Override
  public ArtifactCompilerInstructionCreator archive(@NotNull String archiveFileName) {
    final JarDestinationInfo destination = createFileDestination(archiveFileName);
    final JarInfo jarInfo = new JarInfo(destination);
    final String outputPath = myJarDestination.getOutputPath() + "/" + archiveFileName;
    if (!myInstructionsBuilder.registerJarFile(jarInfo, outputPath)) {
      return new SkipAllInstructionCreator(myInstructionsBuilder);
    }
    myJarInfo.addJar(destination.getPathInJar(), jarInfo);
    return new PackIntoArchiveInstructionCreator(myInstructionsBuilder, jarInfo, "", destination);
  }

  @Nullable
  @Override
  public File getTargetDirectory() {
    return null;
  }
}
