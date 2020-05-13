// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import groovy.io.FileType
import org.jetbrains.intellij.build.*
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.function.Function
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
    logFreeDiskSpace("after building sources archive")
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
  void buildProvidedModulesList(String targetFilePath, List<String> modules) {
    buildContext.executeStep("Build provided modules list", BuildOptions.PROVIDED_MODULES_LIST_STEP, {
      buildContext.messages.progress("Building provided modules list for ${modules.size()} modules")
      buildContext.messages.debug("Building provided modules list for the following modules: $modules")
      FileUtil.delete(new File(targetFilePath))
      // Start the product in headless mode using com.intellij.ide.plugins.BundledPluginsLister.
      runApplicationStarter(buildContext, "$buildContext.paths.temp/builtinModules", modules, ['listBundledPlugins', targetFilePath])
      if (!new File(targetFilePath).exists()) {
        buildContext.messages.error("Failed to build provided modules list: $targetFilePath doesn't exist")
      }
      buildContext.notifyArtifactBuilt(targetFilePath)
    })
  }

  static void runApplicationStarter(BuildContext context, String tempDir, List<String> modules, List<String> arguments, Map<String, Object> systemProperties = [:]) {
    context.ant.mkdir(dir: tempDir)

    Set<String> ideClasspath = new LinkedHashSet<String>()
    context.messages.debug("Collecting classpath to run application starter '${arguments.first()}:")
    for (moduleName in modules) {
      for (pathElement in context.getModuleRuntimeClasspath(context.findRequiredModule(moduleName), false)) {
        if (ideClasspath.add(pathElement)) {
          context.messages.debug(" $pathElement from $moduleName")
        }
      }
    }

    Map<String, ?> ideaProperties = [
      "java.awt.headless": true,
      "idea.home.path"   : context.paths.projectHome,
      "idea.system.path" : "${tempDir}/system",
      "idea.config.path" : "${tempDir}/config"]

    if (context.productProperties.platformPrefix != null) {
      ideaProperties += ["idea.platform.prefix": context.productProperties.platformPrefix]
    }

    BuildUtils.runJava(
      context,
      ["-ea", "-Xmx512m"],
      ideaProperties + systemProperties,
      ideClasspath,
      "com.intellij.idea.Main",
      arguments)
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
      builtinPluginsRepoUrl = artifactsServer.urlToArtifact(buildContext, "${buildContext.applicationInfo.productCode}-plugins/plugins.xml")
      if (builtinPluginsRepoUrl.startsWith("http:")) {
        buildContext.messages.error("Insecure artifact server: " + builtinPluginsRepoUrl)
      }
    }
    BuildUtils.copyAndPatchFile(sourceFile.path, targetFile.path,
                                ["BUILD_NUMBER": buildContext.fullBuildNumber, "BUILD_DATE": date, "BUILD": buildContext.buildNumber,
                                "BUILTIN_PLUGINS_URL": builtinPluginsRepoUrl ?: ""])
    return targetFile
  }

  void layoutShared() {
    buildContext.messages.block("Copy files shared among all distributions") {
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

      if (buildContext.applicationInfo.svgRelativePath != null) {
        buildContext.ant.copy(file: findBrandingResource(buildContext.applicationInfo.svgRelativePath), tofile: "$buildContext.paths.distAll/bin/${buildContext.productProperties.baseFileName}.svg")
      }

      buildContext.productProperties.copyAdditionalFiles(buildContext, buildContext.paths.distAll)
    }
  }

  static void generateBuildTxt(BuildContext buildContext, String targetDirectory) {
    new File(targetDirectory, "build.txt").text = buildContext.fullBuildNumber
  }

  private File findBrandingResource(String relativePath) {
    def inModule = buildContext.findFileInModuleSources(buildContext.productProperties.applicationInfoModule, relativePath)
    if (inModule != null) return inModule
    def inResources = buildContext.productProperties.brandingResourcePaths.collect { new File(it, relativePath) }.find { it.exists() }
    if (inResources == null) {
      buildContext.messages.error("Cannot find '$relativePath' in sources of '$buildContext.productProperties.applicationInfoModule' and in $buildContext.productProperties.brandingResourcePaths")
    }
    return inResources
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
        if (builder != null && context.shouldBuildDistributionForOS(builder.targetOs.osId)) {
          return context.messages.block("Build $builder.targetOs.osName Distribution") {
            def osSpecificDistDirectory = "$context.paths.buildOutputRoot/dist.$builder.targetOs.distSuffix".toString()
            builder.copyFilesForOsDistribution(osSpecificDistDirectory)
            builder.buildArtifacts(osSpecificDistDirectory)
            osSpecificDistDirectory
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
    compileModulesForDistribution(patchedApplicationInfo)
  }

  private DistributionJARsBuilder compileModulesForDistribution(File patchedApplicationInfo) {
    def productLayout = buildContext.productProperties.productLayout
    def moduleNames = DistributionJARsBuilder.getModulesToCompile(buildContext)
    def mavenArtifacts = buildContext.productProperties.mavenArtifacts
    compileModules(moduleNames + (buildContext.proprietaryBuildTools.scrambleTool?.additionalModulesToCompile ?: []) +
                   productLayout.mainModules + mavenArtifacts.additionalModules + mavenArtifacts.proprietaryModules,
                   buildContext.productProperties.modulesToCompileTests)

    def pluginsToPublish = new LinkedHashSet<>(
      DistributionJARsBuilder.getPluginsByModules(buildContext, buildContext.productProperties.productLayout.pluginModulesToPublish))

    if (buildContext.shouldBuildDistributions()) {
      def providedModulesFilePath = "${buildContext.paths.artifacts}/${buildContext.applicationInfo.productCode}-builtinModules.json"
      buildProvidedModulesList(providedModulesFilePath, moduleNames)
      if (buildContext.productProperties.productLayout.buildAllCompatiblePlugins) {
        if (!buildContext.options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
          pluginsToPublish.addAll(new PluginsCollector(buildContext, providedModulesFilePath).collectCompatiblePluginsToPublish())
        }
        else {
          buildContext.messages.info("Skipping collecting compatible plugins because PROVIDED_MODULES_LIST_STEP was skipped")
        }
      }
    }
    return compilePlatformAndPluginModules(patchedApplicationInfo, pluginsToPublish)
  }

  private DistributionJARsBuilder compilePlatformAndPluginModules(File patchedApplicationInfo, LinkedHashSet<PluginLayout> pluginsToPublish) {
    def distributionJARsBuilder = new DistributionJARsBuilder(buildContext, patchedApplicationInfo, pluginsToPublish)
    compileModules(distributionJARsBuilder.modulesForPluginsToPublish)

    //we need this to ensure that all libraries which may be used in the distribution are resolved, even if product modules don't depend on them (e.g. JUnit5)
    CompilationTasks.create(buildContext).resolveProjectDependencies()
    CompilationTasks.create(buildContext).buildProjectArtifacts(distributionJARsBuilder.includedProjectArtifacts)
    return distributionJARsBuilder
  }

  @Override
  void buildDistributions() {
    checkProductProperties()
    copyDependenciesFile()
    setupBundledMaven()

    def patchedApplicationInfo = patchApplicationInfo()
    logFreeDiskSpace("before compilation")
    def distributionJARsBuilder = compileModulesForDistribution(patchedApplicationInfo)
    logFreeDiskSpace("after compilation")
    def mavenArtifacts = buildContext.productProperties.mavenArtifacts
    if (mavenArtifacts.forIdeModules || !mavenArtifacts.additionalModules.isEmpty() || !mavenArtifacts.proprietaryModules.isEmpty()) {
      buildContext.executeStep("Generate Maven artifacts", BuildOptions.MAVEN_ARTIFACTS_STEP) {
        def mavenArtifactsBuilder = new MavenArtifactsBuilder(buildContext)
        def ideModuleNames
        if (mavenArtifacts.forIdeModules) {
          def bundledPlugins = buildContext.productProperties.productLayout.bundledPluginModules as Set<String>
          ideModuleNames = distributionJARsBuilder.platformModules + buildContext.productProperties.productLayout.getIncludedPluginModules(bundledPlugins)
        } else {
          ideModuleNames = []
        }
        def moduleNames = ideModuleNames + mavenArtifacts.additionalModules
        if (!moduleNames.isEmpty()) {
          mavenArtifactsBuilder.generateMavenArtifacts(moduleNames, 'maven-artifacts')
        }
        if (!mavenArtifacts.proprietaryModules.isEmpty()) {
          mavenArtifactsBuilder.generateMavenArtifacts(mavenArtifacts.proprietaryModules, 'proprietary-maven-artifacts')
        }
      }
    }

    buildContext.messages.block("Build platform and plugin JARs") {
      if (buildContext.shouldBuildDistributions()) {
        distributionJARsBuilder.buildJARs()
        distributionJARsBuilder.buildAdditionalArtifacts()
      }
      else {
        buildContext.messages.info("Skipped building product distributions because 'intellij.build.target.os' property is set to '$BuildOptions.OS_NONE'")
        distributionJARsBuilder.buildOrderFiles()
        distributionJARsBuilder.buildSearchableOptions()
        distributionJARsBuilder.buildNonBundledPlugins()
      }
    }

    if (buildContext.shouldBuildDistributions()) {
      if (buildContext.productProperties.scrambleMainJar) {
        scramble()
      }
      setupJBre()
      layoutShared()

      def propertiesFile = patchIdeaPropertiesFile()
      List<BuildTaskRunnable<String>> tasks = new ArrayList<>()
      if (buildContext.shouldBuildDistributionForOS(BuildOptions.OS_WINDOWS)) {
        tasks.add(createDistributionForOsTask("win", { BuildContext context ->
          context.windowsDistributionCustomizer?.
            with { new WindowsDistributionBuilder(context, it, propertiesFile, patchedApplicationInfo) }
        }))
      }
      if (buildContext.shouldBuildDistributionForOS(BuildOptions.OS_LINUX)) {
        tasks.add(createDistributionForOsTask("linux", { BuildContext context ->
          context.linuxDistributionCustomizer?.with { new LinuxDistributionBuilder(context, it, propertiesFile) }
        }))
      }
      if (buildContext.shouldBuildDistributionForOS(BuildOptions.OS_MAC)) {
        tasks.add(createDistributionForOsTask("mac", { BuildContext context ->
          context.macDistributionCustomizer?.with { new MacDistributionBuilder(context, it, propertiesFile) }
        }))
      }

      List<String> paths = runInParallel(tasks).findAll { it != null }

      if (Boolean.getBoolean("intellij.build.toolbox.litegen")) {
        if (buildContext.buildNumber == null) {
          buildContext.messages.warning("Toolbox LiteGen is not executed - it does not support SNAPSHOT build numbers")
        }
        else if (buildContext.options.targetOS != BuildOptions.OS_ALL) {
          buildContext.messages.
            warning("Toolbox LiteGen is not executed - it doesn't support installers are being built only for specific OS")
        }
        else {
          buildContext.executeStep("Building Toolbox Lite-Gen Links", BuildOptions.TOOLBOX_LITE_GEN_STEP) {
            String toolboxLiteGenVersion = System.getProperty("intellij.build.toolbox.litegen.version")
            if (toolboxLiteGenVersion == null) {
              buildContext.messages.error("Toolbox Lite-Gen version is not specified!")
            }
            else {
              String[] liteGenArgs = [
                'runToolboxLiteGen',
                "-Pintellij.build.toolbox.litegen.version=${toolboxLiteGenVersion}",
                //NOTE[jo]: right now we assume all installer files are created under the same path
                "-Pintellij.build.artifacts=${buildContext.paths.artifacts}",
                "-Pintellij.build.productCode=${buildContext.applicationInfo.productCode}",
                "-Pintellij.build.isEAP=${buildContext.applicationInfo.isEAP}",
                "-Pintellij.build.output=${buildContext.paths.buildOutputRoot}/toolbox-lite-gen",
              ]

              buildContext.gradle.runWithModularRuntime('Run Toolbox LiteGen', liteGenArgs)
            }
          }
        }
      }

      if (buildContext.productProperties.buildCrossPlatformDistribution) {
        if (paths.size() == 3) {
          buildContext.executeStep("Build cross-platform distribution", BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP) {
            def crossPlatformBuilder = new CrossPlatformDistributionBuilder(buildContext)
            def monsterZip = crossPlatformBuilder.buildCrossPlatformZip(paths[0], paths[1], paths[2])

            Map<String, String> checkerConfig = buildContext.productProperties.versionCheckerConfig
            if (checkerConfig != null) {
              new ClassVersionChecker(checkerConfig).checkVersions(buildContext, new File(monsterZip))
            }
          }
        }
        else {
          buildContext.messages.info("Skipping building cross-platform distribution because some OS-specific distributions were skipped")
        }
      }
    }
    logFreeDiskSpace("after building distributions")
  }

  @Override
  void buildNonBundledPlugins(List<String> mainPluginModules) {
    checkProductProperties()
    checkPluginModules(mainPluginModules, "mainPluginModules")
    copyDependenciesFile()
    def pluginsToPublish = new LinkedHashSet<PluginLayout>(
      DistributionJARsBuilder.getPluginsByModules(buildContext, mainPluginModules))
    def distributionJARsBuilder = compilePlatformAndPluginModules(patchApplicationInfo(), pluginsToPublish)
    distributionJARsBuilder.buildSearchableOptions()
    distributionJARsBuilder.buildNonBundledPlugins()
  }

  @Override
  void generateProjectStructureMapping(File targetFile) {
    def jarsBuilder = new DistributionJARsBuilder(buildContext, patchApplicationInfo())
    jarsBuilder.generateProjectStructureMapping(targetFile)
  }

  private void setupJBre() {
    logFreeDiskSpace("before downloading JREs")
    String[] args = [
      'setupJbre', "-Dintellij.build.target.os=$buildContext.options.targetOS",
      "-Dintellij.build.bundled.jre.version=$buildContext.options.bundledJreVersion"
    ]
    String prefix = System.getProperty("intellij.build.bundled.jre.prefix")
    if (prefix != null) {
      args += "-Dintellij.build.bundled.jre.prefix=$prefix"
    }
    if (buildContext.options.bundledJreBuild != null) {
      args += "-Dintellij.build.bundled.jre.build=$buildContext.options.bundledJreBuild"
    }
    buildContext.gradle.run('Setting up JetBrains JREs', args)
    logFreeDiskSpace("after downloading JREs")
  }

  private void setupBundledMaven() {
    logFreeDiskSpace("before downloading Maven")
    buildContext.gradle.run('Setting up Bundled Maven', 'setupBundledMaven')
    logFreeDiskSpace("after downloading Maven")
  }

  static def unpackPty4jNative(BuildContext buildContext, String distDir, String pty4jOsSubpackageName) {
    def pty4jNativeDir = "$distDir/lib/pty4j-native"
    def nativePkg = "resources/com/pty4j/native"
    def includedNativePkg = StringUtil.trimEnd(nativePkg + "/" + StringUtil.notNullize(pty4jOsSubpackageName), '/')
    buildContext.project.libraryCollection.findLibrary("pty4j").getFiles(JpsOrderRootType.COMPILED).each {
      buildContext.ant.unzip(src: it, dest: pty4jNativeDir) {
        buildContext.ant.patternset() {
          include(name: "$includedNativePkg/**")
        }
        buildContext.ant.mapper(type: "glob", from: "$nativePkg/*", to: "*")
      }
    }
    def files = []
    new File(pty4jNativeDir).eachFileRecurse(FileType.FILES) { file ->
      files << file
    }
    if (files.empty) {
      buildContext.messages.error("Cannot layout pty4j native: no files extracted")
    }
  }

  private void logFreeDiskSpace(String phase) {
    CompilationContextImpl.logFreeDiskSpace(buildContext.messages, buildContext.paths.buildOutputRoot, phase)
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
      buildContext.project.libraryCollection.findLibrary("JUnit4").getFiles(JpsOrderRootType.COMPILED).each {
        fileset(file: it.absolutePath)
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
    checkPaths(properties.additionalDirectoriesWithLicenses, "productProperties.additionalDirectoriesWithLicenses")

    checkModules(properties.additionalModulesToCompile, "productProperties.additionalModulesToCompile")
    checkModules(properties.modulesToCompileTests, "productProperties.modulesToCompileTests")
    checkModules(properties.additionalModulesRequiredForScrambling, "productProperties.additionalModulesRequiredForScrambling")

    def winCustomizer = buildContext.windowsDistributionCustomizer
    checkPaths([winCustomizer?.icoPath], "productProperties.windowsCustomizer.icoPath")
    checkPaths([winCustomizer?.icoPathForEAP], "productProperties.windowsCustomizer.icoPathForEAP")
    checkPaths([winCustomizer?.installerImagesPath], "productProperties.windowsCustomizer.installerImagesPath")

    checkPaths([buildContext.linuxDistributionCustomizer?.iconPngPath], "productProperties.linuxCustomizer.iconPngPath")
    checkPaths([buildContext.linuxDistributionCustomizer?.iconPngPathForEAP], "productProperties.linuxCustomizer.iconPngPathForEAP")

    def macCustomizer = buildContext.macDistributionCustomizer
    if (macCustomizer != null) {
      checkMandatoryField(macCustomizer.bundleIdentifier, "productProperties.macCustomizer.bundleIdentifier")
      checkMandatoryPath(macCustomizer.icnsPath, "productProperties.macCustomizer.icnsPath")
      checkPaths([macCustomizer.icnsPathForEAP], "productProperties.macCustomizer.icnsPathForEAP")
      checkMandatoryPath(macCustomizer.dmgImagePath, "productProperties.macCustomizer.dmgImagePath")
      checkPaths([macCustomizer.dmgImagePathForEAP], "productProperties.macCustomizer.dmgImagePathForEAP")
    }

    checkModules(properties.mavenArtifacts.additionalModules, "productProperties.mavenArtifacts.additionalModules")
    if (buildContext.productProperties.scrambleMainJar) {
      checkModules(buildContext.proprietaryBuildTools.scrambleTool?.namesOfModulesRequiredToBeScrambled, "ProprietaryBuildTools.scrambleTool.namesOfModulesRequiredToBeScrambled")
    }
  }

  private void checkProductLayout() {
    ProductModulesLayout layout = buildContext.productProperties.productLayout
    if (layout.mainJarName == null) {
      buildContext.messages.error("productProperties.productLayout.mainJarName is not specified")
    }

    List<PluginLayout> nonTrivialPlugins = layout.allNonTrivialPlugins
    checkPluginModules(layout.bundledPluginModules, "productProperties.productLayout.bundledPluginModules")
    checkPluginModules(layout.pluginModulesToPublish, "productProperties.productLayout.pluginModulesToPublish")

    if (!layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
      buildContext.messages.warning("layout.buildAllCompatiblePlugins option isn't enabled. Value of " +
                                    "layout.compatiblePluginsToIgnore property will be ignored ($layout.compatiblePluginsToIgnore)")
    }
    if (layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
      checkPluginModules(layout.compatiblePluginsToIgnore, "productProperties.productLayout.compatiblePluginsToIgnore")
    }

    if (!buildContext.shouldBuildDistributions() && layout.buildAllCompatiblePlugins) {
      buildContext.messages.warning("Distribution is not going to build. Hence all compatible plugins won't be built despite " +
                                    "layout.buildAllCompatiblePlugins option is enabled. layout.pluginModulesToPublish will be used ($layout.pluginModulesToPublish)")
    }
    if (layout.prepareCustomPluginRepositoryForPublishedPlugins && layout.pluginModulesToPublish.isEmpty() &&
        !layout.buildAllCompatiblePlugins) {
      buildContext.messages.error("productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins option is enabled but no pluginModulesToPublish are specified")
    }

    checkModules(layout.productApiModules, "productProperties.productLayout.productApiModules")
    checkModules(layout.productImplementationModules, "productProperties.productLayout.productImplementationModules")
    checkModules(layout.additionalPlatformJars.values(), "productProperties.productLayout.additionalPlatformJars")
    checkModules(layout.moduleExcludes.keySet(), "productProperties.productLayout.moduleExcludes")
    checkModules(layout.mainModules, "productProperties.productLayout.mainModules")
    checkProjectLibraries(layout.projectLibrariesToUnpackIntoMainJar, "productProperties.productLayout.projectLibrariesToUnpackIntoMainJar")
    def allBundledPlugins = layout.bundledPluginModules as Set<String>
    nonTrivialPlugins.findAll { allBundledPlugins.contains(it.mainModule) }.each { plugin ->
      checkModules(plugin.moduleJars.values(), "'$plugin.mainModule' plugin")
      checkModules(plugin.moduleExcludes.keySet(), "'$plugin.mainModule' plugin")
      checkProjectLibraries(plugin.includedProjectLibraries.collect {it.libraryName}, "'$plugin.mainModule' plugin")
      checkArtifacts(plugin.includedArtifacts.keySet(), "'$plugin.mainModule' plugin")
    }
  }

  private void checkModules(Collection<String> modules, String fieldName) {
    if (modules != null) {
      def unknownModules = modules.findAll {buildContext.findModule(it) == null}
      if (!unknownModules.empty) {
        buildContext.messages.error("The following modules from $fieldName aren't found in the project: $unknownModules")
      }
    }
  }

  private void checkProjectLibraries(Collection<String> names, String fieldName) {
    def unknownLibraries = names.findAll {buildContext.project.libraryCollection.findLibrary(it) == null}
    if (!unknownLibraries.empty) {
      buildContext.messages.error("The following libraries from $fieldName aren't found in the project: $unknownLibraries")
    }
  }

  private void checkArtifacts(Collection<String> names, String fieldName) {
    def unknownArtifacts = names - JpsArtifactService.instance.getArtifacts(buildContext.project).collect {it.name}
    if (!unknownArtifacts.empty) {
      buildContext.messages.error("The following artifacts from $fieldName aren't found in the project: $unknownArtifacts")
    }
  }

  private void checkPluginModules(List<String> pluginModules, String fieldName) {
    if (pluginModules == null) {
      return
    }
    checkModules(pluginModules, fieldName)
    def unknownBundledPluginModules = pluginModules.findAll { buildContext.findFileInModuleSources(it, "META-INF/plugin.xml") == null }
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

        //wait until all tasks finishes
        futures.each {
          try {
            it.get()
          }
          catch (Throwable ignore) {
          }
        }

        futures.collect { it.get() }
      }
    }
    catch (ExecutionException e) {
      throw e.cause
    }
    finally {
      buildContext.messages.onAllForksFinished()
    }
    results
  }

  @Override
  void buildUpdaterJar() {
    new LayoutBuilder(buildContext, false).layout(buildContext.paths.artifacts) {
      jar("updater.jar") {
        module("intellij.platform.updater")
      }
    }
  }

  @Override
  void buildFullUpdaterJar() {
    String updaterModule = "intellij.platform.updater"
    def libraryFiles = JpsJavaExtensionService.dependencies(buildContext.findRequiredModule(updaterModule)).productionOnly().runtimeOnly().libraries.collectMany {
      it.getFiles(JpsOrderRootType.COMPILED)
    }
    new LayoutBuilder(buildContext, false).layout(buildContext.paths.artifacts) {
      jar("updater-full.jar") {
        module(updaterModule)
        libraryFiles.each { file ->
          ant.zipfileset(src: file.absolutePath)
        }
      }
    }
  }

  @Override
  void runTestBuild() {
    checkProductProperties()
    def patchedApplicationInfo = patchApplicationInfo()
    def distributionJARsBuilder = compileModulesForDistribution(patchedApplicationInfo)
    distributionJARsBuilder.buildJARs()
    distributionJARsBuilder.buildInternalUtilities()
    if (buildContext.productProperties.scrambleMainJar) {
      scramble()
    }
    layoutShared()
  }

  @Override
  void buildUnpackedDistribution(String targetDirectory, boolean includeBinAndRuntime) {
    buildContext.paths.distAll = targetDirectory
    OsFamily currentOs = SystemInfo.isWindows ? OsFamily.WINDOWS :
                         SystemInfo.isMac ? OsFamily.MACOS :
                         SystemInfo.isLinux ? OsFamily.LINUX : null
    if (currentOs == null) {
      buildContext.messages.error("Update from source isn't supported for '$SystemInfo.OS_NAME'")
    }
    buildContext.options.targetOS = currentOs.osId

    setupBundledMaven()
    def patchedApplicationInfo = patchApplicationInfo()
    compileModulesForDistribution(patchedApplicationInfo).buildJARs()
    if (includeBinAndRuntime) {
      setupJBre()
    }
    layoutShared()

    if (includeBinAndRuntime) {
      def propertiesFile = patchIdeaPropertiesFile()
      OsSpecificDistributionBuilder builder
      switch (currentOs) {
        case OsFamily.WINDOWS:
          builder = new WindowsDistributionBuilder(buildContext, buildContext.windowsDistributionCustomizer, propertiesFile, patchedApplicationInfo)
          break
        case OsFamily.LINUX:
          builder = new LinuxDistributionBuilder(buildContext, buildContext.linuxDistributionCustomizer, propertiesFile)
          break
        case OsFamily.MACOS:
          builder = new MacDistributionBuilder(buildContext, buildContext.macDistributionCustomizer, propertiesFile)
          break
      }
      builder.copyFilesForOsDistribution(targetDirectory)
      def jbrTargetDir = buildContext.bundledJreManager.extractJre(currentOs)
      if (currentOs == OsFamily.WINDOWS) {
        buildContext.ant.move(todir: targetDirectory) {
          fileset(dir: jbrTargetDir)
        }
      }
      else {
        buildContext.ant.exec(executable: '/bin/sh', failOnError: true) {
          arg(value: '-c')
          arg(value: "mv \"$jbrTargetDir\"/* \"$targetDirectory\"")
        }
      }

      def executableFilesPatterns = builder.generateExecutableFilesPatterns(true)
      buildContext.ant.chmod(perm: "755") {
        fileset(dir: targetDirectory) {
          executableFilesPatterns.each {
            include(name: it)
          }
        }
      }
    }
    else {
      unpackPty4jNative(buildContext, targetDirectory, null)
    }
  }

  private abstract static class BuildTaskRunnable<V> {
    final String taskName

    BuildTaskRunnable(String name) {
      taskName = name
    }

    abstract V run(BuildContext context)
  }
}