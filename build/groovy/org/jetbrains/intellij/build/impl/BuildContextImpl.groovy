/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.*
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.util.JpsPathUtil

/**
 * @author nik
 */
@CompileStatic
class BuildContextImpl extends BuildContext {
  private final JpsGlobal global

  static BuildContextImpl create(AntBuilder ant, JpsGantProjectBuilder projectBuilder, JpsProject project, JpsGlobal global,
                                 String communityHome, String projectHome, ProductProperties productProperties,
                                 ProprietaryBuildTools proprietaryBuildTools, BuildOptions options) {
    BuildMessages messages = BuildMessagesImpl.create(projectBuilder, ant.project)
    communityHome = toCanonicalPath(communityHome)
    projectHome = toCanonicalPath(projectHome)
    def jdk8Home = toCanonicalPath(JdkUtils.computeJdkHome(messages, "jdk8Home", "$projectHome/build/jdk/1.8", "JDK_18_x64"))

    WindowsDistributionCustomizer windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHome)
    LinuxDistributionCustomizer linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHome)
    MacDistributionCustomizer macDistributionCustomizer = productProperties.createMacCustomizer(projectHome)

    if (project.modules.isEmpty()) {
      loadProject(communityHome, projectHome, jdk8Home, project, global, messages)
    }
    else {
      //todo[nik] currently we need this to build IDEA CE from IDEA UI build scripts. It would be better to create a separate JpsProject instance instead
      messages.info("Skipping loading project because it's already loaded")
    }

    def context = new BuildContextImpl(ant, messages, communityHome, projectHome, jdk8Home, project, global, projectBuilder, productProperties,
                                       windowsDistributionCustomizer, linuxDistributionCustomizer, macDistributionCustomizer,
                                       proprietaryBuildTools, options)
    context.prepareForBuild()
    return context
  }

  private BuildContextImpl(AntBuilder ant, BuildMessages messages, String communityHome, String projectHome, String jdk8Home,
                           JpsProject project, JpsGlobal global, JpsGantProjectBuilder projectBuilder, ProductProperties productProperties,
                           WindowsDistributionCustomizer windowsDistributionCustomizer,
                           LinuxDistributionCustomizer linuxDistributionCustomizer,
                           MacDistributionCustomizer macDistributionCustomizer,
                           ProprietaryBuildTools proprietaryBuildTools,
                           BuildOptions options) {
    this.ant = ant
    this.messages = messages
    this.project = project
    this.global = global
    this.projectBuilder = projectBuilder
    this.productProperties = productProperties
    this.proprietaryBuildTools = proprietaryBuildTools
    this.options = options
    this.windowsDistributionCustomizer = windowsDistributionCustomizer
    this.linuxDistributionCustomizer = linuxDistributionCustomizer
    this.macDistributionCustomizer = macDistributionCustomizer

    def appInfoFile = findApplicationInfoInSources()
    applicationInfo = new ApplicationInfoProperties(appInfoFile.absolutePath)
    String buildOutputRoot = options.outputRootPath ?: "$projectHome/out/${productProperties.outputDirectoryName(applicationInfo)}"
    paths = new BuildPathsImpl(communityHome, projectHome, buildOutputRoot, jdk8Home)
    bundledJreManager = new BundledJreManager(this, paths.buildOutputRoot)

    buildNumber = options.buildNumber ?: readSnapshotBuildNumber()
    fullBuildNumber = "$productProperties.productCode-$buildNumber"
    systemSelector = productProperties.systemSelector(applicationInfo)

    bootClassPathJarNames = ["bootstrap.jar", "extensions.jar", "util.jar", "jdom.jar", "log4j.jar", "trove4j.jar", "jna.jar"]
  }

  private static void loadProject(String communityHome, String projectHome, String jdkHome, JpsProject project, JpsGlobal global,
                                  BuildMessages messages) {
    def bundledKotlinPath = "$communityHome/build/kotlinc"
    if (!new File(bundledKotlinPath, "lib/kotlin-runtime.jar").exists()) {
      messages.error("Could not find Kotlin runtime at $bundledKotlinPath/lib/kotlin-runtime.jar: run download_kotlin.gant script to download Kotlin JARs")
    }
    JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(global).addPathVariable("KOTLIN_BUNDLED", bundledKotlinPath)

    JdkUtils.defineJdk(global, "IDEA jdk", JdkUtils.computeJdkHome(messages, "jdkHome", "$projectHome/build/jdk/1.6", "JDK_16_x64"))
    JdkUtils.defineJdk(global, "1.8", jdkHome)

    def pathVariables = JpsModelSerializationDataService.computeAllPathVariables(global)
    JpsProjectLoader.loadProject(project, pathVariables, projectHome)
    messages.info("Loaded project $projectHome: ${project.modules.size()} modules, ${project.libraryCollection.libraries.size()} libraries")
  }

  private void prepareForBuild() {
    checkCompilationOptions()
    projectBuilder.buildIncrementally = options.incrementalCompilation
    def dataDirName = options.incrementalCompilation ? ".jps-build-data-incremental" : ".jps-build-data"
    projectBuilder.dataStorageRoot = new File(paths.buildOutputRoot, dataDirName)
    def logDir = new File(paths.buildOutputRoot, "log")
    FileUtil.delete(logDir)
    projectBuilder.setupAdditionalLogging(new File("$logDir/compilation.log"), System.getProperty("intellij.build.debug.logging.categories", ""))

    def classesDirName = "classes"
    def classesOutput = "$paths.buildOutputRoot/$classesDirName"
    List<String> outputDirectoriesToKeep = ["log"]
    if (options.pathToCompiledClassesArchive != null) {
      unpackCompiledClasses(messages, ant, classesOutput, options)
      outputDirectoriesToKeep.add(classesDirName)
    }
    if (options.incrementalCompilation) {
      outputDirectoriesToKeep.add(dataDirName)
      outputDirectoriesToKeep.add(classesDirName)
    }
    if (!options.useCompiledClassesFromProjectOutput) {
      projectBuilder.targetFolder = classesOutput
    }
    else {
      def outputDir = JpsPathUtil.urlToFile(JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl)
      if (!outputDir.exists()) {
        messages.error("$BuildOptions.USE_COMPILED_CLASSES_PROPERTY is enabled, but the project output directory $outputDir.absolutePath doesn't exist")
      }
    }

    suppressWarnings(project)
    projectBuilder.exportModuleOutputProperties()
    cleanOutput(outputDirectoriesToKeep)
  }

  void cleanOutput(List<String> outputDirectoriesToKeep) {
    messages.block("Clean output") {
      def outputPath = paths.buildOutputRoot
      messages.progress("Cleaning output directory $outputPath")
      new File(outputPath).listFiles()?.each { File file ->
        if (outputDirectoriesToKeep.contains(file.name)) {
          messages.info("Skipped cleaning for $file.absolutePath")
        }
        else {
          FileUtil.delete(file)
        }
      }
    }
  }

  @CompileDynamic
  private static void unpackCompiledClasses(BuildMessages messages, AntBuilder ant, String classesOutput, BuildOptions options) {
    messages.block("Unpack compiled classes archive") {
      FileUtil.delete(new File(classesOutput))
      ant.unzip(src: options.pathToCompiledClassesArchive, dest: classesOutput)
    }
  }

  private void checkCompilationOptions() {
    if (options.useCompiledClassesFromProjectOutput && options.incrementalCompilation) {
      messages.warning("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && options.incrementalCompilation) {
      messages.warning("Paths to the compiled project output is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && options.useCompiledClassesFromProjectOutput) {
      messages.warning("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, so the archive with compiled project output won't be used")
      options.pathToCompiledClassesArchive = null
    }
  }

  private static void suppressWarnings(JpsProject project) {
    def compilerOptions = JpsJavaExtensionService.instance.getOrCreateCompilerConfiguration(project).currentCompilerOptions
    compilerOptions.GENERATE_NO_WARNINGS = true
    compilerOptions.DEPRECATION = false
    compilerOptions.ADDITIONAL_OPTIONS_STRING = compilerOptions.ADDITIONAL_OPTIONS_STRING.replace("-Xlint:unchecked", "")
  }

  private String readSnapshotBuildNumber() {
    new File(paths.communityHome, "build.txt").text.trim()
  }

  @Override
  File findApplicationInfoInSources() {
    JpsModule module = findApplicationInfoModule()
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
  JpsModule findRequiredModule(String name) {
    def module = findModule(name)
    if (module == null) {
      messages.error("Cannot find required module '$name' in the project")
    }
    return module
  }

  JpsModule findModule(String name) {
    project.modules.find { it.name == name }
  }

  @Override
  File findFileInModuleSources(String moduleName, String relativePath) {
    getSourceRootsWithPrefixes(findRequiredModule(moduleName)).collect {
      new File(it.first, "${StringUtil.trimStart(relativePath, it.second)}")
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
  boolean shouldBuildDistributionForOS(String os) {
    options.targetOS.toLowerCase() in [BuildOptions.OS_ALL, os]
  }

  @Override
  BuildContext forkForParallelTask(String taskName) {
    def ant = new AntBuilder(ant.project)
    def messages = messages.forkForParallelTask(taskName)
    def child = new BuildContextImpl(ant, messages, paths.communityHome, paths.projectHome, paths.jdkHome, project, global, projectBuilder, productProperties,
                                     windowsDistributionCustomizer, linuxDistributionCustomizer, macDistributionCustomizer,
                                     proprietaryBuildTools, options)
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
    def copy = new BuildContextImpl(ant, messages, paths.communityHome, paths.projectHome, paths.jdkHome, project, global, projectBuilder, productProperties,
                                    windowsDistributionCustomizer, linuxDistributionCustomizer, macDistributionCustomizer, proprietaryBuildTools, options)
    copy.paths.artifacts = paths.artifacts
    copy.bundledJreManager.baseDirectoryForJre = bundledJreManager.baseDirectoryForJre
    copy.prepareForBuild()
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
    return productLayout.mainJarName == null ||
           //todo[nik] remove this condition later; currently build scripts for IDEA don't fully migrated to the new scheme
           productLayout.includedPlatformModules.contains("execution-impl")
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
    jvmArgs += " $productProperties.additionalIdeJvmArguments".trim()
    if (productProperties.toolsJarRequired) {
      jvmArgs += " -Didea.jre.check=true"
    }
    return jvmArgs.trim()
  }

  @Override
  void notifyArtifactBuilt(String artifactPath) {
    def file = new File(artifactPath)
    def baseDir = new File(paths.projectHome)
    if (!FileUtil.isAncestor(baseDir, file, true)) {
      messages.warning("Artifact '$artifactPath' is not under '$paths.projectHome', it won't be reported")
      return
    }
    def relativePath = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(baseDir, file))
    if (file.isDirectory()) {
      relativePath += "=>" + file.name
    }
    messages.artifactBuild(relativePath)
  }

  private static String toCanonicalPath(String communityHome) {
    FileUtil.toSystemIndependentName(new File(communityHome).canonicalPath)
  }
}

class BuildPathsImpl extends BuildPaths {
  BuildPathsImpl(String communityHome, String projectHome, String buildOutputRoot, String jdkHome) {
    this.communityHome = communityHome
    this.projectHome = projectHome
    this.buildOutputRoot = buildOutputRoot
    this.jdkHome = jdkHome
    artifacts = "$buildOutputRoot/artifacts"
    distAll = "$buildOutputRoot/dist.all"
    temp = "$buildOutputRoot/temp"
  }

}
