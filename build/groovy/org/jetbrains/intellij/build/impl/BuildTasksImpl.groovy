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
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildTasks

import java.time.LocalDate
import java.time.format.DateTimeFormatter
/**
 * @author nik
 */
class BuildTasksImpl extends BuildTasks {
  final BuildContext buildContext

  BuildTasksImpl(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  @Override
  void zipSources() {
    buildContext.executeStep("Build sources zip archive", BuildOptions.SOURCES_ARCHIVE_STEP) {
      String targetFile = "$buildContext.paths.artifacts/sources.zip"
      buildContext.messages.progress("Building sources archive $targetFile")

      buildContext.ant.mkdir(dir: buildContext.paths.artifacts)
      buildContext.ant.delete(file: targetFile)
      buildContext.ant.zip(destfile: targetFile) {
        fileset(dir: buildContext.paths.projectHome) {
          ["java", "groovy", "ipr", "iml", "form", "xml", "properties", "kt"].each {
            include(name: "**/*.$it")
          }
          exclude(name: "**/testData/**")
          exclude(name: "out/**")
        }
      }
      buildContext.notifyArtifactBuilt(targetFile)
    }
  }

  //todo[nik] do we need 'cp' and 'jvmArgs' parameters?
  @Override
  void buildSearchableOptions(String targetModuleName, List<String> modulesToIndex, List<String> pathsToLicenses) {
    //todo[nik] create searchableOptions.xml in a separate directory instead of modifying it in the module output
    buildContext.executeStep("Build searchable options index", BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP, {
      def javaRuntimeClasses = "${buildContext.projectBuilder.moduleOutput(buildContext.findModule("java-runtime"))}"
      if (!new File(javaRuntimeClasses).exists()) {
        buildContext.messages.
          error("Cannot build searchable options, 'java-runtime' module isn't compiled ($javaRuntimeClasses doesn't exist)")
      }

      buildContext.messages.progress("Building searchable options for modules $modulesToIndex")

      def targetModuleOutput = buildContext.projectBuilder.moduleOutput(buildContext.findModule(targetModuleName))
      String targetFile = "$targetModuleOutput/search/searchableOptions.xml"
      FileUtil.delete(new File(targetFile))

      def tempDir = "$buildContext.paths.temp/searchableOptions"
      String systemPath = "$tempDir/system"
      String configPath = "$tempDir/config"
      buildContext.ant.mkdir(dir: tempDir)
      pathsToLicenses.each {
        //todo[nik] previously licenses were copied to systemPath
        buildContext.ant.copy(file: it, todir: configPath)
      }

      def ideClasspath = new LinkedHashSet<String>()
      modulesToIndex.collectMany(ideClasspath) { buildContext.projectBuilder.moduleRuntimeClasspath(buildContext.findModule(it), false) }

      String classpathFile = "$tempDir/classpath.txt"
      new File(classpathFile).text = ideClasspath.join("\n")

      buildContext.ant.java(classname: "com.intellij.rt.execution.CommandLineWrapper", fork: true, failonerror: true) {
        jvmarg(line: "-ea -Xmx500m -XX:MaxPermSize=200m")
        jvmarg(value: "-Xbootclasspath/a:${buildContext.projectBuilder.moduleOutput(buildContext.findModule("boot"))}")
        sysproperty(key: "idea.home.path", value: buildContext.paths.projectHome)
        sysproperty(key: "idea.system.path", value: systemPath)
        sysproperty(key: "idea.config.path", value: configPath)
        arg(value: "$classpathFile")
        arg(line: "com.intellij.idea.Main traverseUI")
        arg(value: targetFile)

        classpath() {
          pathelement(location: "$javaRuntimeClasses")
        }
      }

      if (!new File(targetFile).exists()) {
        buildContext.messages.error("Failed to build searchable options index: $targetFile doesn't exist")
      }
    })
  }

  File patchIdeaPropertiesFile() {
    File originalFile = new File("$buildContext.paths.communityHome/bin/idea.properties")

    String text = originalFile.text
    if (buildContext.productProperties.additionalIDEPropertiesFilePath != null) {
      text += "\n" + new File(buildContext.productProperties.additionalIDEPropertiesFilePath).text
    }

    //todo[nik] introduce special systemSelectorWithoutVersion instead?
    String settingsDir = buildContext.systemSelector.replaceFirst("\\d+(\\.\\d+)?", "")
    text = BuildUtils.replaceAll(text, ["settings_dir": settingsDir], "@@")

    text += (buildContext.applicationInfo.isEAP ? """
#-----------------------------------------------------------------------
# Change to 'disabled' if you don't want to receive instant visual notifications
# about fatal errors that happen to an IDE or plugins installed.
#-----------------------------------------------------------------------
idea.fatal.error.notification=enabled
"""
                                                : """
#-----------------------------------------------------------------------
# Change to 'enabled' if you want to receive instant visual notifications
# about fatal errors that happen to an IDE or plugins installed.
#-----------------------------------------------------------------------
idea.fatal.error.notification=disabled
""")
    File propertiesFile = new File(buildContext.paths.temp, "idea.properties")
    propertiesFile.text = text
    return propertiesFile
  }

  @Override
  File patchApplicationInfo() {
    def sourceFile = buildContext.findApplicationInfoInSources()
    def targetFile = new File(buildContext.paths.temp, sourceFile.name)
    def date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    BuildUtils.copyAndPatchFile(sourceFile.path, targetFile.path,
                                ["BUILD_NUMBER": buildContext.fullBuildNumber, "BUILD_DATE": date])
    return targetFile
  }

  void layoutShared() {
    new File(buildContext.paths.distAll, "build.txt").text = buildContext.fullBuildNumber
    buildContext.ant.copy(todir: "$buildContext.paths.distAll/bin") {
      fileset(dir: "$buildContext.paths.communityHome/bin") {
        include(name: "*.*")
        exclude(name: "idea.properties")
      }
    }
    buildContext.ant.copy(todir: "$buildContext.paths.distAll/license") {
      fileset(dir: "$buildContext.paths.communityHome/license")
    }

    //todo[nik] these seems to be required for IDEA CE only
    buildContext.ant.copy(todir: "$buildContext.paths.distAll") {
      fileset(file: "$buildContext.paths.communityHome/LICENSE.txt")
      fileset(file: "$buildContext.paths.communityHome/NOTICE.txt")
    }
  }

  @Override
  void buildDistributions() {
    layoutShared()
    def propertiesFile = patchIdeaPropertiesFile()

    WindowsDistributionBuilder windowsBuilder = null
    buildContext.executeStep("Build Windows distribution", BuildOptions.WINDOWS_DISTRIBUTION_STEP, {
      windowsBuilder = new WindowsDistributionBuilder(buildContext)
      windowsBuilder.layoutWin(propertiesFile)
    })

    LinuxDistributionBuilder linuxBuilder = null
    buildContext.executeStep("Build Linux distribution", BuildOptions.LINUX_DISTRIBUTION_STEP) {
      linuxBuilder = new LinuxDistributionBuilder(buildContext)
      linuxBuilder.layoutUnix(propertiesFile)
    }

    MacDistributionBuilderImpl macBuilder = null
    buildContext.executeStep("Build Mac OS X distribution", BuildOptions.MAC_DISTRIBUTION_STEP) {
      macBuilder = new MacDistributionBuilderImpl(buildContext)
      macBuilder.layoutMac(propertiesFile)
    }

    if (windowsBuilder != null && linuxBuilder != null && macBuilder != null) {
      buildContext.executeStep("Build cross-platform distribution", BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP) {
        def crossPlatformBuilder = new CrossPlatformDistributionBuilder(buildContext)
        crossPlatformBuilder.buildCrossPlatformZip(windowsBuilder.winDistPath, linuxBuilder.unixDistPath, macBuilder.macDistPath)
      }
    }
    else {
      buildContext.messages.info("Skipping building cross-platform distribution because some OS-specific distributions was skipeed")
    }
  }

  @Override
  void cleanOutput() {
    buildContext.messages.block("Clean output") {
      def outputPath = buildContext.paths.buildOutputRoot
      buildContext.messages.progress("Cleaning output directory $outputPath")
      FileUtil.delete(new File(outputPath))
    }
  }

  @Override
  void compileProjectAndTests(List<String> includingTestsInModules = []) {
    if (buildContext.options.useCompiledClassesFromProjectOutput) {
      buildContext.messages.info("Compilation skipped, the compiled classes from the project output will be used")
      return
    }

    buildContext.projectBuilder.cleanOutput()
    buildContext.projectBuilder.buildProduction()
    for (String moduleName : includingTestsInModules) {
      buildContext.projectBuilder.makeModuleTests(buildContext.findModule(moduleName))
    }
  }
}