/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.IgnoredFilePatterns;
import org.jetbrains.jps.PathUtil;
import org.jetbrains.jps.incremental.ModuleRootsIndex;
import org.jetbrains.jps.incremental.artifacts.JarPathUtil;
import org.jetbrains.jps.incremental.storage.BuildDataManager;

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
    final boolean copyExcluded = myInstructionsBuilder.getRootsIndex().isExcluded(directory);
    SourceFileFilter fileFilter = new SourceFileFilterImpl(filter, myInstructionsBuilder.getRootsIndex(), myInstructionsBuilder.getIgnoredFilePatterns(), copyExcluded);
    addDirectoryCopyInstructions(new FileBasedArtifactSourceRoot(directory, fileFilter));
  }

  @Override
  public void addExtractDirectoryInstruction(@NotNull File jarFile, @NotNull String pathInJar) {
    final SourceFileFilterImpl filter = new SourceFileFilterImpl(null, myInstructionsBuilder.getRootsIndex(),
                                                                 myInstructionsBuilder.getIgnoredFilePatterns(), false);
    addDirectoryCopyInstructions(new JarBasedArtifactSourceRoot(jarFile, pathInJar, filter));
  }

  protected abstract void addDirectoryCopyInstructions(ArtifactSourceRoot root);

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

  private static class SourceFileFilterImpl extends SourceFileFilter {
    private final SourceFileFilter myBaseFilter;
    private final ModuleRootsIndex myRootsIndex;
    private final IgnoredFilePatterns myIgnoredFilePatterns;
    private final boolean myIncludeExcluded;

    private SourceFileFilterImpl(@Nullable SourceFileFilter baseFilter,
                                 @NotNull ModuleRootsIndex rootsIndex,
                                 IgnoredFilePatterns patterns,
                                 boolean includeExcluded) {
      myBaseFilter = baseFilter;
      myRootsIndex = rootsIndex;
      myIgnoredFilePatterns = patterns;
      myIncludeExcluded = includeExcluded;
    }

    @Override
    public boolean accept(@NotNull String fullFilePath, BuildDataManager dataManager) throws IOException {
      if (myBaseFilter != null && !myBaseFilter.accept(fullFilePath, dataManager)) return false;

      if (myIgnoredFilePatterns.isIgnored(PathUtil.getFileName(fullFilePath))) {
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
  }
}
