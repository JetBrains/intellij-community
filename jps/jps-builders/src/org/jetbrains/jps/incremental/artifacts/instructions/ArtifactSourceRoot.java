package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public abstract class ArtifactSourceRoot {
  private final SourceFileFilter myFilter;

  protected ArtifactSourceRoot(@NotNull SourceFileFilter filter) {
    myFilter = filter;
  }

  @NotNull
  public abstract File getRootFile();

  public abstract boolean containsFile(String filePath);

  public abstract void copyFromRoot(String filePath, String outputPath, List<String> outputs) throws IOException;

  public SourceFileFilter getFilter() {
    return myFilter;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return myFilter.equals(((ArtifactSourceRoot)o).myFilter);
  }

  @Override
  public int hashCode() {
    return myFilter.hashCode();
  }
}
