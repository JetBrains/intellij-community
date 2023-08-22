// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtifactInstructionsBuilderImpl implements ArtifactInstructionsBuilder {
  private final Map<String, JarInfo> myJarByPath;
  private final List<ArtifactRootDescriptor> myDescriptors;
  private final ModuleExcludeIndex myRootsIndex;
  private final Iterable<ArtifactRootCopyingHandlerProvider> myCopyingHandlerProviders;
  private int myRootIndex;
  private final IgnoredFileIndex myIgnoredFileIndex;
  private final ArtifactBuildTarget myBuildTarget;
  private final JpsModel myModel;
  private final BuildDataPaths myBuildDataPaths;

  public ArtifactInstructionsBuilderImpl(@NotNull ModuleExcludeIndex rootsIndex,
                                         @NotNull IgnoredFileIndex ignoredFileIndex,
                                         @NotNull ArtifactBuildTarget target,
                                         @NotNull JpsModel model, @NotNull BuildDataPaths dataPaths) {
    myRootsIndex = rootsIndex;
    myIgnoredFileIndex = ignoredFileIndex;
    myBuildTarget = target;
    myModel = model;
    myBuildDataPaths = dataPaths;
    myJarByPath = new HashMap<>();
    myDescriptors = new ArrayList<>();
    myCopyingHandlerProviders = JpsServiceManager.getInstance().getExtensions(ArtifactRootCopyingHandlerProvider.class);
  }

  public IgnoredFileIndex getIgnoredFileIndex() {
    return myIgnoredFileIndex;
  }

  public boolean addDestination(@NotNull ArtifactRootDescriptor descriptor) {
    myDescriptors.add(descriptor);
    return true;
  }

  public ModuleExcludeIndex getRootsIndex() {
    return myRootsIndex;
  }

  public boolean registerJarFile(@NotNull JarInfo jarInfo, @NotNull String outputPath) {
    if (myJarByPath.containsKey(outputPath)) {
      return false;
    }
    myJarByPath.put(outputPath, jarInfo);
    return true;
  }

  @Override
  public @NotNull List<ArtifactRootDescriptor> getDescriptors() {
    return myDescriptors;
  }

  public FileBasedArtifactRootDescriptor createFileBasedRoot(@NotNull File file,
                                                             @NotNull SourceFileFilter filter,
                                                             final @NotNull DestinationInfo destinationInfo, FileCopyingHandler handler) {
    return new FileBasedArtifactRootDescriptor(file, filter, myRootIndex++, myBuildTarget, destinationInfo, handler);
  }

  @Override
  public @NotNull FileCopyingHandler createCopyingHandler(@NotNull File file,
                                                          @NotNull JpsPackagingElement contextElement,
                                                          @NotNull ArtifactCompilerInstructionCreator instructionCreator) {
    File targetDirectory = instructionCreator.getTargetDirectory();
    if (targetDirectory != null) {
      for (ArtifactRootCopyingHandlerProvider provider : myCopyingHandlerProviders) {
        FileCopyingHandler handler =
          provider.createCustomHandler(myBuildTarget.getArtifact(), file, targetDirectory, contextElement, myModel, myBuildDataPaths);
        if (handler != null) {
          return handler;
        }
      }
    }
    return FilterCopyHandler.DEFAULT;
  }

  public JarBasedArtifactRootDescriptor createJarBasedRoot(@NotNull File jarFile,
                                                           @NotNull String pathInJar,
                                                           @NotNull SourceFileFilter filter,
                                                           @NotNull DestinationInfo destinationInfo,
                                                           @NotNull Condition<? super String> pathInJarFilter) {
    return new JarBasedArtifactRootDescriptor(jarFile, pathInJar, filter, myRootIndex++, myBuildTarget, destinationInfo, pathInJarFilter);
  }
}
