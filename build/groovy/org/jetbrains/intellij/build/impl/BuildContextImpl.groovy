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
import com.intellij.util.SystemProperties
import org.codehaus.gant.GantBuilder
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.ProductProperties
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
  private final underTeamCity

  BuildContextImpl(GantBuilder ant, JpsGantProjectBuilder projectBuilder, JpsProject project, JpsGlobal global,
                   String communityHome, String projectHome, String buildOutputRoot, ProductProperties productProperties,
                   BuildOptions options = new BuildOptions()) {
    this.projectBuilder = projectBuilder
    this.ant = ant
    this.project = project
    this.global = global
    this.productProperties = productProperties
    this.options = options
    underTeamCity = System.getProperty("teamcity.buildType.id") != null
    messages = new BuildMessagesImpl(projectBuilder, ant.project, underTeamCity)

    paths = new BuildPathsImpl(communityHome, projectHome, buildOutputRoot)

    loadProject()
    def appInfoFile = findApplicationInfoInSources()
    applicationInfo = new ApplicationInfoProperties(appInfoFile.absolutePath)

    buildNumber = System.getProperty("build.number") ?: readSnapshotBuildNumber()
    fullBuildNumber = "$productProperties.code-$buildNumber"
    systemSelector = productProperties.systemSelector(applicationInfo)
    fileNamePrefix = productProperties.prefix

    bootClassPathJarNames = ["bootstrap.jar", "extensions.jar", "util.jar", "jdom.jar", "log4j.jar", "trove4j.jar", "jna.jar"]
  }

  private void loadProject() {
    def projectHome = paths.projectHome
    JdkUtils.defineJdk(global, "IDEA jdk", JdkUtils.computeJdkHome(messages, "jdkHome", "$projectHome/build/jdk/1.6", "JDK_16_x64"))
    JdkUtils.defineJdk(global, "1.8", JdkUtils.computeJdkHome(messages, "jdk8Home", "$projectHome/build/jdk/1.8", "JDK_18_x64"))
    def bundledKotlinPath = "$paths.communityHome/build/kotlinc"
    if (!new File(bundledKotlinPath, "lib/kotlin-runtime.jar").exists()) {
      messages.error("Could not find Kotlin runtime at $bundledKotlinPath/lib/kotlin-runtime.jar: run download_kotlin.gant script to download Kotlin JARs")
    }
    JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(global).addPathVariable("KOTLIN_BUNDLED", bundledKotlinPath)

    projectBuilder.buildIncrementally = SystemProperties.getBooleanProperty("jps.build.incrementally", false)
    def dataDirName = projectBuilder.buildIncrementally ? ".jps-incremental-build" : ".jps-build-data"
    projectBuilder.dataStorageRoot = new File("$projectHome/$dataDirName")
    def tempDir = System.getProperty("teamcity.build.tempDir") ?: System.getProperty("java.io.tmpdir")
    projectBuilder.setupAdditionalLogging(new File("$tempDir/system/build-log/build.log"), System.getProperty("jps.build.debug.logging.categories", ""))

    def pathVariables = JpsModelSerializationDataService.computeAllPathVariables(global)
    JpsProjectLoader.loadProject(project, pathVariables, projectHome)
    projectBuilder.exportModuleOutputProperties()
    messages.info("Loaded project $projectHome: ${project.modules.size()} modules, ${project.libraryCollection.libraries.size()} libraries")
    if (!options.useCompiledClassesFromProjectOutput) {
      projectBuilder.targetFolder = "$paths.buildOutputRoot/classes"
    }
    else {
      def outputDir = JpsPathUtil.urlToFile(JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl)
      if (!outputDir.exists()) {
        messages.error("$BuildOptions.USE_COMPILED_CLASSES_PROPERTY is enabled, but the project output directory $outputDir.absolutePath doesn't exist")
      }
    }

    suppressWarnings()
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
    def appInfoRelativePath = "idea/${productProperties.platformPrefix}ApplicationInfo.xml"
    def appInfoFile = module.sourceRoots.collect { new File(it.file, appInfoRelativePath) }.find { it.exists() }
    if (appInfoFile == null) {
      messages.error("Cannot find $appInfoRelativePath in '$module.name' module")
    }
    return appInfoFile
  }

  @Override
  JpsModule findApplicationInfoModule() {
    def module = findModule(productProperties.appInfoModule)
    if (module == null) {
      messages.error("Cannot find module '$productProperties.appInfoModule' containing ApplicationInfo.xml file")
    }
    return module
  }

  JpsModule findModule(String name) {
    project.modules.find { it.name == name }
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
  void notifyArtifactBuilt(String artifactPath) {
    if (!underTeamCity) return

    if (!FileUtil.startsWith(artifactPath, paths.projectHome)) {
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
  BuildPathsImpl(String communityHome, String projectHome, String buildOutputRoot) {
    this.communityHome = new File(communityHome).canonicalPath
    this.projectHome = new File(projectHome).canonicalPath
    this.buildOutputRoot = new File(buildOutputRoot).canonicalPath
    artifacts = "${this.buildOutputRoot}/artifacts"
    distAll = "$buildOutputRoot/dist.all"
    temp = "$buildOutputRoot/temp"
    winJre = "$buildOutputRoot/jdk.win"
    linuxJre = "$buildOutputRoot/jdk.linux"
  }
}
