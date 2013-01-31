/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.artifacts.IncArtifactBuilder;
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactPathUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * @author nik
 */
public class FileBasedArtifactRootDescriptor extends ArtifactRootDescriptor {
  private static final Logger LOG = Logger.getInstance(FileBasedArtifactRootDescriptor.class);
  public FileBasedArtifactRootDescriptor(@NotNull File file,
                                         @NotNull SourceFileFilter filter,
                                         int index,
                                         ArtifactBuildTarget target,
                                         @NotNull DestinationInfo destinationInfo) {
    super(file, filter, index, target, destinationInfo);
  }

  @Override
  protected String getFullPath() {
    return myRoot.getPath();
  }

  public void copyFromRoot(String filePath,
                           int rootIndex, String outputPath,
                           CompileContext context, BuildOutputConsumer outputConsumer,
                           ArtifactOutputToSourceMapping outSrcMapping) throws IOException, ProjectBuildException {
    final File file = new File(filePath);
    if (!file.exists()) return;
    String targetPath;
    if (!FileUtil.filesEqual(file, getRootFile())) {
      final String relativePath = FileUtil.getRelativePath(FileUtil.toSystemIndependentName(getRootFile().getPath()), filePath, '/');
      if (relativePath == null || relativePath.startsWith("..")) {
        throw new ProjectBuildException(new AssertionError(filePath + " is not under " + getRootFile().getPath()));
      }
      targetPath = JpsArtifactPathUtil.appendToPath(outputPath, relativePath);
    }
    else {
      targetPath = outputPath;
    }

    final File targetFile = new File(targetPath);
    if (FileUtil.filesEqual(file, targetFile)) {
      //do not process file if should be copied to itself. Otherwise the file will be included to source-to-output mapping and will be deleted by rebuild
      return;
    }

    if (outSrcMapping.getState(targetPath) == null) {
      ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
      if (logger.isEnabled()) {
        logger.logCompiledFiles(Collections.singletonList(file), IncArtifactBuilder.BUILDER_NAME, "Copying file:");
      }
      FileUtil.copyContent(file, targetFile);
      outputConsumer.registerOutputFile(targetFile, Collections.singletonList(filePath));
    }
    else if (LOG.isDebugEnabled()) {
      LOG.debug("Target path " + targetPath + " is already registered so " + filePath + " won't be copied");
    }
    outSrcMapping.appendData(targetPath, rootIndex, filePath);
  }
}
