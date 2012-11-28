package org.jetbrains.jps.incremental.artifacts.instructions;

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
    outSrcMapping.appendData(targetPath, Collections.singletonList(new ArtifactOutputToSourceMapping.SourcePathAndRootIndex(filePath, rootIndex)));
  }
}
