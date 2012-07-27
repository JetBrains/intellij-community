package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.artifacts.ArtifactSourceToOutputMapping;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * @author nik
 */
public class FileBasedArtifactSourceRoot extends ArtifactSourceRoot {
  private final File myFile;

  public FileBasedArtifactSourceRoot(@NotNull File file, @NotNull SourceFileFilter filter, int index) {
    super(filter, index);
    myFile = file;
  }

  @NotNull
  @Override
  public File getRootFile() {
    return myFile;
  }

  @Override
  public String toString() {
    return myFile.getPath();
  }

  public void copyFromRoot(String filePath,
                           int rootIndex, String outputPath,
                           CompileContext context, ArtifactSourceToOutputMapping srcOutMapping,
                           ArtifactOutputToSourceMapping outSrcMapping) throws IOException {
    final File file = new File(FileUtil.toSystemDependentName(filePath));
    if (!file.exists()) return;
    String targetPath;
    if (!file.equals(getRootFile())) {
      final String relativePath = FileUtil.getRelativePath(FileUtil.toSystemIndependentName(getRootFile().getPath()), filePath, '/');
      targetPath = JpsPathUtil.appendToPath(outputPath, relativePath);
    }
    else {
      targetPath = outputPath;
    }

    if (outSrcMapping.getState(targetPath) == null) {
      context.getLoggingManager().getArtifactBuilderLogger().fileCopied(filePath);
      final File targetFile = new File(FileUtil.toSystemDependentName(targetPath));
      FileUtil.copyContent(file, targetFile);
      srcOutMapping.appendData(filePath, Collections.singletonList(targetPath));
    }
    outSrcMapping.appendData(targetPath, Collections.singletonList(new ArtifactOutputToSourceMapping.SourcePathAndRootIndex(filePath, rootIndex)));
  }
}
