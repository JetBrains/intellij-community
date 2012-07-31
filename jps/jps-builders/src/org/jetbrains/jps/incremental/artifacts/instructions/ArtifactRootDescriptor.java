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
public abstract class ArtifactRootDescriptor {
  protected final File myRoot;
  private final SourceFileFilter myFilter;
  private final int myRootIndex;
  private final int myArtifactId;
  private final String myArtifactName;

  protected ArtifactRootDescriptor(File root, @NotNull SourceFileFilter filter, int index, int artifactId, String artifactName) {
    myRoot = root;
    myFilter = filter;
    myRootIndex = index;
    myArtifactId = artifactId;
    myArtifactName = artifactName;
  }

  public final String getArtifactName() {
    return myArtifactName;
  }

  public int getArtifactId() {
    return myArtifactId;
  }

  @NotNull
  public final File getRootFile() {
    return myRoot;
  }

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
