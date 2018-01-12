/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.*
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.module.JpsModule

import java.util.function.BiFunction
/**
 * @author nik
 */
@CompileStatic
class BuildContextImpl extends BuildContext {
  private final JpsGlobal global
  private final CompilationContextImpl compilationContext

  static BuildContextImpl create(String communityHome, String projectHome, ProductProperties productProperties,
                                 ProprietaryBuildTools proprietaryBuildTools, BuildOptions options) {
    WindowsDistributionCustomizer windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHome)
    LinuxDistributionCustomizer linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHome)
    MacDistributionCustomizer macDistributionCustomizer = productProperties.createMacCustomizer(projectHome)

    def compilationContext = CompilationContextImpl.create(communityHome, projectHome,
                                                           createBuildOutputRootEvaluator(projectHome, productProperties), options)
    def context = new BuildContextImpl(compilationContext, productProperties,
                                       windowsDistributionCustomizer, linuxDistributionCustomizer, macDistributionCustomizer,
                                       proprietaryBuildTools)
    return context
  }

  private BuildContextImpl(CompilationContextImpl compilationContext, ProductProperties productProperties,
                           WindowsDistributionCustomizer windowsDistributionCustomizer,
                           LinuxDistributionCustomizer linuxDistributionCustomizer,
                           MacDistributionCustomizer macDistributionCustomizer,
                           ProprietaryBuildTools proprietaryBuildTools) {
    this.compilationContext = compilationContext
    this.global = compilationContext.global
    this.productProperties = productProperties
    this.proprietaryBuildTools = proprietaryBuildTools
    this.windowsDistributionCustomizer = windowsDistributionCustomizer
    this.linuxDistributionCustomizer = linuxDistributionCustomizer
    this.macDistributionCustomizer = macDistributionCustomizer

    def appInfoFile = findApplicationInfoInSources(project, productProperties, messages)
    applicationInfo = new ApplicationInfoProperties(appInfoFile.absolutePath)
    bundledJreManager = new BundledJreManager(this, paths.buildOutputRoot)

    buildNumber = options.buildNumber ?: readSnapshotBuildNumber()
    fullBuildNumber = "$productProperties.productCode-$buildNumber"
    systemSelector = productProperties.getSystemSelector(applicationInfo)

    bootClassPathJarNames = ["bootstrap.jar", "extensions.jar", "util.jar", "jdom.jar", "log4j.jar", "trove4j.jar", "jna.jar"]
  }

  private String readSnapshotBuildNumber() {
    new File(paths.communityHome, "build.txt").text.trim()
  }

  private static BiFunction<JpsProject, BuildMessages, String> createBuildOutputRootEvaluator(String projectHome, ProductProperties productProperties) {
    return { JpsProject project, BuildMessages messages ->
      def appInfoFile = findApplicationInfoInSources(project, productProperties, messages)
      def applicationInfo = new ApplicationInfoProperties(appInfoFile.absolutePath)
      return "$projectHome/out/${productProperties.getOutputDirectoryName(applicationInfo)}"
    } as BiFunction<JpsProject, BuildMessages, String>
  }

  static File findApplicationInfoInSources(JpsProject project, ProductProperties productProperties, BuildMessages messages) {
    JpsModule module = project.modules.find {it.name == productProperties.applicationInfoModule }
    if (module == null) {
      messages.error("Cannot find required '${productProperties.applicationInfoModule}' module")
    }
    def appInfoRelativePath = "idea/${productProperties.platformPrefix ?: ""}ApplicationInfo.xml"
    def appInfoFile = module.sourceRoots.collect { new File(it.file, appInfoRelativePath) }.find { it.exists() }
    if (appInfoFile == null) {
      messages.error("Cannot find $appInfoRelativePath in '$module.name' module")
    }
    return appInfoFile
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
  JpsGantProjectBuilder getProjectBuilder() {
    compilationContext.projectBuilder
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
  File findFileInModuleSources(String moduleName, String relativePath) {
    getSourceRootsWithPrefixes(findRequiredModule(moduleName)).collect {
      new File(it.first, StringUtil.trimStart(relativePath, it.second))
    }.find {it.exists()}
  }

  @SuppressWarnings(["GrUnresolvedAccess", "GroovyInArgumentCheck"])
  @CompileDynamic
  private static List<Pair<File, String>> getSourceRootsWithPrefixes(JpsModule module) {
    module.sourceRoots.findAll { it.rootType in JavaModuleSourceRootTypes.PRODUCTION }.collect {
      String prefix = it.properties instanceof JavaSourceRootProperties ? it.properties.packagePrefix.replace(".", "/") : it.properties.relativeOutputPath
      if (!prefix.endsWith("/")) prefix += "/"
      Pair.create(it.file, StringUtil.trimStart(prefix, "/"))
    }
  }

  @Override
  void signExeFile(String path) {
    if (proprietaryBuildTools.signTool != null) {
      messages.progress("Signing $path")
      proprietaryBuildTools.signTool.signExeFile(path, this)
      messages.info("Signing done")
    }
    else {
      messages.warning("Sign tool isn't defined, $path won't be signed")
    }
  }

  @Override
  void executeStep(String stepMessage, String stepId, Closure step) {
    if (options.buildStepsToSkip.contains(stepId)) {
      messages.info("Skipping '$stepMessage'")
    }
    else {
      messages.block(stepMessage, step)
    }
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
    def compilationContextCopy = compilationContext.createCopy(ant, messages, options, createBuildOutputRootEvaluator(compilationContext.paths.projectHome, productProperties))
    def child = new BuildContextImpl(compilationContextCopy, productProperties,
                                     windowsDistributionCustomizer, linuxDistributionCustomizer, macDistributionCustomizer,
                                     proprietaryBuildTools)
    child.paths.artifacts = paths.artifacts
    child.bundledJreManager.baseDirectoryForJre = bundledJreManager.baseDirectoryForJre
    return child
  }

  @Override
  BuildContext createCopyForProduct(ProductProperties productProperties, String projectHomeForCustomizers) {
    WindowsDistributionCustomizer windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeForCustomizers)
    LinuxDistributionCustomizer linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeForCustomizers)
    MacDistributionCustomizer macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeForCustomizers)

    def options = new BuildOptions()
    options.useCompiledClassesFromProjectOutput = true
    def compilationContextCopy = compilationContext.createCopy(ant, messages, options, createBuildOutputRootEvaluator(paths.projectHome, productProperties))
    def copy = new BuildContextImpl(compilationContextCopy, productProperties,
                                    windowsDistributionCustomizer, linuxDistributionCustomizer, macDistributionCustomizer, proprietaryBuildTools)
    copy.paths.artifacts = paths.artifacts
    copy.bundledJreManager.baseDirectoryForJre = bundledJreManager.baseDirectoryForJre
    copy.compilationContext.prepareForBuild()
    return copy
  }

  @Override
  boolean includeBreakGenLibraries() {
    return isJavaSupportedInProduct()
  }

  @Override
  boolean shouldIDECopyJarsByDefault() {
    return isJavaSupportedInProduct()
  }

  private boolean isJavaSupportedInProduct() {
    def productLayout = productProperties.productLayout
    return DistributionJARsBuilder.getIncludedPlatformModules(productLayout).contains("execution-impl")
  }

  @CompileDynamic
  @Override
  void patchInspectScript(String path) {
    //todo[nik] use placeholder in inspect.sh/inspect.bat file instead
    ant.replace(file: path) {
      replacefilter(token: " inspect ", value: " ${productProperties.inspectCommandName} ")
    }
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
