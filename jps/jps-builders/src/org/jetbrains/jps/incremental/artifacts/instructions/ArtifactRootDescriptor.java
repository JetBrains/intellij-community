// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author nik
 */
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

  public void writeConfiguration(PrintWriter out) {
    out.println(getFullPath());
    out.println("->" + myDestinationInfo.getOutputPath());
  }

  @Override
  public ArtifactBuildTarget getTarget() {
    return myTarget;
  }

  @NotNull
  @Override
  public FileFilter createFileFilter() {
    return file -> myFilter.accept(file.getAbsolutePath());
  }

  @Override
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
