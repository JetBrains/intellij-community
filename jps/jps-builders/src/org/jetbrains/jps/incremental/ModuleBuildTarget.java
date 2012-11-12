package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetRegistry;
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsSimpleElement;
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
    final ProcessorConfigProfile profile = context.getAnnotationProcessingProfile(getModule());
    if (profile.isEnabled()) {
      final File annotationOut = context.getProjectPaths().getAnnotationProcessorGeneratedSourcesOutputDir(getModule(), isTests(), profile);
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
    final Set<File> moduleExcludes = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    moduleExcludes.addAll(index.getModuleExcludes(myModule));

    roots_loop:
    for (JpsTypedModuleSourceRoot<JpsSimpleElement<JavaSourceRootProperties>> sourceRoot : myModule.getSourceRoots(type)) {
      for (ExcludedJavaSourceRootProvider provider : excludedRootProviders) {
        if (provider.isExcludedFromCompilation(myModule, sourceRoot) || JpsPathUtil.isUnder(moduleExcludes, sourceRoot.getFile())) {
          continue roots_loop;
        }
      }
      final String packagePrefix = sourceRoot.getProperties().getData().getPackagePrefix();
      roots.add(new JavaSourceRootDescriptor(sourceRoot.getFile(), this, false, false, packagePrefix, computeRootExcludes(sourceRoot.getFile(), index)));
    }
    return roots;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Module '" + getModule().getName() + "' " + (myTargetType.isTests() ? "tests" : "production");
  }

  @Override
  public void writeConfiguration(PrintWriter out, BuildDataPaths dataPaths, BuildRootIndex buildRootIndex) {
    final JpsModule module = getModule();

    int fingerprint = getDependenciesFingerprint();

    final LanguageLevel level = JpsJavaExtensionService.getInstance().getLanguageLevel(module);
    if (level != null) {
      fingerprint += level.name().hashCode();
    }

    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(module.getProject());
    final String bytecodeTarget = config.getByteCodeTargetLevel(module.getName());
    if (bytecodeTarget != null) {
      fingerprint += bytecodeTarget.hashCode();
    }

    out.write(Integer.toHexString(fingerprint));
  }

  private int getDependenciesFingerprint() {
    final JpsModule module = getModule();

    int fingerprint = 0;

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
