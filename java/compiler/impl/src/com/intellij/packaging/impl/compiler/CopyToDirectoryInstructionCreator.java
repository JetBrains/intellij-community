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
  private final String myOutputPath;
  private final @Nullable VirtualFile myOutputFile;

  public CopyToDirectoryInstructionCreator(ArtifactsProcessingItemsBuilderContext context, String outputPath,
                                           @Nullable VirtualFile outputFile) {
    super(context);
    myOutputPath = outputPath;
    myOutputFile = outputFile;
  }

  public void addFileCopyInstruction(@NotNull VirtualFile file, @NotNull String outputFileName) {
    myContext.addDestination(file, new ExplodedDestinationInfo(myOutputPath + "/" + outputFileName, outputChild(outputFileName)));
  }

  public IncrementalCompilerInstructionCreator subFolder(@NotNull String directoryName) {
    return new CopyToDirectoryInstructionCreator(myContext, myOutputPath + "/" + directoryName, outputChild(directoryName));
  }

  public IncrementalCompilerInstructionCreator archive(@NotNull String archiveFileName) {
    String jarOutputPath = myOutputPath + "/" + archiveFileName;
    final JarInfo jarInfo = new JarInfo();
    if (!myContext.registerJarFile(jarInfo, jarOutputPath)) {
      return new SkipAllInstructionCreator(myContext);
    }
    VirtualFile outputFile = outputChild(archiveFileName);
    final ExplodedDestinationInfo destination = new ExplodedDestinationInfo(jarOutputPath, outputFile);
    jarInfo.addDestination(destination);
    return new PackIntoArchiveInstructionCreator(myContext, jarInfo, "", destination);
  }

  @Nullable
  private VirtualFile outputChild(String name) {
    return myOutputFile != null ? myOutputFile.findChild(name) : null;
  }
}
