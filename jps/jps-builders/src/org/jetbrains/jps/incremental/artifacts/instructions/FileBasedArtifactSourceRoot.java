package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.PathUtil;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.artifacts.ArtifactSourceToOutputMapping;
import org.jetbrains.jps.incremental.storage.BuildDataManager;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * @author nik
 */
public class FileBasedArtifactSourceRoot extends ArtifactSourceRoot {
  private final File myFile;

  public FileBasedArtifactSourceRoot(@NotNull File file, @NotNull SourceFileFilter filter) {
    super(filter);
    myFile = file;
  }

  @NotNull
  @Override
  public File getRootFile() {
    return myFile;
  }

  @Override
  public boolean containsFile(String filePath, BuildDataManager dataManager) throws IOException {
    return FileUtil.isAncestor(myFile, new File(FileUtil.toSystemDependentName(filePath)), false) && getFilter().accept(filePath, dataManager);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    return myFile.equals(((FileBasedArtifactSourceRoot)o).myFile);
  }

  @Override
  public String toString() {
    return myFile.getPath();
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myFile.hashCode();
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
      targetPath = PathUtil.appendToPath(outputPath, relativePath);
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
