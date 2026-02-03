// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.dynatrace.hash4j.hashing.HashSink;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

@ApiStatus.Internal
public abstract class ArtifactRootDescriptor extends BuildRootDescriptor {
  protected final File myRoot;
  private final SourceFileFilter myFilter;
  private final int myRootIndex;
  private final ArtifactBuildTarget myTarget;
  private final DestinationInfo myDestinationInfo;

  protected ArtifactRootDescriptor(File root,
                                   @NotNull SourceFileFilter filter,
                                   int index,
                                   ArtifactBuildTarget target,
                                   @NotNull DestinationInfo destinationInfo) {
    myRoot = root;
    myFilter = filter;
    myRootIndex = index;
    myTarget = target;
    myDestinationInfo = destinationInfo;
  }

  @Override
  public String toString() {
    return getFullPath();
  }

  protected abstract String getFullPath();

  public void writeConfiguration(@NotNull HashSink hash, PathRelativizerService relativizer) {
    hash.putString(relativizer.toRelative(getFullPath()));
    hash.putString(relativizer.toRelative(myDestinationInfo.getOutputPath()));
  }

  @Override
  public @NotNull ArtifactBuildTarget getTarget() {
    return myTarget;
  }

  @Override
  public @NotNull FileFilter createFileFilter() {
    return file -> myFilter.accept(file.getAbsolutePath());
  }

  @Override
  public final @NotNull File getRootFile() {
    return myRoot;
  }

  @Override
  public @NotNull String getRootId() {
    return String.valueOf(myRootIndex);
  }

  public abstract void copyFromRoot(String filePath,
                                    int rootIndex, String outputPath,
                                    CompileContext context, BuildOutputConsumer outputConsumer,
                                    ArtifactOutputToSourceMapping outSrcMapping) throws IOException, ProjectBuildException;

  public SourceFileFilter getFilter() {
    return myFilter;
  }

  public DestinationInfo getDestinationInfo() {
    return myDestinationInfo;
  }

  public int getRootIndex() {
    return myRootIndex;
  }
}
