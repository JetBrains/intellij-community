/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public ArtifactBuildTarget getTarget() {
    return myTarget;
  }

  @NotNull
  @Override
  public FileFilter createFileFilter() {
    return new FileFilter() {
      @Override
      public boolean accept(File file) {
        return myFilter.accept(file.getAbsolutePath());
      }
    };
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
