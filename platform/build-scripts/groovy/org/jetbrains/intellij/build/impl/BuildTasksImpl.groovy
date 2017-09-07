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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.intellij.build.*
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.function.Function
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
            buildContext.ant.zipfileset(dir: root.file.absolutePath, prefix: root.properties.relativeOutputPath, erroronmissingdir: false) {
              exclude(name: "**/*.png")
            }
          }
        }
      }

      buildContext.notifyArtifactBuilt(targetFilePath)
    }
  }
  
  /**
   * Build a list with modules that the IDE will provide for plugins.
   */
  void buildProvidedModulesList(String targetFilePath, List<String> modules, List<String> pathsToLicenses) {
    buildContext.executeStep("Build provided modules list", BuildOptions.PROVIDED_MODULES_LIST_STEP, {
      buildContext.messages.progress("Building provided modules list for modules $modules")
      FileUtil.delete(new File(targetFilePath))
      // Start the product in headless mode using com.intellij.ide.plugins.BundledPluginsLister.
      runApplicationStarter("$buildContext.paths.temp/builtinModules", modules, pathsToLicenses, ['listBundledPlugins', targetFilePath])
      if (!new File(targetFilePath).exists()) {
        buildContext.messages.error("Failed to build provided modules list: $targetFilePath doesn't exist")
      }
      buildContext.notifyArtifactBuilt(targetFilePath)
    })
  }

  /**
   * Build index which is used to search options in the Settings dialog.
   */
  void buildSearchableOptionsIndex(File targetDirectory, List<String> modulesToIndex, List<String> pathsToLicenses) {
    buildContext.executeStep("Build searchable options index", BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP, {
      buildContext.messages.progress("Building searchable options for modules $modulesToIndex")
      String targetFile = "${targetDirectory.absolutePath}/search/searchableOptions.xml"
      FileUtil.delete(new File(targetFile))
      // Start the product in headless mode using com.intellij.ide.ui.search.TraverseUIStarter.
      // It'll process all UI elements in Settings dialog and build index for them.
      runApplicationStarter("$buildContext.paths.temp/searchableOptions", modulesToIndex, pathsToLicenses, ['traverseUI', targetFile])
      if (!new File(targetFile).exists()) {
        buildContext.messages.error("Failed to build searchable options index: $targetFile doesn't exist")
      }
    })
  }

  private void runApplicationStarter(String tempDir, List<String> modules, List<String> pathsToLicenses, List<String> arguments) {
    def javaRuntimeClasses = "${buildContext.getModuleOutputPath(buildContext.findModule("java-runtime"))}"
    if (!new File(javaRuntimeClasses).exists()) {
      buildContext.messages.error("Cannot run application starter ${arguments}, 'java-runtime' module isn't compiled ($javaRuntimeClasses doesn't exist)")
    }

    buildContext.ant.mkdir(dir: tempDir)
    String systemPath = "$tempDir/system"
    String configPath = "$tempDir/config"
    pathsToLicenses.each {
      //todo[nik] previously licenses were copied to systemPath
      buildContext.ant.copy(file: it, todir: "$tempDir/config")
    }
  
    def ideClasspath = new LinkedHashSet<String>()
    modules.collectMany(ideClasspath) { buildContext.getModuleRuntimeClasspath(buildContext.findModule(it), false) }

    String classpathFile = "$tempDir/classpath.txt"
    new File(classpathFile).text = ideClasspath.join("\n")

    buildContext.ant.java(classname: "com.intellij.rt.execution.CommandLineWrapper", fork: true, failonerror: true) {
      jvmarg(line: "-ea -Xmx500m")
      jvmarg(value: "-Xbootclasspath/a:${buildContext.getModuleOutputPath(buildContext.findModule("boot"))}")
      sysproperty(key: "java.awt.headless", value: true)
      sysproperty(key: "idea.home.path", value: buildContext.paths.projectHome)
      sysproperty(key: "idea.system.path", value: systemPath)
      sysproperty(key: "idea.config.path", value: configPath)
      if (buildContext.productProperties.platformPrefix != null) {
        sysproperty(key: "idea.platform.prefix", value: buildContext.productProperties.platformPrefix)
      }
      arg(value: "$classpathFile")
      arg(line: "com.intellij.idea.Main")
      arguments.each { arg(value: it) }

      classpath() {
        pathelement(location: "$javaRuntimeClasses")
      }
    }
  }

  File patchIdeaPropertiesFile() {
    File originalFile = new File("$buildContext.paths.communityHome/bin/idea.properties")

    String text = originalFile.text
    if (!buildContext.shouldIDECopyJarsByDefault()) {
      text += """
#---------------------------------------------------------------------
# IDE can copy library .jar files to prevent their locking. Set this property to 'false' to enable copying.
#---------------------------------------------------------------------
idea.jars.nocopy=true
"""
    }
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

  File patchApplicationInfo() {
    def sourceFile = BuildContextImpl.findApplicationInfoInSources(buildContext.project, buildContext.productProperties, buildContext.messages)
    def targetFile = new File(buildContext.paths.temp, sourceFile.name)
    def date = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("uuuuMMddHHmm"))

    def artifactsServer = buildContext.proprietaryBuildTools.artifactsServer
    def builtinPluginsRepoUrl = ""
    if (artifactsServer != null && buildContext.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
      builtinPluginsRepoUrl = artifactsServer.urlToArtifact("${buildContext.productProperties.productCode}-plugins/plugins.xml")
    }
    BuildUtils.copyAndPatchFile(sourceFile.path, targetFile.path,
                                ["BUILD_NUMBER": buildContext.fullBuildNumber, "BUILD_DATE": date, "BUILD": buildContext.buildNumber,
                                "BUILTIN_PLUGINS_URL": builtinPluginsRepoUrl ?: ""])
    return targetFile
  }

  void layoutShared() {
    buildContext.messages.block("Copy files shared among all distributions") {
      new File(buildContext.paths.distAll, "build.txt").text = buildContext.fullBuildNumber

      buildContext.ant.copy(todir: "$buildContext.paths.distAll/bin") {
        fileset(dir: "$buildContext.paths.communityHome/bin") {
          include(name: "*.*")
          exclude(name: "idea.properties")
          exclude(name: "log.xml")
        }
      }

      copyLogXml()

      buildContext.ant.copy(todir: "$buildContext.paths.distAll/license") {
        fileset(dir: "$buildContext.paths.communityHome/license")
        buildContext.productProperties.additionalDirectoriesWithLicenses.each {
          fileset(dir: it)
        }
      }

      buildContext.productProperties.copyAdditionalFiles(buildContext, buildContext.paths.distAll)
    }
  }

  private void copyLogXml() {
    def src = new File("$buildContext.paths.communityHome/bin/log.xml")
    def dst = new File("$buildContext.paths.distAll/bin/log.xml")
    dst.parentFile.mkdirs()
    src.filterLine { String it -> !it.contains('appender-ref ref="CONSOLE-WARN"') }.writeTo(dst.newWriter()).close()
  }

  private static BuildTaskRunnable<String> createDistributionForOsTask(String taskName, Function<BuildContext, OsSpecificDistributionBuilder> factory) {
    new BuildTaskRunnable<String>(taskName) {
      @Override
      String run(BuildContext context) {
        def builder = factory.apply(context)
        if (builder != null && context.shouldBuildDistributionForOS(builder.osTargetId)) {
          return context.messages.block("Build $builder.osName Distribution") {
            def distDirectory = builder.copyFilesForOsDistribution()
            builder.buildArtifacts(distDirectory)
            distDirectory
          }
        }
        return null
      }
    }
  }

  @Override
  void compileModulesFromProduct() {
    checkProductProperties()
    def patchedApplicationInfo = patchApplicationInfo()
    def distributionJARsBuilder = new DistributionJARsBuilder(buildContext, patchedApplicationInfo)
    compileModulesForDistribution(distributionJARsBuilder)
  }

  private compileModulesForDistribution(DistributionJARsBuilder distributionJARsBuilder) {
    def bundledPlugins = buildContext.productProperties.productLayout.bundledPluginModules as Set<String>
    def moduleNames = buildContext.productProperties.productLayout.getIncludedPluginModules(bundledPlugins) +
                      distributionJARsBuilder.platformModules +
                      buildContext.productProperties.additionalModulesToCompile +
                      (buildContext.proprietaryBuildTools.scrambleTool?.additionalModulesToCompile ?: [])
    compileModules(moduleNames, buildContext.productProperties.modulesToCompileTests)

    def productLayout = buildContext.productProperties.productLayout

    def providedModulesFilePath = "${buildContext.paths.artifacts}/${buildContext.productProperties.productCode}-builtinModules.json"
    buildProvidedModulesList(providedModulesFilePath, productLayout.mainModules, productLayout.licenseFilesToBuildSearchableOptions)
    def pluginsToPublish = distributionJARsBuilder.getPluginsByModules(buildContext.productProperties.productLayout.pluginModulesToPublish)
    if (buildContext.productProperties.productLayout.buildAllCompatiblePlugins) {
      if (!buildContext.options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
        pluginsToPublish = new PluginsCollector(buildContext, providedModulesFilePath).collectCompatiblePluginsToPublish()
      }
      else {
        buildContext.messages.info("Skipping collecting compatible plugins because PROVIDED_MODULES_LIST_STEP was skipped")
      }
    }
    compileModules(pluginsToPublish.collect { it.moduleJars.values()  }.flatten() as List<String>)
    distributionJARsBuilder.pluginsToPublish.addAll(pluginsToPublish)
  }

  @Override
  void buildDistributions() {
    checkProductProperties()
    copyDependenciesFile()

    def patchedApplicationInfo = patchApplicationInfo()
    def distributionJARsBuilder = new DistributionJARsBuilder(buildContext, patchedApplicationInfo)
    compileModulesForDistribution(distributionJARsBuilder)
    buildContext.messages.block("Build platform and plugin JARs") {
      if (buildContext.shouldBuildDistributions()) {
        distributionJARsBuilder.buildJARs()
        distributionJARsBuilder.buildAdditionalArtifacts()
      }
      else {
        buildContext.messages.info("Skipped building product distributions because 'intellij.build.target.os' property is set to '$BuildOptions.OS_NONE'")
        distributionJARsBuilder.buildNonBundledPlugins()
      }
    }

    if (buildContext.shouldBuildDistributions()) {
      if (buildContext.productProperties.scrambleMainJar) {
        scramble()
      }
      buildContext.gradle.run('Setting up JetBrains JREs', 'setupJbre', "-Dintellij.build.target.os=$buildContext.options.targetOS")
      layoutShared()

      def propertiesFile = patchIdeaPropertiesFile()
      List<BuildTaskRunnable<String>> tasks = [
        createDistributionForOsTask("win", { BuildContext context ->
          context.windowsDistributionCustomizer?.with { new WindowsDistributionBuilder(context, it, propertiesFile, patchedApplicationInfo) }
        }),
        createDistributionForOsTask("linux", { BuildContext context ->
          context.linuxDistributionCustomizer?.with { new LinuxDistributionBuilder(context, it, propertiesFile) }
        }),
        createDistributionForOsTask("mac", { BuildContext context ->
          context.macDistributionCustomizer?.with { new MacDistributionBuilder(context, it, propertiesFile) }
        })
      ]

      List<String> paths = runInParallel(tasks).findAll { it != null }

      if (buildContext.productProperties.buildCrossPlatformDistribution) {
        if (paths.size() == 3) {
          buildContext.executeStep("Build cross-platform distribution", BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP) {
            def crossPlatformBuilder = new CrossPlatformDistributionBuilder(buildContext)
            crossPlatformBuilder.buildCrossPlatformZip(paths[0], paths[1], paths[2])
          }
        }
        else {
          buildContext.messages.info("Skipping building cross-platform distribution because some OS-specific distributions were skipped")
        }
      }
    }
  }

  private def copyDependenciesFile() {
    if (buildContext.gradle.forceRun('Preparing dependencies file', 'dependenciesFile')) {
      def outputFile = "$buildContext.paths.artifacts/dependencies.txt"
      buildContext.ant.copy(file: "$buildContext.paths.communityHome/build/dependencies/build/dependencies.properties", tofile: outputFile)
      buildContext.notifyArtifactBuilt(outputFile)
    }
  }

  private void scramble() {
    if (buildContext.proprietaryBuildTools.scrambleTool != null) {
      buildContext.proprietaryBuildTools.scrambleTool.scramble(buildContext.productProperties.productLayout.mainJarName, buildContext)
    }
    else {
      buildContext.messages.warning("Scrambling skipped: 'scrambleTool' isn't defined")
    }
    buildContext.ant.zip(destfile: "$buildContext.paths.artifacts/internalUtilities.zip") {
      fileset(file: "$buildContext.paths.buildOutputRoot/internal/internalUtilities.jar")
      fileset(dir: "$buildContext.paths.communityHome/lib") {
        include(name: "junit-4*.jar")
        include(name: "hamcrest-core-*.jar")
      }
      zipfileset(src: "$buildContext.paths.buildOutputRoot/internal/internalUtilities.jar") {
        include(name: "*.xml")
      }
    }
  }

  private void checkProductProperties() {
    checkProductLayout()
    def properties = buildContext.productProperties
    checkPaths(properties.brandingResourcePaths, "productProperties.brandingResourcePaths")
    checkPaths(properties.additionalIDEPropertiesFilePaths, "productProperties.additionalIDEPropertiesFilePaths")
    checkPaths([properties.yourkitAgentBinariesDirectoryPath], "productProperties.yourkitAgentBinariesDirectoryPath")
    checkPaths(properties.additionalDirectoriesWithLicenses, "productProperties.additionalDirectoriesWithLicenses")

    def winCustomizer = buildContext.windowsDistributionCustomizer
    checkPaths([winCustomizer?.icoPath], "productProperties.windowsCustomizer.icoPath")
    checkPaths([winCustomizer?.installerImagesPath], "productProperties.windowsCustomizer.installerImagesPath")

    checkPaths([buildContext.linuxDistributionCustomizer?.iconPngPath], "productProperties.linuxCustomizer.iconPngPath")

    def macCustomizer = buildContext.macDistributionCustomizer
    if (macCustomizer != null) {
      checkMandatoryField(macCustomizer.bundleIdentifier, "productProperties.macCustomizer.bundleIdentifier")
      checkMandatoryPath(macCustomizer.icnsPath, "productProperties.macCustomizer.icnsPath")
      checkPaths([macCustomizer.icnsPathForEAP], "productProperties.macCustomizer.icnsPathForEAP")
      checkMandatoryPath(macCustomizer.dmgImagePath, "productProperties.macCustomizer.dmgImagePath")
      checkPaths([macCustomizer.dmgImagePathForEAP], "productProperties.macCustomizer.dmgImagePathForEAP")
    }
  }

  private void checkProductLayout() {
    ProductModulesLayout layout = buildContext.productProperties.productLayout
    if (layout.mainJarName == null) {
      buildContext.messages.error("productProperties.productLayout.mainJarName is not specified")
    }

    List<PluginLayout> nonTrivialPlugins = layout.allNonTrivialPlugins
    def optionalModules = nonTrivialPlugins.collectMany { it.optionalModules } as Set<String>
    checkPaths(layout.licenseFilesToBuildSearchableOptions, "productProperties.productLayout.licenseFilesToBuildSearchableOptions")
    checkPluginModules(layout.bundledPluginModules, "productProperties.productLayout.bundledPluginModules", optionalModules)
    checkPluginModules(layout.pluginModulesToPublish, "productProperties.productLayout.pluginModulesToPublish", optionalModules)

    if (!layout.pluginModulesToPublish.isEmpty() && layout.buildAllCompatiblePlugins && buildContext.shouldBuildDistributions()) {
      buildContext.messages.warning("layout.buildAllCompatiblePlugins option is enabled. Value of layout.pluginModulesToPublish property " +
                                    "will be ignored ($layout.pluginModulesToPublish)")
    }
    if (!buildContext.shouldBuildDistributions() && layout.buildAllCompatiblePlugins) {
      buildContext.messages.warning("Distribution is not going to build. Hence all compatible plugins won't be built despite " +
                                    "layout.buildAllCompatiblePlugins option is enabled. layout.pluginModulesToPublish will be used ($layout.pluginModulesToPublish)")
    }
    if (layout.prepareCustomPluginRepositoryForPublishedPlugins && layout.pluginModulesToPublish.isEmpty() &&
        !layout.buildAllCompatiblePlugins) {
      buildContext.messages.error("productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins option is enabled but no pluginModulesToPublish are specified")
    }

    checkModules(layout.platformApiModules, "productProperties.productLayout.platformApiModules")
    checkModules(layout.platformImplementationModules, "productProperties.productLayout.platformImplementationModules")
    checkModules(layout.additionalPlatformJars.values(), "productProperties.productLayout.additionalPlatformJars")
    checkModules(layout.moduleExcludes.keySet(), "productProperties.productLayout.moduleExcludes")
    checkModules(layout.mainModules, "productProperties.productLayout.mainModules")
    checkModules([layout.searchableOptionsModule], "productProperties.productLayout.searchableOptionsModule")
    checkModules(layout.pluginModulesWithRestrictedCompatibleBuildRange, "productProperties.productLayout.pluginModulesWithRestrictedCompatibleBuildRange")
    checkProjectLibraries(layout.projectLibrariesToUnpackIntoMainJar, "productProperties.productLayout.projectLibrariesToUnpackIntoMainJar")
    nonTrivialPlugins.findAll {layout.bundledPluginModules.contains(it.mainModule)}.each { plugin ->
      checkModules(plugin.moduleJars.values() - plugin.optionalModules, "'$plugin.mainModule' plugin")
      checkModules(plugin.moduleExcludes.keySet(), "'$plugin.mainModule' plugin")
      checkProjectLibraries(plugin.includedProjectLibraries, "'$plugin.mainModule' plugin")
    }
  }

  private void checkModules(Collection<String> modules, String fieldName) {
    def unknownModules = modules.findAll {buildContext.findModule(it) == null}
    if (!unknownModules.empty) {
      buildContext.messages.error("The following modules from $fieldName aren't found in the project: $unknownModules")
    }
  }

  private void checkProjectLibraries(Collection<String> names, String fieldName) {
    def unknownLibraries = names.findAll {buildContext.project.libraryCollection.findLibrary(it) == null}
    if (!unknownLibraries.empty) {
      buildContext.messages.error("The following libraries from $fieldName aren't found in the project: $unknownLibraries")
    }
  }

  private void checkPluginModules(List<String> pluginModules, String fieldName, Set<String> optionalModules) {
    checkModules(pluginModules, fieldName)
    def unknownBundledPluginModules = pluginModules.findAll { !optionalModules.contains(it) && buildContext.findFileInModuleSources(it, "META-INF/plugin.xml") == null }
    if (!unknownBundledPluginModules.empty) {
      buildContext.messages.error(
        "The following modules from $fieldName don't contain META-INF/plugin.xml file and aren't specified as optional plugin modules " +
        "in productProperties.productLayout.allNonTrivialPlugins: $unknownBundledPluginModules. "
      )
    }
  }

  private void checkPaths(Collection<String> paths, String fieldName) {
    def nonExistingFiles = paths.findAll { it != null && !new File(it).exists() }
    if (!nonExistingFiles.empty) {
      buildContext.messages.error("$fieldName contains non-existing path${nonExistingFiles.size() > 1 ? "s" : ""}: ${nonExistingFiles.join(",")}")
    }
  }

  private void checkMandatoryField(String value, String fieldName) {
    if (value == null) {
      buildContext.messages.error("Mandatory property '$fieldName' is not specified")
    }
  }

  private void checkMandatoryPath(String path, String fieldName) {
    checkMandatoryField(path, fieldName)
    checkPaths([path], fieldName)
  }

  @Override
  void compileProjectAndTests(List<String> includingTestsInModules = []) {
    compileModules(null, includingTestsInModules)
  }

  @Override
  void compileModules(List<String> moduleNames, List<String> includingTestsInModules = []) {
    CompilationTasks.create(buildContext).compileModules(moduleNames, includingTestsInModules)
  }

  private <V> List<V> runInParallel(List<BuildTaskRunnable<V>> tasks) {
    if (!buildContext.options.runBuildStepsInParallel) {
      return tasks.collect {
        it.run(buildContext)
      }
    }

    List<V> results = []
    try {
      results = buildContext.messages.block("Run parallel tasks") {
        buildContext.messages.info("Started ${tasks.size()} tasks in parallel: ${tasks.collect { it.taskName }}")
        def executorService = Executors.newCachedThreadPool()
        List<Future<V>> futures = tasks.collect { task ->
          def childContext = buildContext.forkForParallelTask(task.taskName)
          executorService.submit({
            def start = System.currentTimeMillis()
            childContext.messages.onForkStarted()
            try {
              return task.run(childContext)
            }
            finally {
              buildContext.messages.info("'$task.taskName' task finished in ${StringUtil.formatDuration(System.currentTimeMillis() - start)}")
              childContext.messages.onForkFinished()
            }
          } as Callable<V>)
        }
        futures.collect { it.get() }
      }
    }
    finally {
      buildContext.messages.onAllForksFinished()
    }
    results
  }

  @Override
  void buildUpdaterJar() {
    new LayoutBuilder(buildContext.ant, buildContext.project, false).layout(buildContext.paths.artifacts) {
      jar("updater.jar") {
        module("updater")
      }
    }
  }

  @Override
  void buildUnpackedDistribution(String targetDirectory) {
    buildContext.paths.distAll = targetDirectory
    def jarsBuilder = new DistributionJARsBuilder(buildContext, patchApplicationInfo())
    jarsBuilder.buildJARs()
    layoutShared()

    //todo[nik]
    buildContext.ant.copy(todir: "$targetDirectory/lib/libpty/") {
      fileset(dir: "$buildContext.paths.communityHome/lib/libpty/")
    }
/*
    //todo[nik] uncomment this to update os-specific files (e.g. in 'bin' directory) as well
    def propertiesFile = patchIdeaPropertiesFile()
    OsSpecificDistributionBuilder builder;
    if (SystemInfo.isWindows) {
      builder = new WindowsDistributionBuilder(buildContext, buildContext.windowsDistributionCustomizer, propertiesFile)
    }
    else if (SystemInfo.isLinux) {
      builder = new LinuxDistributionBuilder(buildContext, buildContext.linuxDistributionCustomizer, propertiesFile)
    }
    else if (SystemInfo.isMac) {
      builder = new MacDistributionBuilder(buildContext, buildContext.macDistributionCustomizer, propertiesFile)
    }
    else {
      buildContext.messages.error("Update from source isn't supported for '$SystemInfo.OS_NAME'")
      return
    }
    def osSpecificDistPath = builder.copyFilesForOsDistribution()
*/
  }

  private abstract static class BuildTaskRunnable<V> {
    final String taskName

    BuildTaskRunnable(String name) {
      taskName = name
    }

    abstract V run(BuildContext context)
  }
}