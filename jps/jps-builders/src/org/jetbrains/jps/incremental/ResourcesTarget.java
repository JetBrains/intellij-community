// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider;
import org.jetbrains.jps.builders.java.FilteredResourceRootDescriptor;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.builders.java.ResourcesTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.ProjectStamps;
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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Describes step of compilation process which copies resources files from source and resource roots of a Java module.
 */
public final class ResourcesTarget extends JVMModuleBuildTarget<ResourceRootDescriptor> {
  private final @NotNull ResourcesTargetType myTargetType;

  public ResourcesTarget(@NotNull JpsModule module, @NotNull ResourcesTargetType targetType) {
    super(targetType, module);
    myTargetType = targetType;
  }

  public @Nullable File getOutputDir() {
    return JpsJavaExtensionService.getInstance().getOutputDirectory(myModule, myTargetType.isTests());
  }

  @Override
  public @NotNull Collection<File> getOutputRoots(@NotNull CompileContext context) {
    return ContainerUtil.createMaybeSingletonList(getOutputDir());
  }

  @Override
  public boolean isTests() {
    return myTargetType.isTests();
  }

  @Override
  public boolean isCompiledBeforeModuleLevelBuilders() {
    return true;
  }

  @Override
  public @NotNull Collection<BuildTarget<?>> computeDependencies(@NotNull BuildTargetRegistry targetRegistry, @NotNull TargetOutputIndex outputIndex) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull List<ResourceRootDescriptor> computeRootDescriptors(@NotNull JpsModel model, @NotNull ModuleExcludeIndex index, @NotNull IgnoredFileIndex ignoredFileIndex, @NotNull BuildDataPaths dataPaths) {
    List<ResourceRootDescriptor> roots = new ArrayList<>();
    JavaSourceRootType type = isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    Iterable<ExcludedJavaSourceRootProvider> excludedRootProviders = JpsServiceManager.getInstance().getExtensions(ExcludedJavaSourceRootProvider.class);
    FileFilter filterForExcludedPatterns = index.getModuleFileFilterHonorExclusionPatterns(myModule);
    for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : myModule.getSourceRoots(type)) {
      if (!isExcludedFromCompilation(excludedRootProviders, sourceRoot)) {
        final String packagePrefix = sourceRoot.getProperties().getPackagePrefix();
        final File rootFile = sourceRoot.getFile();
        roots.add(new FilteredResourceRootDescriptor(rootFile, this, packagePrefix, computeRootExcludes(rootFile, index),
                                                     filterForExcludedPatterns));
      }
    }

    JavaResourceRootType resourceType = isTests() ? JavaResourceRootType.TEST_RESOURCE : JavaResourceRootType.RESOURCE;
    for (JpsTypedModuleSourceRoot<JavaResourceRootProperties> root : myModule.getSourceRoots(resourceType)) {
      if (!isExcludedFromCompilation(excludedRootProviders, root)) {
        File rootFile = root.getFile();
        String relativeOutputPath = root.getProperties().getRelativeOutputPath();
        roots.add(new ResourceRootDescriptor(rootFile, this, relativeOutputPath.replace('/', '.'), computeRootExcludes(rootFile, index), filterForExcludedPatterns));
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
    return "Resources for '" + getModule().getName() + "' " + (myTargetType.isTests() ? "tests" : "production");
  }

  @Override
  public void writeConfiguration(@NotNull ProjectDescriptor pd, @NotNull PrintWriter out) {
    int fingerprint = 0;
    final BuildRootIndex rootIndex = pd.getBuildRootIndex();
    final PathRelativizerService relativizer = pd.dataManager.getRelativizer();
    final List<ResourceRootDescriptor> roots = rootIndex.getTargetRoots(this, null);
    for (ResourceRootDescriptor root : roots) {
      String path = relativizer.toRelative(root.getRootFile().getAbsolutePath());
      fingerprint += pathHashCode(path);
      fingerprint += root.getPackagePrefix().hashCode();
    }
    out.write(Integer.toHexString(fingerprint));
  }

  private static int pathHashCode(@Nullable String path) {
    // On case insensitive OS hash calculated from path converted to lower case
    if (ProjectStamps.PORTABLE_CACHES) {
      return StringUtil.isEmpty(path) ? 0 : FileUtil.toCanonicalPath(path).hashCode();
    }
    return FileUtil.pathHashCode(path);
  }
}
