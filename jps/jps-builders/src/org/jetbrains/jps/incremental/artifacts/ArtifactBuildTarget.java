// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts;

import com.dynatrace.hash4j.hashing.HashSink;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuildersRegistry;
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactUtil;
import org.jetbrains.jps.incremental.artifacts.instructions.*;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactOutputPackagingElement;

import java.io.File;
import java.util.*;

@ApiStatus.Internal
public final class ArtifactBuildTarget extends ArtifactBasedBuildTarget implements BuildTargetHashSupplier {
  public ArtifactBuildTarget(@NotNull JpsArtifact artifact) {
    super(ArtifactBuildTargetType.INSTANCE, artifact);
  }

  @Override
  public @NotNull Collection<BuildTarget<?>> computeDependencies(@NotNull BuildTargetRegistry targetRegistry, final @NotNull TargetOutputIndex outputIndex) {
    final LinkedHashSet<BuildTarget<?>> dependencies = new LinkedHashSet<>();
    final JpsArtifact artifact = getArtifact();
    JpsArtifactUtil.processPackagingElements(artifact.getRootElement(), element -> {
      if (element instanceof JpsArtifactOutputPackagingElement) {
        JpsArtifact included = ((JpsArtifactOutputPackagingElement)element).getArtifactReference().resolve();
        if (included != null && !included.equals(artifact)) {
          if (!Strings.isEmpty(included.getOutputPath())) {
            dependencies.add(new ArtifactBuildTarget(included));
            return false;
          }
        }
      }
      dependencies.addAll(LayoutElementBuildersRegistry.getInstance().getDependencies(element, outputIndex));
      return true;
    });
    if (!dependencies.isEmpty()) {
      List<BuildTarget<?>> additional = new ArrayList<>();
      for (BuildTarget<?> dependency : dependencies) {
        if (dependency instanceof ModuleBasedTarget<?>) {
          ModuleBasedTarget target = (ModuleBasedTarget)dependency;
          additional.addAll(targetRegistry.getModuleBasedTargets(target.getModule(), target.isTests()? BuildTargetRegistry.ModuleTargetSelector.TEST : BuildTargetRegistry.ModuleTargetSelector.PRODUCTION));
        }
      }
      dependencies.addAll(additional);
    }
    return dependencies;
  }

  @Override
  public void computeConfigurationDigest(@NotNull ProjectDescriptor projectDescriptor, @NotNull HashSink hash) {
    PathRelativizerService relativizer = projectDescriptor.dataManager.getRelativizer();
    String outputPath = getArtifact().getOutputPath();
    hash.putString(Strings.isEmpty(outputPath) ? "" : relativizer.toRelative(outputPath));
    BuildRootIndex rootIndex = projectDescriptor.getBuildRootIndex();
    List<ArtifactRootDescriptor> targetRoots = rootIndex.getTargetRoots(this, null);
    for (ArtifactRootDescriptor descriptor : targetRoots) {
      descriptor.writeConfiguration(hash, relativizer);
    }
    hash.putInt(targetRoots.size());
  }

  @Override
  public @NotNull List<ArtifactRootDescriptor> computeRootDescriptors(@NotNull JpsModel model,
                                                                      @NotNull ModuleExcludeIndex index,
                                                                      @NotNull IgnoredFileIndex ignoredFileIndex,
                                                                      @NotNull BuildDataPaths dataPaths) {
    ArtifactInstructionsBuilderImpl builder = new ArtifactInstructionsBuilderImpl(index, ignoredFileIndex, this, model, dataPaths);
    ArtifactInstructionsBuilderContext context = new ArtifactInstructionsBuilderContextImpl(model, dataPaths);
    final JpsArtifact artifact = getArtifact();
    String outputPath = Strings.notNullize(artifact.getOutputPath());
    final CopyToDirectoryInstructionCreator instructionCreator = new CopyToDirectoryInstructionCreator(builder, outputPath);
    LayoutElementBuildersRegistry.getInstance().generateInstructions(artifact, instructionCreator, context);
    return builder.getDescriptors();
  }

  @Override
  public ArtifactRootDescriptor findRootDescriptor(@NotNull String rootId,
                                                   @NotNull BuildRootIndex rootIndex) {
    return rootIndex.getTargetRoots(this, null).get(Integer.parseInt(rootId));
  }

  @Override
  public @NotNull String getPresentableName() {
    return "Artifact '" + getArtifact().getName() + "'";
  }

  @Override
  public @NotNull Collection<File> getOutputRoots(@NotNull CompileContext context) {
    String outputFilePath = getArtifact().getOutputFilePath();
    return outputFilePath != null && !Strings.isEmpty(outputFilePath)
           ? Collections.singleton(new File(FileUtilRt.toSystemDependentName(outputFilePath))) : Collections.emptyList();
  }
}
