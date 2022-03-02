// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.Strings
import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Scope
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

import java.lang.reflect.UndeclaredThrowableException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors

@CompileStatic
final class BuildContextImpl extends BuildContext {
  final ApplicationInfoProperties applicationInfo

  private final JpsGlobal global
  private final CompilationContextImpl compilationContext

  // thread-safe - forkForParallelTask pass it to child context
  private final ConcurrentLinkedQueue<Map.Entry<Path, String>> distFiles

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
                                                           createBuildOutputRootEvaluator(projectHome, productProperties, options), options)

    return new BuildContextImpl(compilationContext, productProperties,
                                windowsDistributionCustomizer, linuxDistributionCustomizer, macDistributionCustomizer,
                                proprietaryBuildTools, new ConcurrentLinkedQueue<>())
  }

  private BuildContextImpl(CompilationContextImpl compilationContext, ProductProperties productProperties,
                           WindowsDistributionCustomizer windowsDistributionCustomizer,
                           LinuxDistributionCustomizer linuxDistributionCustomizer,
                           MacDistributionCustomizer macDistributionCustomizer,
                           ProprietaryBuildTools proprietaryBuildTools,
                           @NotNull ConcurrentLinkedQueue<Map.Entry<Path, String>> distFiles) {
    this.compilationContext = compilationContext
    this.global = compilationContext.global
    this.productProperties = productProperties
    this.distFiles = distFiles
    this.proprietaryBuildTools = proprietaryBuildTools == null ? ProprietaryBuildTools.DUMMY : proprietaryBuildTools
    this.windowsDistributionCustomizer = windowsDistributionCustomizer
    this.linuxDistributionCustomizer = linuxDistributionCustomizer
    this.macDistributionCustomizer = macDistributionCustomizer

    buildNumber = options.buildNumber ?: readSnapshotBuildNumber(paths.communityHomeDir)

    xBootClassPathJarNames = productProperties.xBootClassPathJarNames
    bootClassPathJarNames = List.of("util.jar")
    applicationInfo = new ApplicationInfoProperties(project, productProperties, options, messages).patch(this)
    if (productProperties.productCode == null && applicationInfo.productCode != null) {
      productProperties.productCode = applicationInfo.productCode
    }

    if (systemSelector.contains(" ")) {
      messages.error("System selector must not contain spaces: " + systemSelector)
    }

    options.buildStepsToSkip.addAll(productProperties.incompatibleBuildSteps)
    if (!options.buildStepsToSkip.isEmpty()) {
      messages.info("Build steps to be skipped: ${String.join(", ", options.buildStepsToSkip)}")
    }
  }

  private BuildContextImpl(@NotNull BuildContextImpl parent,
                           @NotNull BuildMessages messages,
                           @NotNull ConcurrentLinkedQueue<Map.Entry<Path, String>> distFiles) {
    compilationContext = parent.compilationContext.cloneForContext(messages)
    this.distFiles = distFiles
    global = compilationContext.global
    productProperties = parent.productProperties
    proprietaryBuildTools = parent.proprietaryBuildTools
    windowsDistributionCustomizer = parent.windowsDistributionCustomizer
    linuxDistributionCustomizer = parent.linuxDistributionCustomizer
    macDistributionCustomizer = parent.macDistributionCustomizer

    buildNumber = parent.buildNumber

    xBootClassPathJarNames = parent.xBootClassPathJarNames
    bootClassPathJarNames = parent.bootClassPathJarNames
    applicationInfo = parent.applicationInfo
  }

  @Override
  void addDistFile(@NotNull Map.Entry<Path, String> file) {
    messages.debug("$file requested to be added to app resources")
    distFiles.add(file)
  }

  @NotNull Collection<Map.Entry<Path, String>> getDistFiles() {
    return List.copyOf(distFiles)
  }

  static String readSnapshotBuildNumber(Path communityHome) {
    return Files.readString(communityHome.resolve("build.txt")).trim()
  }

  private static BiFunction<JpsProject, BuildMessages, String> createBuildOutputRootEvaluator(String projectHome,
                                                                                              ProductProperties productProperties,
                                                                                              BuildOptions buildOptions) {
    return { JpsProject project, BuildMessages messages ->
      ApplicationInfoProperties applicationInfo = new ApplicationInfoProperties(project, productProperties, buildOptions, messages)
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
  BuildOptions getOptions() {
    compilationContext.options
  }

  @Override
  BuildMessages getMessages() {
    compilationContext.messages
  }

  @Override
  DependenciesProperties getDependenciesProperties() {
    compilationContext.dependenciesProperties
  }

  @Override
  BuildPaths getPaths() {
    compilationContext.paths
  }

  @Override
  BundledRuntime getBundledRuntime() {
    compilationContext.bundledRuntime
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
  Path getModuleOutputDir(JpsModule module) {
    return compilationContext.getModuleOutputDir(module)
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
  @Nullable Path findFileInModuleSources(@NotNull String moduleName, @NotNull String relativePath) {
    for (Pair<Path, String> info : getSourceRootsWithPrefixes(findRequiredModule(moduleName))) {
      if (relativePath.startsWith(info.second)) {
        Path result = info.first.resolve(Strings.trimStart(Strings.trimStart(relativePath, info.second), "/"))
        if (Files.exists(result)) {
          return result
        }
      }
    }
    return null
  }

  @NotNull
  private static List<Pair<Path, String>> getSourceRootsWithPrefixes(@NotNull JpsModule module) {
    return module.sourceRoots
      .stream()
      .filter(new Predicate<JpsModuleSourceRoot>() {
        @Override
        boolean test(JpsModuleSourceRoot root) {
          return JavaModuleSourceRootTypes.PRODUCTION.contains(root.rootType)
        }
      })
      .map(new Function<JpsModuleSourceRoot, Pair<Path, String>>() {
        @Override
        Pair<Path, String> apply(JpsModuleSourceRoot moduleSourceRoot) {
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
          return new Pair<>(Path.of(JpsPathUtil.urlToPath(moduleSourceRoot.getUrl())), Strings.trimStart(prefix, "/"))
        }
      })
      .collect(Collectors.toList())
  }

  @Override
  void signFiles(@NotNull List<Path> files, Map<String, String> options) {
    if (proprietaryBuildTools.signTool == null) {
      messages.warning("Sign tool isn't defined, $files won't be signed")
    }
    else {
      proprietaryBuildTools.signTool.signFiles(files, this, options)
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
  void executeStep(SpanBuilder spanBuilder, String stepId, Runnable step) {
    if (options.buildStepsToSkip.contains(stepId)) {
      spanBuilder.startSpan().addEvent("skip").end()
      return
    }

    Span span = spanBuilder.startSpan()
    Scope scope = span.makeCurrent()
    // we cannot flush tracing after "throw e" as we have to end the current span before that
    boolean success = false
    try {
      step.run()
      success = true
    }
    catch (Throwable e) {
      if (e instanceof UndeclaredThrowableException) {
        e = e.cause
      }

      span.recordException(e)
      span.setStatus(StatusCode.ERROR, e.message)
      throw e
    }
    finally {
      try {
        scope.close()
      }
      finally {
        span.end()
      }

      if (!success) {
        // print all pending spans - after current span
        TracerProviderManager.flush()
      }
    }
  }

  @Override
  boolean shouldBuildDistributions() {
    return options.targetOS.toLowerCase() != BuildOptions.OS_NONE
  }

  @Override
  boolean shouldBuildDistributionForOS(String os) {
    return shouldBuildDistributions() && options.targetOS.toLowerCase() in [BuildOptions.OS_ALL, os]
  }

  @Override
  BuildContext forkForParallelTask(String taskName) {
    return new BuildContextImpl(this, messages.forkForParallelTask(taskName), distFiles)
  }

  @Override
  BuildContext createCopyForProduct(ProductProperties productProperties, String projectHomeForCustomizers) {
    WindowsDistributionCustomizer windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeForCustomizers)
    LinuxDistributionCustomizer linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeForCustomizers)
    MacDistributionCustomizer macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeForCustomizers)
    /**
     * FIXME compiled classes are assumed to be already fetched in the FIXME from {@link org.jetbrains.intellij.build.impl.CompilationContextImpl#prepareForBuild}, please change them together
     */
    BuildOptions options = new BuildOptions()
    options.useCompiledClassesFromProjectOutput = true
    CompilationContextImpl compilationContextCopy = compilationContext
      .createCopy(ant, messages, options, createBuildOutputRootEvaluator(paths.projectHome, productProperties, options))
    BuildContextImpl copy = new BuildContextImpl(compilationContextCopy, productProperties,
                                                 windowsDistributionCustomizer, linuxDistributionCustomizer, macDistributionCustomizer,
                                                 proprietaryBuildTools, new ConcurrentLinkedQueue<>())
    copy.paths.artifactDir = paths.artifactDir.resolve(productProperties.productCode)
    copy.paths.artifacts = paths.artifacts + "/" + productProperties.productCode
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
  @SuppressWarnings("SpellCheckingInspection")
  @NotNull List<String> getAdditionalJvmArguments() {
    List<String> jvmArgs = new ArrayList<>()

    String classLoader = productProperties.classLoader
    if (classLoader != null) {
      jvmArgs.add("-Djava.system.class.loader=" + classLoader)
      if (classLoader == "com.intellij.util.lang.PathClassLoader") {
        jvmArgs.add("-Didea.strict.classpath=true")
      }
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

    if (options.bundledRuntimeVersion >= 17) {
      jvmArgs.addAll(OpenedPackages.getCommandLineArguments(this))
    }

    return jvmArgs
  }
}
