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
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.builders.java.ResourcesTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public final class ResourcesTarget extends JVMModuleBuildTarget<ResourceRootDescriptor> {
  private final ResourcesTargetType myTargetType;

  public ResourcesTarget(@NotNull JpsModule module, ResourcesTargetType targetType) {
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
    return result;
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
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<ResourceRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
    List<ResourceRootDescriptor> roots = new ArrayList<ResourceRootDescriptor>();
    JavaSourceRootType type = isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    Iterable<ExcludedJavaSourceRootProvider> excludedRootProviders = JpsServiceManager.getInstance().getExtensions(ExcludedJavaSourceRootProvider.class);

    final THashSet<File> addedRoots = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);

    roots_loop:
    for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> sourceRoot : myModule.getSourceRoots(type)) {
      for (ExcludedJavaSourceRootProvider provider : excludedRootProviders) {
        if (provider.isExcludedFromCompilation(myModule, sourceRoot)) {
          continue roots_loop;
        }
      }
      final String packagePrefix = sourceRoot.getProperties().getData().getPackagePrefix();
      final File rootFile = sourceRoot.getFile();
      roots.add(new ResourceRootDescriptor(rootFile, this, false, packagePrefix, computeRootExcludes(rootFile, index)));
      addedRoots.add(rootFile);
    }

    final ProcessorConfigProfile profile = findAnnotationProcessingProfile(model);
    if (profile != null) {
      final File annotationOut = new ProjectPaths(model.getProject()).getAnnotationProcessorGeneratedSourcesOutputDir(getModule(), isTests(), profile);
      if (annotationOut != null && !addedRoots.contains(annotationOut) && !FileUtil.filesEqual(annotationOut, getOutputDir())) {
        roots.add(new ResourceRootDescriptor(annotationOut, this, true, "", computeRootExcludes(annotationOut, index)));
      }
    }

    return roots;
  }

  @Nullable
  private ProcessorConfigProfile findAnnotationProcessingProfile(JpsModel model) {
    final Collection<ProcessorConfigProfile> allProfiles =
      JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(model.getProject()).getAnnotationProcessingConfigurations();
    ProcessorConfigProfile profile = null;
    final String moduleName = getModule().getName();
    for (ProcessorConfigProfile p : allProfiles) {
      if (p.getModuleNames().contains(moduleName)) {
        if (p.isEnabled()) {
          profile = p;
        }
        break;
      }
    }
    return profile;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Resources for '" + getModule().getName() + "' " + (myTargetType.isTests() ? "tests" : "production");
  }

  @Override
  public void writeConfiguration(PrintWriter out, BuildDataPaths dataPaths, BuildRootIndex buildRootIndex) {
    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(getModule().getProject());
    int fingerprint = 0;
    final List<String> patterns = config.getResourcePatterns();
    for (String pattern : patterns) {
      fingerprint += pattern.hashCode();
    }
    final List<ResourceRootDescriptor> roots = buildRootIndex.getTargetRoots(this, null);
    for (ResourceRootDescriptor root : roots) {
      fingerprint += FileUtil.fileHashCode(root.getRootFile());
      fingerprint += root.getPackagePrefix().hashCode();
    }
    out.write(Integer.toHexString(fingerprint));
  }

}
