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
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
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
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public final class ModuleBuildTarget extends JVMModuleBuildTarget<JavaSourceRootDescriptor> {
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
    Collection<File> result = new SmartList<File>();
    final File outputDir = getOutputDir();
    if (outputDir != null) {
      result.add(outputDir);
    }
    final JpsModule module = getModule();
    final JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(module.getProject());
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
  public final boolean isCompiledBeforeModuleLevelBuilders() {
    return false;
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(myModule).compileOnly();
    if (!isTests()) {
      enumerator.productionOnly();
    }
    final ArrayList<BuildTarget<?>> dependencies = new ArrayList<BuildTarget<?>>();
    enumerator.processModules(new Consumer<JpsModule>() {
      @Override
      public void consume(JpsModule module) {
        dependencies.add(new ModuleBuildTarget(module, myTargetType));
      }
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
    List<JavaSourceRootDescriptor> roots = new ArrayList<JavaSourceRootDescriptor>();
    JavaSourceRootType type = isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    Iterable<ExcludedJavaSourceRootProvider> excludedRootProviders = JpsServiceManager.getInstance().getExtensions(ExcludedJavaSourceRootProvider.class);
    final Set<File> moduleExcludes = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    moduleExcludes.addAll(index.getModuleExcludes(myModule));
    final JpsJavaCompilerConfiguration compilerConfig = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(myModule.getProject());

    roots_loop:
    for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> sourceRoot : myModule.getSourceRoots(type)) {
      if (JpsPathUtil.isUnder(moduleExcludes, sourceRoot.getFile())) {
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
          excludes = ContainerUtil.newTroveSet(FileUtil.FILE_HASHING_STRATEGY, excludes);
          excludes.add(outputDir);
        }
      }

      roots.add(new JavaSourceRootDescriptor(sourceRoot.getFile(), this, false, false, packagePrefix, excludes));
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

    int fingerprint = getDependenciesFingerprint();

    for (JavaSourceRootDescriptor root : pd.getBuildRootIndex().getTargetRoots(this, null)) {
      fingerprint += FileUtil.fileHashCode(root.getRootFile());
    }
    
    final LanguageLevel level = JpsJavaExtensionService.getInstance().getLanguageLevel(module);
    if (level != null) {
      fingerprint += level.name().hashCode();
    }

    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(module.getProject());
    final String bytecodeTarget = config.getByteCodeTargetLevel(module.getName());
    if (bytecodeTarget != null) {
      fingerprint += bytecodeTarget.hashCode();
    }

    final CompilerEncodingConfiguration encodingConfig = pd.getEncodingConfiguration();
    final String encoding = encodingConfig.getPreferredModuleEncoding(module);
    if (encoding != null) {
      fingerprint += encoding.hashCode();
    }

    out.write(Integer.toHexString(fingerprint));
  }

  private int getDependenciesFingerprint() {
    int fingerprint = 0;

    if (!REBUILD_ON_DEPENDENCY_CHANGE) {
      return fingerprint;
    }

    final JpsModule module = getModule();
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).compileOnly();
    if (!isTests()) {
      enumerator = enumerator.productionOnly();
    }

    for (String url : enumerator.classes().getUrls()) {
      fingerprint = 31 * fingerprint + url.hashCode();
    }
    return fingerprint;
  }
}
