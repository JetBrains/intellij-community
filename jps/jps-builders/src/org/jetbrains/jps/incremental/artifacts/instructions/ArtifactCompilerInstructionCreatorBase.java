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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.artifacts.JarPathUtil;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public abstract class ArtifactCompilerInstructionCreatorBase implements ArtifactCompilerInstructionCreator {
  protected final ArtifactInstructionsBuilderImpl myInstructionsBuilder;

  public ArtifactCompilerInstructionCreatorBase(ArtifactInstructionsBuilderImpl instructionsBuilder) {
    myInstructionsBuilder = instructionsBuilder;
  }

  public void addDirectoryCopyInstructions(@NotNull File directoryUrl) {
    addDirectoryCopyInstructions(directoryUrl, null);
  }

  public void addDirectoryCopyInstructions(@NotNull File directory, @Nullable SourceFileFilter filter) {
    addDirectoryCopyInstructions(directory, filter, FileCopyingHandler.DEFAULT);
  }

  public void addDirectoryCopyInstructions(@NotNull File directory, @Nullable SourceFileFilter filter, @NotNull FileCopyingHandler copyingHandler) {
    final boolean copyExcluded = myInstructionsBuilder.getRootsIndex().isExcluded(directory);
    SourceFileFilter fileFilter = new SourceFileFilterImpl(filter, myInstructionsBuilder.getRootsIndex(), myInstructionsBuilder.getIgnoredFileIndex(), copyExcluded);
    DestinationInfo destination = createDirectoryDestination();
    if (destination != null) {
      ArtifactRootDescriptor descriptor = myInstructionsBuilder.createFileBasedRoot(directory, fileFilter, destination, copyingHandler);
      if (myInstructionsBuilder.addDestination(descriptor)) {
        onAdded(descriptor);
      }
    }
  }

  @Override
  public void addExtractDirectoryInstruction(@NotNull File jarFile, @NotNull String pathInJar) {
    addExtractDirectoryInstruction(jarFile, pathInJar, Conditions.<String>alwaysTrue());
  }

  @Override
  public void addExtractDirectoryInstruction(@NotNull File jarFile,
                                             @NotNull String pathInJar,
                                             @NotNull Condition<String> pathInJarFilter) {
    //an entry of a jar file is excluded if and only if the jar file itself is excluded. In that case we should unpack entries to the artifact
    //because the jar itself is explicitly added to the artifact layout.
    boolean includeExcluded = true;

    final SourceFileFilterImpl filter = new SourceFileFilterImpl(null, myInstructionsBuilder.getRootsIndex(),
                                                                 myInstructionsBuilder.getIgnoredFileIndex(), includeExcluded);
    DestinationInfo destination = createDirectoryDestination();
    if (destination != null) {
      ArtifactRootDescriptor descriptor = myInstructionsBuilder.createJarBasedRoot(jarFile, pathInJar, filter, destination, pathInJarFilter);
      if (myInstructionsBuilder.addDestination(descriptor)) {
        onAdded(descriptor);
      }
    }
  }

  @Override
  public abstract ArtifactCompilerInstructionCreatorBase subFolder(@NotNull String directoryName);

  public ArtifactCompilerInstructionCreator subFolderByRelativePath(@NotNull String relativeDirectoryPath) {
    final List<String> folders = StringUtil.split(relativeDirectoryPath, "/");
    ArtifactCompilerInstructionCreator current = this;
    for (String folder : folders) {
      current = current.subFolder(folder);
    }
    return current;
  }


  @Override
  public void addFileCopyInstruction(@NotNull File file, @NotNull String outputFileName) {
    addFileCopyInstruction(file, outputFileName, FileCopyingHandler.DEFAULT);
  }

  @Override
  public void addFileCopyInstruction(@NotNull File file, @NotNull String outputFileName, @NotNull FileCopyingHandler copyingHandler) {
    DestinationInfo destination = createFileDestination(outputFileName);
    if (destination != null) {
      FileBasedArtifactRootDescriptor root = myInstructionsBuilder.createFileBasedRoot(file, SourceFileFilter.ALL, destination, copyingHandler);
      if (myInstructionsBuilder.addDestination(root)) {
        onAdded(root);
      }
    }
  }

  @Override
  public ArtifactInstructionsBuilder getInstructionsBuilder() {
    return myInstructionsBuilder;
  }

  @Nullable
  protected abstract DestinationInfo createDirectoryDestination();

  protected abstract DestinationInfo createFileDestination(@NotNull String outputFileName);

  protected abstract void onAdded(ArtifactRootDescriptor descriptor);

  private static class SourceFileFilterImpl extends SourceFileFilter {
    private final SourceFileFilter myBaseFilter;
    private final ModuleExcludeIndex myRootsIndex;
    private final IgnoredFileIndex myIgnoredFileIndex;
    private final boolean myIncludeExcluded;

    private SourceFileFilterImpl(@Nullable SourceFileFilter baseFilter,
                                 @NotNull ModuleExcludeIndex rootsIndex,
                                 IgnoredFileIndex patterns,
                                 boolean includeExcluded) {
      myBaseFilter = baseFilter;
      myRootsIndex = rootsIndex;
      myIgnoredFileIndex = patterns;
      myIncludeExcluded = includeExcluded;
    }

    @Override
    public boolean accept(@NotNull String fullFilePath) {
      if (myBaseFilter != null && !myBaseFilter.accept(fullFilePath)) return false;

      if (myIgnoredFileIndex.isIgnored(PathUtilRt.getFileName(fullFilePath))) {
        return false;
      }

      if (!myIncludeExcluded) {
        final File file = JarPathUtil.getLocalFile(fullFilePath);
        if (myRootsIndex.isExcluded(file)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean shouldBeCopied(@NotNull String fullFilePath, ProjectDescriptor projectDescriptor) throws IOException {
      return myBaseFilter == null || myBaseFilter.shouldBeCopied(fullFilePath, projectDescriptor);
    }
  }
}
