package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.artifacts.ArtifactSourceToOutputMapping;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public abstract class ArtifactSourceRoot {
  private final SourceFileFilter myFilter;
  private final int myRootIndex;

  protected ArtifactSourceRoot(@NotNull SourceFileFilter filter, int index) {
    myFilter = filter;
    myRootIndex = index;
  }

  @NotNull
  public abstract File getRootFile();

  public abstract void copyFromRoot(String filePath,
                                    int rootIndex, String outputPath,
                                    CompileContext context, ArtifactSourceToOutputMapping srcOutMapping,
                                    ArtifactOutputToSourceMapping outSrcMapping) throws IOException;

  public SourceFileFilter getFilter() {
    return myFilter;
  }

  public int getRootIndex() {
    return myRootIndex;
  }
}
