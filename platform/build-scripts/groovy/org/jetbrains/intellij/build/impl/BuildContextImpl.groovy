// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.*
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

    applicationInfo = new ApplicationInfoProperties(findApplicationInfoInSources(project, productProperties, messages))
    if (productProperties.customProductCode != null) {
      applicationInfo.productCode = productProperties.customProductCode
    }
    else if (productProperties.productCode != null && applicationInfo.productCode == null) {
      applicationInfo.productCode = productProperties.productCode
    }
    else if (productProperties.productCode == null && applicationInfo.productCode != null) {
      productProperties.productCode = applicationInfo.productCode
    }

    bundledJreManager = new BundledJreManager(this)

    buildNumber = options.buildNumber ?: readSnapshotBuildNumber()
    fullBuildNumber = "$applicationInfo.productCode-$buildNumber"
    systemSelector = productProperties.getSystemSelector(applicationInfo, buildNumber)

    bootClassPathJarNames = List.of("bootstrap.jar", "util.jar", "jdom.jar", "log4j.jar", "jna.jar")
    dependenciesProperties = new DependenciesProperties(this)
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

  private String readSnapshotBuildNumber() {
    return Files.readString(paths.communityHomeDir.resolve("build.txt")).trim()
  }

  private static BiFunction<JpsProject, BuildMessages, String> createBuildOutputRootEvaluator(String projectHome,
                                                                                              ProductProperties productProperties) {
    return { JpsProject project, BuildMessages messages ->
      ApplicationInfoProperties applicationInfo = new ApplicationInfoProperties(findApplicationInfoInSources(project, productProperties, messages))
      return "$projectHome/out/${productProperties.getOutputDirectoryName(applicationInfo)}"
    } as BiFunction<JpsProject, BuildMessages, String>
  }

  static @NotNull Path findApplicationInfoInSources(JpsProject project, ProductProperties productProperties, BuildMessages messages) {
    JpsModule module = project.modules.find { it.name == productProperties.applicationInfoModule }
    if (module == null) {
      messages.error("Cannot find required '${productProperties.applicationInfoModule}' module")
    }
    def appInfoRelativePath = "idea/${productProperties.platformPrefix ?: ""}ApplicationInfo.xml"
    def appInfoFile = module.sourceRoots.collect { new File(it.file, appInfoRelativePath) }.find { it.exists() }
    if (appInfoFile == null) {
      messages.error("Cannot find $appInfoRelativePath in '$module.name' module")
      return null
    }
    return appInfoFile.toPath()
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
      Path result = info.first.resolve(StringUtil.trimStart(StringUtil.trimStart(relativePath, info.second), "/"))
      if (Files.exists(result)) {
        return result
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
  void signExeFile(String path) {
    if (proprietaryBuildTools.signTool != null) {
      executeStep("Signing $path", BuildOptions.WIN_SIGN_STEP) {
        proprietaryBuildTools.signTool.signExeFile(path, this)
        messages.info("Signed $path")
      }
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
  String getAdditionalJvmArguments() {
    String jvmArgs
    if (productProperties.platformPrefix != null) {
      jvmArgs = "-Didea.platform.prefix=${productProperties.platformPrefix}"
    }
    else {
      jvmArgs = ""
    }

    String additionalJvmArguments = productProperties.additionalIdeJvmArguments.trim()
    if (!additionalJvmArguments.isEmpty()) {
      jvmArgs += " $additionalJvmArguments"
    }

    if (productProperties.toolsJarRequired) {
      jvmArgs += " -Didea.jre.check=true"
    }
    return jvmArgs.trim()
  }
}
