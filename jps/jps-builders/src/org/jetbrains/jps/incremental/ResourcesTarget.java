package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
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
public final class ResourcesTarget extends ModuleBasedTarget<ResourceRootDescriptor> {
  private final String myModuleName;
  private final ResourcesTargetType myTargetType;

  public ResourcesTarget(@NotNull JpsModule module, ResourcesTargetType targetType) {
    super(targetType, module);
    myTargetType = targetType;
    myModuleName = module.getName();
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

  public String getModuleName() {
    return myModuleName;
  }

  @Override
  public boolean isTests() {
    return myTargetType.isTests();
  }

  @Override
  public String getId() {
    return myModuleName;
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry) {
    return Collections.<BuildTarget<?>>singleton(
      new ModuleBuildTarget(myModule, isTests()? JavaModuleBuildTargetType.TEST: JavaModuleBuildTargetType.PRODUCTION)
    );
  }

  @NotNull
  @Override
  public List<ResourceRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
    List<ResourceRootDescriptor> roots = new ArrayList<ResourceRootDescriptor>();
    JavaSourceRootType type = isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    Iterable<ExcludedJavaSourceRootProvider> excludedRootProviders = JpsServiceManager.getInstance().getExtensions(ExcludedJavaSourceRootProvider.class);

    roots_loop:
    for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> sourceRoot : myModule.getSourceRoots(type)) {
      for (ExcludedJavaSourceRootProvider provider : excludedRootProviders) {
        if (provider.isExcludedFromCompilation(myModule, sourceRoot)) {
          continue roots_loop;
        }
      }
      final String packagePrefix = sourceRoot.getProperties().getData().getPackagePrefix();
      roots.add(new ResourceRootDescriptor(sourceRoot.getFile(), this, false, packagePrefix));
    }

    final ProcessorConfigProfile profile = findAnnotationProcessingProfile(model);
    if (profile != null) {
      final File annotationOut = new ProjectPaths(model.getProject()).getAnnotationProcessorGeneratedSourcesOutputDir(getModule(), isTests(), profile);
      if (annotationOut != null && !FileUtil.filesEqual(annotationOut, getOutputDir())) {
        roots.add(new ResourceRootDescriptor(annotationOut, this, true, ""));
      }
    }

    return roots;
  }

  @Nullable
  private ProcessorConfigProfile findAnnotationProcessingProfile(JpsModel model) {
    final Collection<ProcessorConfigProfile> allProfiles =
      JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(model.getProject()).getAnnotationProcessingConfigurations();
    ProcessorConfigProfile profile = null;
    for (ProcessorConfigProfile p : allProfiles) {
      if (p.getModuleNames().contains(getModuleName())) {
        if (p.isEnabled()) {
          profile = p;
        }
        break;
      }
    }
    return profile;
  }

  @Override
  public ResourceRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    List<ResourceRootDescriptor> descriptors = rootIndex.getRootDescriptors(new File(rootId), Collections.<ResourcesTargetType>singletonList(myTargetType), null);
    return ContainerUtil.getFirstItem(descriptors);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Resources for '" + myModuleName + "' " + (myTargetType.isTests() ? "tests" : "production");
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
