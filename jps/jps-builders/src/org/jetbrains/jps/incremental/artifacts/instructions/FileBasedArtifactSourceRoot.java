package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.PathUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
  public boolean containsFile(String filePath) {
    return FileUtil.isAncestor(myFile, new File(FileUtil.toSystemDependentName(filePath)), false) && getFilter().accept(filePath);
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

  public void copyFromRoot(String filePath, String outputPath, List<String> outputs) throws IOException {
    final File file = new File(FileUtil.toSystemDependentName(filePath));
    String targetPath;
    if (!file.equals(getRootFile())) {
      final String relativePath = FileUtil.getRelativePath(FileUtil.toSystemIndependentName(getRootFile().getPath()), filePath, '/');
      targetPath = PathUtil.appendToPath(outputPath, relativePath);
    }
    else {
      targetPath = outputPath;
    }
    final File targetFile = new File(FileUtil.toSystemDependentName(targetPath));
    FileUtil.copyContent(file, targetFile);
    outputs.add(targetPath);
  }
}
