package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.artifacts.ArtifactSourceToOutputMapping;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public abstract class ArtifactRootDescriptor extends BuildRootDescriptor {
  protected final File myRoot;
  private final SourceFileFilter myFilter;
  private final int myRootIndex;
  private final ArtifactBuildTarget myTarget;

  protected ArtifactRootDescriptor(File root, @NotNull SourceFileFilter filter, int index, ArtifactBuildTarget target) {
    myRoot = root;
    myFilter = filter;
    myRootIndex = index;
    myTarget = target;
  }

  public final String getArtifactName() {
    return myTarget.getId();
  }

  public ArtifactBuildTarget getTarget() {
    return myTarget;
  }

  @NotNull
  public final File getRootFile() {
    return myRoot;
  }

  @Override
  public String getRootId() {
    return String.valueOf(myRootIndex);
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
