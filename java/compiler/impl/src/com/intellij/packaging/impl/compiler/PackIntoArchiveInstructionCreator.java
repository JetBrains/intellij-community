/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.packagingCompiler.DestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.JarDestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.JarInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.IncrementalCompilerInstructionCreator;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class PackIntoArchiveInstructionCreator extends IncrementalCompilerInstructionCreatorBase {
  private final DestinationInfo myJarDestination;
  private final JarInfo myJarInfo;
  private final String myPathInJar;

  public PackIntoArchiveInstructionCreator(ArtifactsProcessingItemsBuilderContext context, JarInfo jarInfo,
                                           String pathInJar, DestinationInfo jarDestination) {
    super(context);
    myJarInfo = jarInfo;
    myPathInJar = pathInJar;
    myJarDestination = jarDestination;
  }

  public void addFileCopyInstruction(@NotNull VirtualFile file, @NotNull String outputFileName) {
    final String pathInJar = childPathInJar(outputFileName);
    if (myContext.addDestination(file, new JarDestinationInfo(pathInJar, myJarInfo, myJarDestination))) {
      myJarInfo.addContent(pathInJar, file);
    }
  }

  private String childPathInJar(String fileName) {
    return myPathInJar.length() == 0 ? fileName : myPathInJar + "/" + fileName;
  }

  public PackIntoArchiveInstructionCreator subFolder(@NotNull String directoryName) {
    return new PackIntoArchiveInstructionCreator(myContext, myJarInfo, childPathInJar(directoryName), myJarDestination);
  }

  public IncrementalCompilerInstructionCreator archive(@NotNull String archiveFileName) {
    final JarInfo jarInfo = new JarInfo();
    final String outputPath = myJarDestination.getOutputPath() + "/" + archiveFileName;
    if (!myContext.registerJarFile(jarInfo, outputPath)) {
      return new SkipAllInstructionCreator(myContext);
    }
    final JarDestinationInfo destination = new JarDestinationInfo(childPathInJar(archiveFileName), myJarInfo, myJarDestination);
    jarInfo.addDestination(destination);
    return new PackIntoArchiveInstructionCreator(myContext, jarInfo, "", destination);
  }
}
