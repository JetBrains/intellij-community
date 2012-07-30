package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.artifacts.ArtifactRootId;
import org.jetbrains.jps.incremental.artifacts.ArtifactSourceToOutputMapping;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public abstract class ArtifactRootDescriptor {
  protected final File myRoot;
  private final SourceFileFilter myFilter;
  private final ArtifactRootId myRootId;
  private final String myArtifactName;

  protected ArtifactRootDescriptor(File root, @NotNull SourceFileFilter filter, int index, int artifactId, String artifactName) {
    myRoot = root;
    myFilter = filter;
    myArtifactName = artifactName;
    myRootId = new ArtifactRootId(artifactId, index);
  }

  public final String getArtifactName() {
    return myArtifactName;
  }

  public final ArtifactRootId getRootId() {
    return myRootId;
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
    return myRootId.getRootIndex();
  }
}
