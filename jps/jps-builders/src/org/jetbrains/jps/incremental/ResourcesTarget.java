// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.dynatrace.hash4j.hashing.HashSink;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetHashSupplier;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider;
import org.jetbrains.jps.builders.java.FilteredResourceRootDescriptor;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.builders.java.ResourcesTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Describes a step of compilation process which copies resource's files from source and resource roots of a Java module.
 */
public final class ResourcesTarget extends JVMModuleBuildTarget<ResourceRootDescriptor> implements BuildTargetHashSupplier {
  private final @NotNull ResourcesTargetType targetType;

  public ResourcesTarget(@NotNull JpsModule module, @NotNull ResourcesTargetType targetType) {
    super(targetType, module);
    this.targetType = targetType;
  }

  public @Nullable File getOutputDir() {
    return JpsJavaExtensionService.getInstance().getOutputDirectory(myModule, targetType.isTests());
  }

  @Override
  public @NotNull Collection<File> getOutputRoots(@NotNull CompileContext context) {
    File element = getOutputDir();
    return element == null ? List.of() : List.of(element);
  }

  @Override
  public boolean isTests() {
    return targetType.isTests();
  }

  @Override
  public boolean isCompiledBeforeModuleLevelBuilders() {
    return true;
  }

  @Override
  public @NotNull Collection<BuildTarget<?>> computeDependencies(@NotNull BuildTargetRegistry targetRegistry, @NotNull TargetOutputIndex outputIndex) {
    return List.of();
  }

  @Override
  public @NotNull List<ResourceRootDescriptor> computeRootDescriptors(@NotNull JpsModel model,
                                                                      @NotNull ModuleExcludeIndex index,
                                                                      @NotNull IgnoredFileIndex ignoredFileIndex,
                                                                      @NotNull BuildDataPaths dataPaths) {
    List<ResourceRootDescriptor> roots = new ArrayList<>();
    JavaSourceRootType type = isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    Iterable<ExcludedJavaSourceRootProvider> excludedRootProviders = JpsServiceManager.getInstance().getExtensions(ExcludedJavaSourceRootProvider.class);
    FileFilter filterForExcludedPatterns = index.getModuleFileFilterHonorExclusionPatterns(myModule);
    for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : myModule.getSourceRoots(type)) {
      if (!isExcludedFromCompilation(excludedRootProviders, sourceRoot)) {
        String packagePrefix = sourceRoot.getProperties().getPackagePrefix();
        Path rootFile = sourceRoot.getPath();
        roots.add(new FilteredResourceRootDescriptor(rootFile.toFile(), this, packagePrefix, computeRootExcludes(rootFile, index),
                                                     filterForExcludedPatterns));
      }
    }

    JavaResourceRootType resourceType = isTests() ? JavaResourceRootType.TEST_RESOURCE : JavaResourceRootType.RESOURCE;
    for (JpsTypedModuleSourceRoot<JavaResourceRootProperties> root : myModule.getSourceRoots(resourceType)) {
      if (!isExcludedFromCompilation(excludedRootProviders, root)) {
        Path rootFile = root.getPath();
        String relativeOutputPath = root.getProperties().getRelativeOutputPath();
        roots.add(new ResourceRootDescriptor(rootFile.toFile(), this, relativeOutputPath.replace('/', '.'), computeRootExcludes(rootFile, index), filterForExcludedPatterns));
      }
    }
    
    return roots;
  }

  private boolean isExcludedFromCompilation(Iterable<ExcludedJavaSourceRootProvider> excludedRootProviders, JpsModuleSourceRoot sourceRoot) {
    for (ExcludedJavaSourceRootProvider provider : excludedRootProviders) {
      if (provider.isExcludedFromCompilation(myModule, sourceRoot)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public @NotNull String getPresentableName() {
    return "Resources for '" + getModule().getName() + "' " + (targetType.isTests() ? "tests" : "production");
  }

  @Override
  @ApiStatus.Internal
  public void computeConfigurationDigest(@NotNull ProjectDescriptor projectDescriptor, @NotNull HashSink hash) {
    PathRelativizerService relativizer = projectDescriptor.dataManager.getRelativizer();

    List<ResourceRootDescriptor> roots = projectDescriptor.getBuildRootIndex().getTargetRoots(this, null);
    for (ResourceRootDescriptor root : roots) {
      String path = relativizer.toRelative(root.rootFile.toString());
      FileHashUtil.computePathHashCode(path, hash);
      hash.putString(root.getPackagePrefix());
    }
    hash.putInt(roots.size());
  }
}
