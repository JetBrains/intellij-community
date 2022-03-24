// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Describes step of compilation process which produces JVM *.class files from files in production/test source roots of a Java module. These
 * targets are built by {@link ModuleLevelBuilder} and they are the only targets which can have circular dependencies on each other.
 */
public final class ModuleBuildTarget extends JVMModuleBuildTarget<JavaSourceRootDescriptor> {
  private static final Logger LOG = Logger.getInstance(ModuleBuildTarget.class);

  public static final Boolean REBUILD_ON_DEPENDENCY_CHANGE = Boolean.valueOf(
    System.getProperty(GlobalOptions.REBUILD_ON_DEPENDENCY_CHANGE_OPTION, "true")
  );
  private final JavaModuleBuildTargetType myTargetType;

  public ModuleBuildTarget(@NotNull JpsModule module, JavaModuleBuildTargetType targetType) {
    super(targetType, module);
    myTargetType = targetType;
  }

  @Nullable
  public File getOutputDir() {
    return JpsJavaExtensionService.getInstance().getOutputDirectory(myModule, myTargetType.isTests());
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    Collection<File> result = new SmartList<>();
    final File outputDir = getOutputDir();
    if (outputDir != null) {
      result.add(outputDir);
    }
    final JpsModule module = getModule();
    final JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(module.getProject());
    final ProcessorConfigProfile profile = configuration.getAnnotationProcessingProfile(module);
    if (profile.isEnabled()) {
      final File annotationOut = ProjectPaths.getAnnotationProcessorGeneratedSourcesOutputDir(module, isTests(), profile);
      if (annotationOut != null) {
        result.add(annotationOut);
      }
    }
    return result;
  }

  @Override
  public boolean isTests() {
    return myTargetType.isTests();
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
    JpsJavaDependenciesEnumeratorImpl enumerator = (JpsJavaDependenciesEnumeratorImpl)JpsJavaExtensionService.dependencies(myModule).compileOnly();
    if (!isTests()) {
      enumerator.productionOnly();
    }
    final ArrayList<BuildTarget<?>> dependencies = new ArrayList<>();
    enumerator.processDependencies(dependencyElement -> {
      if (dependencyElement instanceof JpsModuleDependency) {
        JpsModule depModule = ((JpsModuleDependency)dependencyElement).getModule();
        if (depModule != null) {
          JavaModuleBuildTargetType targetType;
          if (myTargetType.equals(JavaModuleBuildTargetType.PRODUCTION) && enumerator.isProductionOnTests(dependencyElement)) {
            targetType = JavaModuleBuildTargetType.TEST;
          }
          else {
            targetType = myTargetType;
          }
          dependencies.add(new ModuleBuildTarget(depModule, targetType));
        }
      }
      return true;
    });
    if (isTests()) {
      dependencies.add(new ModuleBuildTarget(myModule, JavaModuleBuildTargetType.PRODUCTION));
    }
    final Collection<ModuleBasedTarget<?>> moduleBased = targetRegistry.getModuleBasedTargets(
      getModule(), isTests() ? BuildTargetRegistry.ModuleTargetSelector.TEST : BuildTargetRegistry.ModuleTargetSelector.PRODUCTION
    );
    for (ModuleBasedTarget<?> target : moduleBased) {
      if (target != this && target.isCompiledBeforeModuleLevelBuilders()) {
        dependencies.add(target);
      }
    }
    dependencies.trimToSize();
    return dependencies;
  }

  @NotNull
  @Override
  public List<JavaSourceRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
    List<JavaSourceRootDescriptor> roots = new ArrayList<>();
    JavaSourceRootType type = isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    Iterable<ExcludedJavaSourceRootProvider> excludedRootProviders = JpsServiceManager.getInstance().getExtensions(ExcludedJavaSourceRootProvider.class);
    final JpsJavaCompilerConfiguration compilerConfig = JpsJavaExtensionService.getInstance().getCompilerConfiguration(myModule.getProject());

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
      final String packagePrefix = sourceRoot.getProperties().getPackagePrefix();

      // consider annotation processors output for generated sources, if contained under some source root
      Set<File> excludes = computeRootExcludes(sourceRoot.getFile(), index);
      final ProcessorConfigProfile profile = compilerConfig.getAnnotationProcessingProfile(myModule);
      if (profile.isEnabled()) {
        final File outputDir = ProjectPaths.getAnnotationProcessorGeneratedSourcesOutputDir(myModule, JavaSourceRootType.TEST_SOURCE == sourceRoot.getRootType(), profile);
        if (outputDir != null && FileUtil.isAncestor(sourceRoot.getFile(), outputDir, true)) {
          excludes = FileCollectionFactory.createCanonicalFileSet(excludes);
          excludes.add(outputDir);
        }
      }
      FileFilter filterForExcludedPatterns = index.getModuleFileFilterHonorExclusionPatterns(myModule);
      roots.add(new JavaSourceRootDescriptor(sourceRoot.getFile(), this, false, false, packagePrefix, excludes, filterForExcludedPatterns));
    }
    return roots;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Module '" + getModule().getName() + "' " + (myTargetType.isTests() ? "tests" : "production");
  }

  @Override
  public void writeConfiguration(ProjectDescriptor pd, PrintWriter out) {
    final JpsModule module = getModule();
    final PathRelativizerService relativizer = pd.dataManager.getRelativizer();

    final StringBuilder logBuilder = LOG.isDebugEnabled() ? new StringBuilder() : null;

    int fingerprint = getDependenciesFingerprint(logBuilder, relativizer);

    for (JavaSourceRootDescriptor root : pd.getBuildRootIndex().getTargetRoots(this, null)) {
      final File file = root.getRootFile();
      String path = relativizer.toRelative(file.getPath());
      if (logBuilder != null) {
        logBuilder.append(path).append("\n");
      }
      fingerprint += pathHashCode(path);
    }

    final LanguageLevel level = JpsJavaExtensionService.getInstance().getLanguageLevel(module);
    if (level != null) {
      if (logBuilder != null) {
        logBuilder.append(level.name()).append("\n");
      }
      fingerprint += level.name().hashCode();
    }

    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(module.getProject());
    final String bytecodeTarget = config.getByteCodeTargetLevel(module.getName());
    if (bytecodeTarget != null) {
      if (logBuilder != null) {
        logBuilder.append(bytecodeTarget).append("\n");
      }
      fingerprint += bytecodeTarget.hashCode();
    }

    final CompilerEncodingConfiguration encodingConfig = pd.getEncodingConfiguration();
    final String encoding = encodingConfig.getPreferredModuleEncoding(module);
    if (encoding != null) {
      if (logBuilder != null) {
        logBuilder.append(encoding).append("\n");
      }
      fingerprint += encoding.hashCode();
    }

    final String hash = Integer.toHexString(fingerprint);
    out.write(hash);
    if (logBuilder != null) {
      File configurationTextFile = new File(pd.getTargetsState().getDataPaths().getTargetDataRoot(this), "config.dat.debug.txt");
      @NonNls String oldText;
      try {
        oldText = FileUtil.loadFile(configurationTextFile);
      }
      catch (IOException e) {
        oldText = null;
      }
      String newText = logBuilder.toString();
      if (!newText.equals(oldText)) {
        if (oldText != null) {
          LOG.debug("Configuration differs from the last recorded one for " + getPresentableName() + ".\nRecorded configuration:\n" + oldText +
                    "\nCurrent configuration (hash=" + hash + "):\n" + newText);
        }
        try {
          FileUtil.writeToFile(configurationTextFile, newText);
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    }
  }

  private int getDependenciesFingerprint(@Nullable StringBuilder logBuilder, @NotNull PathRelativizerService relativizer) {
    int fingerprint = 0;

    if (!REBUILD_ON_DEPENDENCY_CHANGE) {
      return fingerprint;
    }

    final JpsModule module = getModule();
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).compileOnly().recursively().exportedOnly();
    if (!isTests()) {
      enumerator = enumerator.productionOnly();
    }
    if (ProjectStamps.PORTABLE_CACHES) {
      enumerator = enumerator.withoutSdk();
    }

    for (File file : enumerator.classes().getRoots()) {
      String path = relativizer.toRelative(file.getAbsolutePath());

      if (logBuilder != null) {
        logBuilder.append(path).append("\n");
      }
      fingerprint = 31 * fingerprint + pathHashCode(path);
    }
    return fingerprint;
  }

  private static int pathHashCode(@NotNull String path) {
    // On case insensitive OS hash calculated from path converted to lower case
    if (ProjectStamps.PORTABLE_CACHES) {
      return StringUtil.isEmpty(path) ? 0 : FileUtil.toCanonicalPath(path).hashCode();
    }
    return FileUtil.pathHashCode(path);
  }
}
