package org.jetbrains.jps.incremental;

import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.ModuleBasedTarget;
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ModuleBuildTarget extends ModuleBasedTarget<JavaSourceRootDescriptor> {
  private final String myModuleName;
  private final JavaModuleBuildTargetType myTargetType;

  public ModuleBuildTarget(@NotNull JpsModule module, JavaModuleBuildTargetType targetType) {
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
  public Collection<File> getOutputDirs(CompileContext context) {
    Collection<File> result = new SmartList<File>();
    final File outputDir = getOutputDir();
    if (outputDir != null) {
      result.add(outputDir);
    }
    final ProcessorConfigProfile profile = context.getAnnotationProcessingProfile(getModule());
    if (profile.isEnabled()) {
      final File annotationOut = context.getProjectPaths().getAnnotationProcessorGeneratedSourcesOutputDir(getModule(), isTests(), profile);
      if (annotationOut != null) {
        result.add(annotationOut);
      }
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
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(myModule).compileOnly();
    if (!isTests()) {
      enumerator.productionOnly();
    }
    final List<BuildTarget<?>> dependencies = new ArrayList<BuildTarget<?>>();
    enumerator.processModules(new Consumer<JpsModule>() {
      @Override
      public void consume(JpsModule module) {
        dependencies.add(new ModuleBuildTarget(module, myTargetType));
      }
    });
    if (isTests()) {
      dependencies.add(new ModuleBuildTarget(myModule, JavaModuleBuildTargetType.PRODUCTION));
    }
    return dependencies;
  }

  @NotNull
  @Override
  public List<JavaSourceRootDescriptor> computeRootDescriptors(JpsModel model, ModuleExcludeIndex index, IgnoredFileIndex ignoredFileIndex, BuildDataPaths dataPaths) {
    List<JavaSourceRootDescriptor> roots = new ArrayList<JavaSourceRootDescriptor>();
    JavaSourceRootType type = isTests() ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    Iterable<ExcludedJavaSourceRootProvider> excludedRootProviders = JpsServiceManager.getInstance().getExtensions(ExcludedJavaSourceRootProvider.class);

    roots_loop:
    for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> sourceRoot : myModule.getSourceRoots(type)) {
      for (ExcludedJavaSourceRootProvider provider : excludedRootProviders) {
        if (provider.isExcludedFromCompilation(myModule, sourceRoot)) {
          continue roots_loop;
        }
      }
      String packagePrefix = sourceRoot.getProperties().getData().getPackagePrefix();
      roots.add(new JavaSourceRootDescriptor(sourceRoot.getFile(), this, false, false, packagePrefix));
    }
    return roots;
  }

  @Override
  public JavaSourceRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    List<JavaSourceRootDescriptor> descriptors = rootIndex.getRootDescriptors(new File(rootId), Collections.<JavaModuleBuildTargetType>singletonList(myTargetType), null);
    return ContainerUtil.getFirstItem(descriptors);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Module '" + myModuleName + "' " + (myTargetType.isTests() ? "tests" : "production");
  }
}
