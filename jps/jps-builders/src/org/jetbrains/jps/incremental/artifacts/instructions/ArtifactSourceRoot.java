package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.artifacts.ArtifactSourceToOutputMapping;
import org.jetbrains.jps.incremental.storage.BuildDataManager;

import java.io.File;
import java.io.IOException;

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

  public abstract boolean containsFile(String filePath, BuildDataManager dataManager) throws IOException;

  public abstract void copyFromRoot(String filePath,
                                    int rootIndex, String outputPath,
                                    CompileContext context, ArtifactSourceToOutputMapping srcOutMapping,
                                    ArtifactOutputToSourceMapping outSrcMapping) throws IOException;

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
