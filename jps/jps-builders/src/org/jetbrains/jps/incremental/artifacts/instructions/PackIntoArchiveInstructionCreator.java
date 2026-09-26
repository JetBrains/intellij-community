// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@ApiStatus.Internal
public final class PackIntoArchiveInstructionCreator extends ArtifactCompilerInstructionCreatorBase {
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

  @Override
  protected @NotNull DestinationInfo createDirectoryDestination() {
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
    return myPathInJar.isEmpty() ? fileName : myPathInJar + "/" + fileName;
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

  @Override
  public @Nullable File getTargetDirectory() {
    return null;
  }
}
