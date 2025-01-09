// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.dynatrace.hash4j.hashing.HashSink;
import com.dynatrace.hash4j.hashing.HashStream64;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.*;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.ProjectStamps;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumeratorImpl;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Describes a step of compilation process which produces JVM *.class files from files in production/test source roots of a Java module.
 * These targets are built by {@link ModuleLevelBuilder} and they are the only targets that can have circular dependencies on each other.
 */
@ApiStatus.NonExtendable
// open for Bazel - we cannot introduce an interface for ModuleBuildTarget to avoid using `instanceOf` for now,
// as it would involve a relatively massive and potentially unsafe change.
public class ModuleBuildTarget extends JVMModuleBuildTarget<JavaSourceRootDescriptor> implements BuildTargetHashSupplier {
  private static final Logger LOG = Logger.getInstance(ModuleBuildTarget.class);

  public static final Boolean REBUILD_ON_DEPENDENCY_CHANGE = Boolean.valueOf(
    System.getProperty(GlobalOptions.REBUILD_ON_DEPENDENCY_CHANGE_OPTION, "true")
  );
  private final JavaModuleBuildTargetType targetType;

  public ModuleBuildTarget(@NotNull JpsModule module, @NotNull JavaModuleBuildTargetType targetType) {
    super(targetType, module);
    this.targetType = targetType;
  }

  public @Nullable File getOutputDir() {
    return JpsJavaExtensionService.getInstance().getOutputDirectory(myModule, targetType.isTests());
  }

  @Override
  public @NotNull @Unmodifiable Collection<File> getOutputRoots(@NotNull CompileContext context) {
    File outputDir = getOutputDir();

    JpsModule module = getModule();
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(module.getProject());
    ProcessorConfigProfile profile = configuration.getAnnotationProcessingProfile(module);
    if (profile.isEnabled()) {
      File annotationOut = ProjectPaths.getAnnotationProcessorGeneratedSourcesOutputDir(module, isTests(), profile);
      if (annotationOut != null) {
        return outputDir == null ? List.of(annotationOut) : List.of(outputDir, annotationOut);
      }
    }
    return outputDir == null ? List.of() : List.of(outputDir);
  }

  @Override
  public boolean isTests() {
    return targetType.isTests();
  }

  @Override
  public @NotNull @Unmodifiable Collection<BuildTarget<?>> computeDependencies(@NotNull BuildTargetRegistry targetRegistry, @NotNull TargetOutputIndex outputIndex) {
    JpsJavaDependenciesEnumeratorImpl enumerator = (JpsJavaDependenciesEnumeratorImpl)JpsJavaExtensionService.dependencies(myModule).compileOnly();
    if (!isTests()) {
      enumerator.productionOnly();
    }
    List<BuildTarget<?>> dependencies = new ArrayList<>();
    enumerator.processDependencies(dependencyElement -> {
      if (dependencyElement instanceof JpsModuleDependency) {
        JpsModule depModule = ((JpsModuleDependency)dependencyElement).getModule();
        if (depModule != null) {
          JavaModuleBuildTargetType targetType;
          if (this.targetType.equals(JavaModuleBuildTargetType.PRODUCTION) && enumerator.isProductionOnTests(dependencyElement)) {
            targetType = JavaModuleBuildTargetType.TEST;
          }
          else {
            targetType = this.targetType;
          }
          dependencies.add(new ModuleBuildTarget(depModule, targetType));
        }
      }
      return true;
    });
    if (isTests()) {
      dependencies.add(new ModuleBuildTarget(myModule, JavaModuleBuildTargetType.PRODUCTION));
    }
    Collection<ModuleBasedTarget<?>> moduleBased = targetRegistry.getModuleBasedTargets(
      getModule(), isTests() ? BuildTargetRegistry.ModuleTargetSelector.TEST : BuildTargetRegistry.ModuleTargetSelector.PRODUCTION
    );
    for (ModuleBasedTarget<?> target : moduleBased) {
      if (target != this && target.isCompiledBeforeModuleLevelBuilders()) {
        dependencies.add(target);
      }
    }
    return dependencies;
  }

  @Override
  public @NotNull @Unmodifiable List<JavaSourceRootDescriptor> computeRootDescriptors(
    @NotNull JpsModel model,
    @NotNull ModuleExcludeIndex index,
    @NotNull IgnoredFileIndex ignoredFileIndex,
    @NotNull BuildDataPaths dataPaths
  ) {
    List<JavaSourceRootDescriptor> roots = new ArrayList<>();
    JavaSourceRootType type = isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    Iterable<ExcludedJavaSourceRootProvider> excludedRootProviders = JpsServiceManager.getInstance().getExtensions(ExcludedJavaSourceRootProvider.class);
    JpsJavaCompilerConfiguration compilerConfig = JpsJavaExtensionService.getInstance().getCompilerConfiguration(myModule.getProject());

    roots_loop:
    for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : myModule.getSourceRoots(type)) {
      if (index.isExcludedFromModule(sourceRoot.getFile(), myModule)) {
        continue;
      }
      for (ExcludedJavaSourceRootProvider provider : excludedRootProviders) {
        if (provider.isExcludedFromCompilation(myModule, sourceRoot)) {
          continue roots_loop;
        }
      }

      String packagePrefix = sourceRoot.getProperties().getPackagePrefix();

      // consider annotation processors output for generated sources, if contained under some source root
      Set<Path> excludes = computeRootExcludes(sourceRoot.getPath(), index);
      ProcessorConfigProfile profile = compilerConfig.getAnnotationProcessingProfile(myModule);
      if (profile.isEnabled()) {
        File outputIoDir = ProjectPaths.getAnnotationProcessorGeneratedSourcesOutputDir(myModule, JavaSourceRootType.TEST_SOURCE == sourceRoot.getRootType(), profile);
        if (outputIoDir != null) {
          Path outputDir = outputIoDir.toPath();
          if (sourceRoot.getPath().startsWith(outputDir)) {
            excludes = FileCollectionFactory.createCanonicalPathSet(excludes);
            excludes.add(outputDir);
          }
        }
      }
      FileFilter filterForExcludedPatterns = index.getModuleFileFilterHonorExclusionPatterns(myModule);
      roots.add(JavaSourceRootDescriptor.createJavaSourceRootDescriptor(sourceRoot.getFile(), this, false, false, packagePrefix, excludes, filterForExcludedPatterns));
    }
    return roots;
  }

  @Override
  public @NotNull String getPresentableName() {
    return "Module '" + getModule().getName() + "' " + (targetType.isTests() ? "tests" : "production");
  }

  @Override
  @ApiStatus.Internal
  public void computeConfigurationDigest(@NotNull ProjectDescriptor projectDescriptor, @NotNull HashSink hash) {
    JpsModule module = getModule();
    PathRelativizerService relativizer = projectDescriptor.dataManager.getRelativizer();

    StringBuilder logBuilder = LOG.isDebugEnabled() ? new StringBuilder() : null;

    getDependenciesFingerprint(logBuilder, relativizer, hash);

    List<JavaSourceRootDescriptor> roots = projectDescriptor.getBuildRootIndex().getTargetRoots(this, null);
    for (JavaSourceRootDescriptor root : roots) {
      String path = relativizer.toRelative(root.rootFile);
      if (logBuilder != null) {
        logBuilder.append(path).append('\n');
      }
      FileHashUtil.computePathHashCode(path, hash);
    }
    hash.putInt(roots.size());

    LanguageLevel level = JpsJavaExtensionService.getInstance().getLanguageLevel(module);
    if (level == null) {
      hash.putInt(0);
    }
    else {
      if (logBuilder != null) {
        logBuilder.append(level.name()).append("\n");
      }
      hash.putString(level.name());
    }

    JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(module.getProject());
    String bytecodeTarget = config.getByteCodeTargetLevel(module.getName());
    if (bytecodeTarget == null) {
      hash.putInt(0);
    }
    else {
      if (logBuilder != null) {
        logBuilder.append(bytecodeTarget).append('\n');
      }
      hash.putString(bytecodeTarget);
    }

    CompilerEncodingConfiguration encodingConfig = projectDescriptor.getEncodingConfiguration();
    String encoding = encodingConfig.getPreferredModuleEncoding(module);
    if (encoding == null) {
      hash.putInt(0);
    }
    else {
      if (logBuilder != null) {
        logBuilder.append(encoding).append("\n");
      }
      hash.putString(encoding);
    }

    if (logBuilder == null) {
      return;
    }

    Path configurationTextFile = projectDescriptor.dataManager.getDataPaths().getTargetDataRootDir(this).resolve("config.dat.debug.txt");
    @NonNls String oldText;
    try {
      oldText = Files.readString(configurationTextFile);
    }
    catch (IOException e) {
      oldText = null;
    }
    String newText = logBuilder.toString();
    if (!newText.equals(oldText)) {
      if (oldText != null && hash instanceof HashStream64) {
        LOG.debug("Configuration differs from the last recorded one for " + getPresentableName() + ".\nRecorded configuration:\n" + oldText +
                  "\nCurrent configuration (hash=" + ((HashStream64)hash).getAsLong() + "):\n" + newText);
      }
      try {
        Files.createDirectories(configurationTextFile.getParent());
        Files.writeString(configurationTextFile, newText);
      }
      catch (IOException e) {
        LOG.debug(e);
      }
    }
  }

  private void getDependenciesFingerprint(@Nullable StringBuilder logBuilder, @NotNull PathRelativizerService relativizer, @NotNull HashSink hash) {
    if (!REBUILD_ON_DEPENDENCY_CHANGE) {
      hash.putInt(0);
      return;
    }

    JpsModule module = getModule();
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).compileOnly().recursivelyExportedOnly();
    if (!isTests()) {
      enumerator = enumerator.productionOnly();
    }
    if (ProjectStamps.PORTABLE_CACHES) {
      enumerator = enumerator.withoutSdk();
    }

    Collection<Path> roots = enumerator.classes().getPaths();
    for (Path file : roots) {
      String path = relativizer.toRelative(file);
      getContentHash(file, hash);
      if (logBuilder != null) {
        logBuilder.append(path);
        // not a content hash, but the current hash value
        if (hash instanceof HashStream64) {
          logBuilder.append(": ").append((((HashStream64)hash).getAsLong()));
        }
        logBuilder.append("\n");
      }
      FileHashUtil.computePathHashCode(path, hash);
    }
    hash.putInt(roots.size());
  }

  private static void getContentHash(Path file, HashSink hash) {
    if (ProjectStamps.TRACK_LIBRARY_CONTENT) {
      try {
        if (Files.isRegularFile(file) && file.getFileName().endsWith(".jar")) {
          FileHashUtil.getFileHash(file, hash);
        }
        else {
          hash.putInt(0);
        }
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
