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
package org.jetbrains.jps.builders.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
* @author Eugene Zhuravlev
*/
public class BuildOutputConsumerImpl implements BuildOutputConsumer {
  private static final Logger LOG = Logger.getInstance(BuildOutputConsumerImpl.class);
  private final BuildTarget<?> myTarget;
  private final CompileContext myContext;
  private FileGeneratedEvent myFileGeneratedEvent;
  private Collection<File> myOutputs;
  private THashSet<String> myRegisteredSources = new THashSet<>(FileUtil.PATH_HASHING_STRATEGY);

  public BuildOutputConsumerImpl(BuildTarget<?> target, CompileContext context) {
    myTarget = target;
    myContext = context;
    myFileGeneratedEvent = new FileGeneratedEvent(target);
    myOutputs = myTarget.getOutputRoots(context);
  }

  private void registerOutput(final File output, boolean isDirectory, Collection<String> sourcePaths) throws IOException {
    final String outputPath = FileUtil.toSystemIndependentName(output.getPath());
    for (File outputRoot : myOutputs) {
      final String outputRootPath = FileUtil.toSystemIndependentName(outputRoot.getPath());
      final String relativePath = FileUtil.getRelativePath(outputRootPath, outputPath, '/');
      if (relativePath != null && !relativePath.startsWith("../")) {
        // the relative path must be under the root or equal to it
        if (isDirectory) {
          addEventsRecursively(output, outputRootPath, relativePath);
        }
        else {
          myFileGeneratedEvent.add(outputRootPath, relativePath);
        }
      }
    }
    final SourceToOutputMapping mapping = myContext.getProjectDescriptor().dataManager.getSourceToOutputMap(myTarget);
    for (String sourcePath : sourcePaths) {
      if (myRegisteredSources.add(FileUtil.toSystemIndependentName(sourcePath))) {
        mapping.setOutput(sourcePath, outputPath);
      }
      else {
        mapping.appendOutput(sourcePath, outputPath);
      }
    }
  }

  private void addEventsRecursively(File output, String outputRootPath, String relativePath) {
    File[] children = output.listFiles();
    if (children == null) {
      myFileGeneratedEvent.add(outputRootPath, relativePath);
    }
    else {
      String prefix = relativePath.isEmpty() || relativePath.equals(".") ? "" : relativePath + "/";
      for (File child : children) {
        addEventsRecursively(child, outputRootPath, prefix + child.getName());
      }
    }
  }

  @Override
  public void registerOutputFile(@NotNull final File outputFile, @NotNull Collection<String> sourcePaths) throws IOException {
    registerOutput(outputFile, false, sourcePaths);
  }

  @Override
  public void registerOutputDirectory(@NotNull File outputDir, @NotNull Collection<String> sourcePaths) throws IOException {
    LOG.assertTrue(!(myTarget instanceof ModuleBuildTarget) && !(myTarget instanceof ArtifactBuildTarget),
                   "'registerOutputDirectory' method cannot be used for target " + myTarget + ", it will break incremental compilation");
    registerOutput(outputDir, true, sourcePaths);
  }

  public int getNumberOfProcessedSources() {
    return myRegisteredSources.size();
  }

  public void fireFileGeneratedEvent() {
    if (!myFileGeneratedEvent.getPaths().isEmpty()) {
      myContext.processMessage(myFileGeneratedEvent);
    }
  }
}
