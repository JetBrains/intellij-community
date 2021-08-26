// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.kotlin.KotlinBinaries
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.util.JpsPathUtil

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.BiFunction
import java.util.function.Supplier
import java.util.stream.Collectors

@CompileStatic
final class BuildContextImpl extends BuildContext {
  private final JpsGlobal global
  private final CompilationContextImpl compilationContext

  // thread-safe - forkForParallelTask pass it to child context
  private final ConcurrentLinkedQueue<Pair<Path, String>> distFiles

  @Override
  String getFullBuildNumber() {
    return "$applicationInfo.productCode-$buildNumber"
  }

  @Override
  String getSystemSelector() {
    return productProperties.getSystemSelector(applicationInfo, buildNumber)
  }

  static BuildContextImpl create(String communityHome, String projectHome, ProductProperties productProperties,
                                 ProprietaryBuildTools proprietaryBuildTools, BuildOptions options) {
    WindowsDistributionCustomizer windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHome)
    LinuxDistributionCustomizer linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHome)
    MacDistributionCustomizer macDistributionCustomizer = productProperties.createMacCustomizer(projectHome)

    def compilationContext = CompilationContextImpl.create(communityHome, projectHome,
                                                           createBuildOutputRootEvaluator(projectHome, productProperties), options)
    return new BuildContextImpl(compilationContext, productProperties,
                                windowsDistributionCustomizer, linuxDistributionCustomizer, macDistributionCustomizer,
                                proprietaryBuildTools, new ConcurrentLinkedQueue<>())
  }

  private BuildContextImpl(CompilationContextImpl compilationContext, ProductProperties productProperties,
                           WindowsDistributionCustomizer windowsDistributionCustomizer,
                           LinuxDistributionCustomizer linuxDistributionCustomizer,
                           MacDistributionCustomizer macDistributionCustomizer,
                           ProprietaryBuildTools proprietaryBuildTools,
                           @NotNull ConcurrentLinkedQueue<Pair<Path, String>> distFiles) {
    this.compilationContext = compilationContext
    this.global = compilationContext.global
    this.productProperties = productProperties
    this.distFiles = distFiles
    this.proprietaryBuildTools = proprietaryBuildTools == null ? ProprietaryBuildTools.DUMMY : proprietaryBuildTools
    this.windowsDistributionCustomizer = windowsDistributionCustomizer
    this.linuxDistributionCustomizer = linuxDistributionCustomizer
    this.macDistributionCustomizer = macDistributionCustomizer

    bundledJreManager = new BundledJreManager(this)

    buildNumber = options.buildNumber ?: readSnapshotBuildNumber(paths.communityHomeDir)

    bootClassPathJarNames = List.of("bootstrap.jar", "util.jar", "jna.jar")
    dependenciesProperties = new DependenciesProperties(this)
    applicationInfo = new ApplicationInfoProperties(project, productProperties, messages)
    applicationInfo = applicationInfo.patch(this)
    if (productProperties.productCode == null && applicationInfo.productCode != null) {
      productProperties.productCode = applicationInfo.productCode
    }

    if (systemSelector.contains(" ")) {
      messages.error("System selector must not contain spaces: " + systemSelector)
    }

    options.buildStepsToSkip.addAll(productProperties.incompatibleBuildSteps)
    messages.info("Build steps to be skipped: ${options.buildStepsToSkip.join(',')}")
  }

  @Override
  void addDistFile(@NotNull Pair<Path, String> file) {
    messages.debug("$file requested to be added to app resources")
    distFiles.add(file)
  }

  @NotNull Collection<Pair<Path, String>> getDistFiles() {
    return List.copyOf(distFiles)
  }

  static String readSnapshotBuildNumber(Path communityHome) {
    return Files.readString(communityHome.resolve("build.txt")).trim()
  }

  private static BiFunction<JpsProject, BuildMessages, String> createBuildOutputRootEvaluator(String projectHome,
                                                                                              ProductProperties productProperties) {
    return { JpsProject project, BuildMessages messages ->
      ApplicationInfoProperties applicationInfo = new ApplicationInfoProperties(project, productProperties, messages)
      return "$projectHome/out/${productProperties.getOutputDirectoryName(applicationInfo)}"
    } as BiFunction<JpsProject, BuildMessages, String>
  }

  @Override
  JpsModule findApplicationInfoModule() {
    return findRequiredModule(productProperties.applicationInfoModule)
  }

  @Override
  AntBuilder getAnt() {
    compilationContext.ant
  }

  @Override
  GradleRunner getGradle() {
    compilationContext.gradle
  }

  @Override
  BuildOptions getOptions() {
    compilationContext.options
  }

  @Override
  BuildMessages getMessages() {
    compilationContext.messages
  }

  @Override
  BuildPaths getPaths() {
    compilationContext.paths
  }

  @Override
  JpsProject getProject() {
    compilationContext.project
  }

  @Override
  JpsModel getProjectModel() {
    compilationContext.projectModel
  }

  @Override
  JpsCompilationData getCompilationData() {
    compilationContext.compilationData
  }

  @Override
  File getProjectOutputDirectory() {
    return compilationContext.projectOutputDirectory
  }

  @Override
  JpsModule findRequiredModule(String name) {
    return compilationContext.findRequiredModule(name)
  }

  JpsModule findModule(String name) {
    return compilationContext.findModule(name)
  }

  @Override
  String getOldModuleName(String newName) {
    return compilationContext.getOldModuleName(newName)
  }

  @Override
  String getModuleOutputPath(JpsModule module) {
    return compilationContext.getModuleOutputPath(module)
  }

  @Override
  String getModuleTestsOutputPath(JpsModule module) {
    return compilationContext.getModuleTestsOutputPath(module)
  }

  @Override
  List<String> getModuleRuntimeClasspath(JpsModule module, boolean forTests) {
    return compilationContext.getModuleRuntimeClasspath(module, forTests)
  }

  @Override
  void notifyArtifactBuilt(String artifactPath) {
    compilationContext.notifyArtifactBuilt(artifactPath)
  }

  @Override
  void notifyArtifactBuilt(Path artifactPath) {
    compilationContext.notifyArtifactWasBuilt(artifactPath)
  }

  @Override
  void notifyArtifactWasBuilt(Path artifactPath) {
    compilationContext.notifyArtifactWasBuilt(artifactPath)
  }

  @Override
  @Nullable Path findFileInModuleSources(String moduleName, String relativePath) {
    for (Pair<Path, String> info : getSourceRootsWithPrefixes(findRequiredModule(moduleName)) ) {
      if (relativePath.startsWith(info.second)) {
        Path result = info.first.resolve(StringUtil.trimStart(StringUtil.trimStart(relativePath, info.second), "/"))
        if (Files.exists(result)) {
          return result
        }
      }
    }
    return null
  }

  private static @NotNull List<Pair<Path, String>> getSourceRootsWithPrefixes(JpsModule module) {
    return module.sourceRoots
      .stream()
      .filter({ JavaModuleSourceRootTypes.PRODUCTION.contains(it.rootType) })
      .map({ JpsModuleSourceRoot moduleSourceRoot ->
        String prefix
        JpsElement properties = moduleSourceRoot.properties
        if (properties instanceof JavaSourceRootProperties) {
          prefix = ((JavaSourceRootProperties)properties).packagePrefix.replace(".", "/")
        }
        else {
          prefix = ((JavaResourceRootProperties)properties).relativeOutputPath
        }
        if (!prefix.endsWith("/")) {
          prefix += "/"
        }
        return new Pair<>(Paths.get(JpsPathUtil.urlToPath(moduleSourceRoot.getUrl())), StringUtil.trimStart(prefix, "/"))
      })
      .collect(Collectors.toList())
  }

  @Override
  void signFile(String path, Map<String, String> options) {
    if (proprietaryBuildTools.signTool != null) {
      proprietaryBuildTools.signTool.signFile(path, this, options)
      messages.info("Signed $path")
    }
    else {
      messages.warning("Sign tool isn't defined, $path won't be signed")
    }
  }

  @Override
  boolean executeStep(String stepMessage, String stepId, Runnable step) {
    if (options.buildStepsToSkip.contains(stepId)) {
      messages.info("Skipping '$stepMessage'")
    }
    else {
      messages.block(stepMessage, new Supplier<Void>() {
        @Override
        Void get() {
          step.run()
          return null
        }
      })
    }
    return true
  }

  @Override
  boolean shouldBuildDistributions() {
    options.targetOS.toLowerCase() != BuildOptions.OS_NONE
  }

  @Override
  boolean shouldBuildDistributionForOS(String os) {
    shouldBuildDistributions() && options.targetOS.toLowerCase() in [BuildOptions.OS_ALL, os]
  }

  @Override
  BuildContext forkForParallelTask(String taskName) {
    def ant = new AntBuilder(ant.project)
    def messages = messages.forkForParallelTask(taskName)
    def compilationContextCopy =
      compilationContext.createCopy(ant, messages, options, createBuildOutputRootEvaluator(compilationContext.paths.projectHome, productProperties))
    def copy = new BuildContextImpl(compilationContextCopy, productProperties,
                                    windowsDistributionCustomizer, linuxDistributionCustomizer, macDistributionCustomizer,
                                    proprietaryBuildTools, distFiles)
    copy.paths.artifacts = paths.artifacts
    return copy
  }

  @Override
  BuildContext createCopyForProduct(ProductProperties productProperties, String projectHomeForCustomizers) {
    WindowsDistributionCustomizer windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeForCustomizers)
    LinuxDistributionCustomizer linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeForCustomizers)
    MacDistributionCustomizer macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeForCustomizers)
    /**
     * FIXME compiled classes are assumed to be already fetched in the FIXME from {@link org.jetbrains.intellij.build.impl.CompilationContextImpl#prepareForBuild}, please change them together
     */
    def options = new BuildOptions()
    options.useCompiledClassesFromProjectOutput = true
    def compilationContextCopy =
      compilationContext.createCopy(ant, messages, options, createBuildOutputRootEvaluator(paths.projectHome, productProperties))
    def copy = new BuildContextImpl(compilationContextCopy, productProperties,
                                    windowsDistributionCustomizer, linuxDistributionCustomizer, macDistributionCustomizer,
                                    proprietaryBuildTools, new ConcurrentLinkedQueue<>())
    copy.paths.artifacts = paths.artifacts
    copy.compilationContext.prepareForBuild()
    return copy
  }

  @Override
  boolean includeBreakGenLibraries() {
    return isJavaSupportedInProduct()
  }

  private boolean isJavaSupportedInProduct() {
    return productProperties.productLayout.bundledPluginModules.contains("intellij.java.plugin")
  }

  @Override
  void patchInspectScript(@NotNull Path path) {
    //todo[nik] use placeholder in inspect.sh/inspect.bat file instead
    Files.writeString(path, Files.readString(path).replaceAll(" inspect ", " ${productProperties.inspectCommandName} "))
  }

  @Override
  @SuppressWarnings('SpellCheckingInspection')
  @NotNull List<String> getAdditionalJvmArguments() {
    List<String> jvmArgs = new ArrayList<>()

    def classLoader = productProperties.classLoader
    if (classLoader != null) {
      jvmArgs.add('-Djava.system.class.loader=' + classLoader)
    }

    jvmArgs.add('-Didea.vendor.name=' + applicationInfo.shortCompanyName)

    jvmArgs.add('-Didea.paths.selector=' + systemSelector)

    if (productProperties.platformPrefix != null) {
      jvmArgs.add('-Didea.platform.prefix=' + productProperties.platformPrefix)
    }

    jvmArgs.addAll(productProperties.additionalIdeJvmArguments)

    if (productProperties.toolsJarRequired) {
      jvmArgs.add('-Didea.jre.check=true')
    }

    if (productProperties.useSplash) {
      //noinspection SpellCheckingInspection
      jvmArgs.add('-Dsplash=true')
    }
    return jvmArgs
  }
}
