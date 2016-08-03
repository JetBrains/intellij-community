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
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.ProductModulesLayout
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule

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
  void zipProjectSources() {
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

  @Override
  void zipSourcesOfModules(Collection<String> modules, String targetFilePath) {
    buildContext.executeStep("Build sources of modules archive", BuildOptions.SOURCES_ARCHIVE_STEP) {
      buildContext.messages.progress("Building archive of ${modules.size()} modules to $targetFilePath")
      buildContext.ant.mkdir(dir: new File(targetFilePath).getParent())
      buildContext.ant.delete(file: targetFilePath)
      buildContext.ant.zip(destfile: targetFilePath) {
        modules.each {
          JpsModule module = buildContext.findModule(it)
          if (module == null) {
            buildContext.messages.error("Cannot build sources archive: '$it' module doesn't exist")
          }
          module.getSourceRoots(JavaSourceRootType.SOURCE).each { root ->
            buildContext.ant.
              zipfileset(dir: root.file.absolutePath, prefix: root.properties.packagePrefix.replace('.', '/'), erroronmissingdir: false)
          }
          module.getSourceRoots(JavaResourceRootType.RESOURCE).each { root ->
            buildContext.ant.zipfileset(dir: root.file.absolutePath, prefix: root.properties.relativeOutputPath, erroronmissingdir: false)
          }
        }
      }

      buildContext.notifyArtifactBuilt(targetFilePath)
    }
  }

  @Override
  void buildSearchableOptions(String targetModuleName, List<String> modulesToIndex, List<String> pathsToLicenses) {
    buildSearchableOptions(new File(buildContext.projectBuilder.moduleOutput(buildContext.findModule(targetModuleName))), modulesToIndex, pathsToLicenses)
  }

//todo[nik] do we need 'cp' and 'jvmArgs' parameters?
  void buildSearchableOptions(File targetDirectory, List<String> modulesToIndex, List<String> pathsToLicenses) {
    buildContext.executeStep("Build searchable options index", BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP, {
      def javaRuntimeClasses = "${buildContext.projectBuilder.moduleOutput(buildContext.findModule("java-runtime"))}"
      if (!new File(javaRuntimeClasses).exists()) {
        buildContext.messages.error("Cannot build searchable options, 'java-runtime' module isn't compiled ($javaRuntimeClasses doesn't exist)")
      }

      buildContext.messages.progress("Building searchable options for modules $modulesToIndex")

      String targetFile = "${targetDirectory.absolutePath}/search/searchableOptions.xml"
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
        if (buildContext.productProperties.platformPrefix != null) {
          sysproperty(key: "idea.platform.prefix", value: buildContext.productProperties.platformPrefix)
        }
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
    buildContext.productProperties.additionalIDEPropertiesFilePaths.each {
      text += "\n" + new File(it).text
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
      buildContext.productProperties.additionalDirectoriesWithLicenses.each {
        fileset(dir: it)
      }
    }

    buildContext.productProperties.copyAdditionalFiles(buildContext, buildContext.paths.distAll)
  }

  @Override
  void buildDistributions() {
    buildContext.messages.block("Copy files shared among all distributions") {
      layoutShared()
    }
    def propertiesFile = patchIdeaPropertiesFile()

    WindowsDistributionBuilder windowsBuilder = null
    LinuxDistributionBuilder linuxBuilder = null
    MacDistributionBuilder macBuilder = null
    runInParallel([new BuildTaskRunnable("win") {
      @Override
      void run(BuildContext buildContext) {
        def windowsDistributionCustomizer = buildContext.windowsDistributionCustomizer
        if (windowsDistributionCustomizer != null) {
          buildContext.executeStep("Build Windows distribution", BuildOptions.WINDOWS_DISTRIBUTION_STEP, {
            windowsBuilder = new WindowsDistributionBuilder(buildContext, windowsDistributionCustomizer)
            windowsBuilder.layoutWin(propertiesFile)
          })
        }
      }
    }, new BuildTaskRunnable("linux") {
      @Override
      void run(BuildContext buildContext) {
        def linuxDistributionCustomizer = buildContext.linuxDistributionCustomizer
        if (linuxDistributionCustomizer != null) {
          buildContext.executeStep("Build Linux distribution", BuildOptions.LINUX_DISTRIBUTION_STEP) {
            linuxBuilder = new LinuxDistributionBuilder(buildContext, linuxDistributionCustomizer)
            linuxBuilder.layoutUnix(propertiesFile)
          }
        }
      }
    }, new BuildTaskRunnable("mac") {
      @Override
      void run(BuildContext buildContext) {
        def macDistributionCustomizer = buildContext.macDistributionCustomizer
        if (macDistributionCustomizer != null) {
          buildContext.executeStep("Build Mac OS distribution", BuildOptions.MAC_DISTRIBUTION_STEP) {
            macBuilder = new MacDistributionBuilder(buildContext, macDistributionCustomizer)
            macBuilder.layoutMac(propertiesFile)
          }
        }
      }
    }
    ])

    if (buildContext.productProperties.buildCrossPlatformDistribution) {
      if (windowsBuilder != null && linuxBuilder != null && macBuilder != null) {
        buildContext.executeStep("Build cross-platform distribution", BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP) {
          def crossPlatformBuilder = new CrossPlatformDistributionBuilder(buildContext)
          crossPlatformBuilder.buildCrossPlatformZip(windowsBuilder.winDistPath, linuxBuilder.unixDistPath, macBuilder.macDistPath)
        }
      }
      else {
        buildContext.messages.info("Skipping building cross-platform distribution because some OS-specific distributions were skipped")
      }
    }
  }

  @Override
  void compileModulesAndBuildDistributions(List<PluginLayout> allPlugins) {
    def productLayout = buildContext.productProperties.productLayout
    checkProductLayout(productLayout, allPlugins)
    cleanOutput()
    def includedModules = productLayout.getIncludedModules(allPlugins)
    compileModules(includedModules)
    buildContext.messages.block("Build platform and plugin JARs") {
      new DistributionJARsBuilder(buildContext, includedModules, allPlugins).buildJARs()
    }
    if (buildContext.productProperties.scrambleMainJar) {
      if (buildContext.scrambleTool != null) {
        buildContext.scrambleTool.scramble(buildContext.productProperties.productLayout.mainJarName, buildContext)
      }
      else {
        buildContext.messages.warning("Scrambling skipped: 'srambleTool' isn't defined")
      }
    }
    buildDistributions()
  }

  private void checkProductLayout(ProductModulesLayout layout, List<PluginLayout> allPlugins) {
    def allPluginModules = allPlugins.collectMany { [it.mainModule] + it.optionalModules } as Set<String>
    checkPluginModules(layout.bundledPluginModules, "bundledPluginModules", allPluginModules)
    checkPluginModules(layout.pluginModulesToPublish, "pluginModulesToPublish", allPluginModules)

    checkModules(layout.platformApiModules, "platformApiModules")
    checkModules(layout.platformImplementationModules, "platformImplementationModules")
    checkModules(layout.additionalPlatformModules.keySet(), "additionalPlatformModules")
  }

  private void checkModules(Collection<String> modules, String fieldName) {
    def unknownModules = modules.findAll {buildContext.findModule(it) == null}
    if (!unknownModules.empty) {
      buildContext.messages.error("The following modules from productProperties.$fieldName aren't found in the project.")
    }
  }

  private void checkPluginModules(List<String> pluginModules, String fieldName, Set<String> allPluginModules) {
    def unknownBundledPluginModules = pluginModules.findAll { !allPluginModules.contains(it) }
    if (!unknownBundledPluginModules.empty) {
      buildContext.messages.error(
        "The following modules from productProperties.productLayout.$fieldName aren't found in the registered plugins: $unknownBundledPluginModules. " +
        "Make sure that the plugin layouts are specified in one of *_REPOSITORY_PLUGINS lists and you refer to either main plugin module or an optional module."
      )
    }
  }

  @Override
  void cleanOutput() {
    buildContext.messages.block("Clean output") {
      def outputPath = buildContext.paths.buildOutputRoot
      buildContext.messages.progress("Cleaning output directory $outputPath")
      new File(outputPath).listFiles()?.each {
        if (buildContext instanceof BuildContextImpl && buildContext.outputDirectoriesToKeep.contains(it.name)) {
          buildContext.messages.info("Skipped cleaning for $it.absolutePath")
        }
        else {
          FileUtil.delete(it)
        }
      }
    }
  }

  @Override
  void compileProjectAndTests(List<String> includingTestsInModules = []) {
    compileModules(null, includingTestsInModules)
  }

  @Override
  void compileModules(List<String> moduleNames, List<String> includingTestsInModules = []) {
    if (buildContext.options.useCompiledClassesFromProjectOutput) {
      buildContext.messages.info("Compilation skipped, the compiled classes from the project output will be used")
      return
    }
    if (buildContext.options.pathToCompiledClassesArchive != null) {
      buildContext.messages.info("Compilation skipped, the compiled classes from '${buildContext.options.pathToCompiledClassesArchive}' will be used")
      return
    }

    buildContext.projectBuilder.cleanOutput()
    if (moduleNames == null) {
      buildContext.projectBuilder.buildProduction()
    }
    else {
      List<String> modulesToBuild = ((moduleNames as Set<String>) + DistributionJARsBuilder.ADDITIONAL_MODULES_TO_COMPILE
        + buildContext.scrambleTool?.additionalModulesToCompile ?: []) as List<String>
      List<String> invalidModules = modulesToBuild.findAll {buildContext.findModule(it) == null}
      if (!invalidModules.empty) {
        buildContext.messages.warning("The following modules won't be compiled: $invalidModules")
      }
      buildContext.projectBuilder.buildModules(modulesToBuild.collect {buildContext.findModule(it)}.findAll {it != null})
    }
    for (String moduleName : includingTestsInModules) {
      buildContext.projectBuilder.makeModuleTests(buildContext.findModule(moduleName))
    }
  }

  private void runInParallel(List<BuildTaskRunnable> tasks) {
    if (!buildContext.options.runBuildStepsInParallel) {
      tasks.each {
        it.run(buildContext)
      }
      return
    }

    List<Thread> threads = []
    List<BuildMessages> messages = []
    List<Throwable> errors = Collections.synchronizedList([])
    tasks.each { task ->
      def childContext = buildContext.forkForParallelTask(task.taskName)
      def thread = new Thread("Thread for build task '$task.taskName'") {
        @Override
        void run() {
          def start = System.currentTimeMillis()
          childContext.messages.onForkStarted()
          try {
            task.run(childContext)
          }
          catch (Throwable t) {
            errors << t
          }
          finally {
            buildContext.messages.info("'$task.taskName' task finished in ${StringUtil.formatDuration(System.currentTimeMillis() - start)}")
            childContext.messages.onForkFinished()
          }
        }
      }
      threads << thread
      messages << childContext.messages
    }
    buildContext.messages.block("Run parallel tasks") {
      buildContext.messages.info("Started ${tasks.size()} tasks in parallel: ${tasks.collect { it.taskName }}")
      threads.each { it.start() }
      threads.each { it.join() }
    }
    buildContext.messages.onAllForksFinished()
    if (!errors.empty) {
      errors.subList(1, errors.size()).each { it.printStackTrace() }
      throw errors.first()
    }
  }

  private abstract static class BuildTaskRunnable {
    final String taskName

    BuildTaskRunnable(String name) {
      taskName = name
    }

    abstract void run(BuildContext context)
  }
}
