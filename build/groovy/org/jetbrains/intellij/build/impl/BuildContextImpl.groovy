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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.codehaus.gant.GantBuilder
import org.jetbrains.intellij.build.*
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.util.JpsPathUtil
/**
 * @author nik
 */
class BuildContextImpl extends BuildContext {
  private final JpsGlobal global
  private final boolean underTeamCity
  final List<String> outputDirectoriesToKeep = []

//todo[nik] construct buildOutputRoot automatically based on product name
  BuildContextImpl(GantBuilder ant, JpsGantProjectBuilder projectBuilder, JpsProject project, JpsGlobal global,
                   String communityHome, String projectHome, String buildOutputRoot, ProductProperties productProperties,
                   BuildOptions options, MacHostProperties macHostProperties, SignTool signTool, ScrambleTool scrambleTool) {
    this.projectBuilder = projectBuilder
    this.ant = ant
    this.project = project
    this.global = global
    this.productProperties = productProperties
    windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHome)
    linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHome)
    macDistributionCustomizer = productProperties.createMacCustomizer(projectHome)
    this.options = options
    this.macHostProperties = macHostProperties
    this.signTool = signTool
    this.scrambleTool = scrambleTool
    underTeamCity = System.getProperty("teamcity.buildType.id") != null
    messages = new BuildMessagesImpl(projectBuilder, ant.project, underTeamCity)

    bundledJreManager = new BundledJreManager(this, buildOutputRoot)
    def jdk8Home = JdkUtils.computeJdkHome(messages, "jdk8Home", "$projectHome/build/jdk/1.8", "JDK_18_x64")
    paths = new BuildPathsImpl(communityHome, projectHome, buildOutputRoot, jdk8Home)

    if (project.modules.isEmpty()) {
      loadProject()
    }
    else {
      //todo[nik] currently we need this to build IDEA CE from IDEA UI build scripts. It would be better to create a separate JpsProject instance instead
      messages.info("Skipping loading project because it's already loaded")
    }
    def appInfoFile = findApplicationInfoInSources()
    applicationInfo = new ApplicationInfoProperties(appInfoFile.absolutePath)

    buildNumber = System.getProperty("build.number") ?: readSnapshotBuildNumber()
    fullBuildNumber = "$productProperties.productCode-$buildNumber"
    systemSelector = productProperties.systemSelector(applicationInfo)

    bootClassPathJarNames = ["bootstrap.jar", "extensions.jar", "util.jar", "jdom.jar", "log4j.jar", "trove4j.jar", "jna.jar"]
  }

  private void loadProject() {
    def projectHome = paths.projectHome
    def bundledKotlinPath = "$paths.communityHome/build/kotlinc"
    if (!new File(bundledKotlinPath, "lib/kotlin-runtime.jar").exists()) {
      messages.error("Could not find Kotlin runtime at $bundledKotlinPath/lib/kotlin-runtime.jar: run download_kotlin.gant script to download Kotlin JARs")
    }
    JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(global).addPathVariable("KOTLIN_BUNDLED", bundledKotlinPath)

    JdkUtils.defineJdk(global, "IDEA jdk", JdkUtils.computeJdkHome(messages, "jdkHome", "$projectHome/build/jdk/1.6", "JDK_16_x64"))
    JdkUtils.defineJdk(global, "1.8", paths.jdkHome)

    checkOptions()
    projectBuilder.buildIncrementally = options.incrementalCompilation
    def dataDirName = options.incrementalCompilation ? ".jps-build-data-incremental" : ".jps-build-data"
    projectBuilder.dataStorageRoot = new File(paths.buildOutputRoot, dataDirName)
    def tempDir = System.getProperty("teamcity.build.tempDir") ?: System.getProperty("java.io.tmpdir")
    projectBuilder.setupAdditionalLogging(new File("$tempDir/system/build-log/build.log"), System.getProperty("intellij.build.debug.logging.categories", ""))

    def pathVariables = JpsModelSerializationDataService.computeAllPathVariables(global)
    JpsProjectLoader.loadProject(project, pathVariables, projectHome)
    projectBuilder.exportModuleOutputProperties()
    messages.info("Loaded project $projectHome: ${project.modules.size()} modules, ${project.libraryCollection.libraries.size()} libraries")

    def classesDirName = "classes"
    def classesOutput = "$paths.buildOutputRoot/$classesDirName"
    if (options.pathToCompiledClassesArchive != null) {
      messages.block("Unpack compiled classes archive") {
        FileUtil.delete(new File(classesOutput))
        ant.unzip(src: options.pathToCompiledClassesArchive, dest: classesOutput)
      }
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

    suppressWarnings()
  }

  private void checkOptions() {
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

  private void suppressWarnings() {
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
  void signExeFile(String path) {
    if (signTool != null) {
      messages.progress("Signing $path")
      signTool.signExeFile(path, this)
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
  boolean includeBreakGenLibraries() {
    def productLayout = productProperties.productLayout
    return productLayout.mainJarName == null || //todo[nik] remove this condition later
           productLayout.additionalPlatformModules.containsKey("java-runtime")
  }

  @Override
  void notifyArtifactBuilt(String artifactPath) {
    if (!underTeamCity) return

    if (!FileUtil.startsWith(FileUtil.toSystemIndependentName(artifactPath), paths.projectHome)) {
      messages.warning("Artifact '$artifactPath' is not under '$paths.projectHome', it won't be reported")
      return
    }
    def relativePath = StringUtil.trimStart(artifactPath.substring(paths.projectHome.length()), "/")
    def file = new File(artifactPath)
    if (file.isDirectory()) {
      relativePath += "=>" + file.name
    }
    messages.info("##teamcity[publishArtifacts '$relativePath']")
  }
}

class BuildPathsImpl extends BuildPaths {
  BuildPathsImpl(String communityHome, String projectHome, String buildOutputRoot, String jdkHome) {
    this.communityHome = toCanonicalPath(communityHome)
    this.projectHome = toCanonicalPath(projectHome)
    this.buildOutputRoot = toCanonicalPath(buildOutputRoot)
    this.jdkHome = toCanonicalPath(jdkHome)
    artifacts = "${this.buildOutputRoot}/artifacts"
    distAll = "${this.buildOutputRoot}/dist.all"
    temp = "${this.buildOutputRoot}/temp"
  }

  private static String toCanonicalPath(String communityHome) {
    FileUtil.toSystemIndependentName(new File(communityHome).canonicalPath)
  }
}
