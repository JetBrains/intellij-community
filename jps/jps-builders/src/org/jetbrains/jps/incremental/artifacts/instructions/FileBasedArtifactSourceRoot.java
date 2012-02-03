package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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
  public int hashCode() {
    return 31 * super.hashCode() + myFile.hashCode();
  }
}
