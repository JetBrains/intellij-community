// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.Formats
import com.intellij.openapi.util.text.Strings
import groovy.io.FileType
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.*
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.java.*
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.*
import java.util.function.Function

@CompileStatic
final class BuildTasksImpl extends BuildTasks {
  final BuildContext buildContext

  BuildTasksImpl(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  @Override
  @CompileStatic(TypeCheckingMode.SKIP)
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
  @CompileStatic(TypeCheckingMode.SKIP)
  void zipSourcesOfModules(Collection<String> modules, Path targetFile) {
    buildContext.executeStep("Build sources of modules archive", BuildOptions.SOURCES_ARCHIVE_STEP) {
      buildContext.messages.progress("Building archive of ${modules.size()} modules to $targetFile")
      Files.createDirectories(targetFile.parent)
      Files.deleteIfExists(targetFile)
      buildContext.ant.zip(destfile: targetFile) {
        for (String moduleName in modules) {
          JpsModule module = buildContext.findModule(moduleName)
          if (module == null) {
            buildContext.messages.error("Cannot build sources archive: '$moduleName' module doesn't exist")
          }
          for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> root in module.getSourceRoots(JavaSourceRootType.SOURCE)) {
            buildContext.ant.zipfileset(dir: root.file.absolutePath,
                                        prefix: root.properties.packagePrefix.replace('.', '/'), erroronmissingdir: false)
          }
          for (JpsTypedModuleSourceRoot<JavaResourceRootProperties> root in module.getSourceRoots(JavaResourceRootType.RESOURCE)) {
            buildContext.ant.zipfileset(dir: root.file.absolutePath, prefix: root.properties.relativeOutputPath, erroronmissingdir: false) {
              exclude(name: "**/*.png")
            }
          }
        }
      }

      buildContext.notifyArtifactBuilt(targetFile)
    }
  }

  /**
   * Build a list with modules that the IDE will provide for plugins.
   */
  private void buildProvidedModulesList(Path targetFile, List<String> modules) {
    buildContext.executeStep("Build provided modules list", BuildOptions.PROVIDED_MODULES_LIST_STEP, {
      buildContext.messages.progress("Building provided modules list for ${modules.size()} modules")
      buildContext.messages.debug("Building provided modules list for the following modules: $modules")
      FileUtil.delete(targetFile)
      // start the product in headless mode using com.intellij.ide.plugins.BundledPluginsLister
      runApplicationStarter(buildContext, buildContext.paths.tempDir.resolve("builtinModules"), modules, ["listBundledPlugins", targetFile.toString()])
      if (!Files.exists(targetFile)) {
        buildContext.messages.error("Failed to build provided modules list: $targetFile doesn't exist")
      }
      buildContext.notifyArtifactWasBuilt(targetFile)
    })
  }

  static void runApplicationStarter(@NotNull BuildContext context,
                                    @NotNull Path tempDir,
                                    List<String> modules,
                                    List<String> arguments,
                                    Map<String, Object> systemProperties = Collections.emptyMap(),
                                    List<String> vmOptions = List.of("-Xmx512m"),
                                    List<String> pluginsToDisable = Collections.emptyList(),
                                    long timeoutMillis = TimeUnit.MINUTES.toMillis(10L)) {
    Files.createDirectories(tempDir)

    Set<String> ideClasspath = new LinkedHashSet<String>()
    context.messages.debug("Collecting classpath to run application starter '${arguments.first()}:")
    for (moduleName in modules) {
      for (pathElement in context.getModuleRuntimeClasspath(context.findRequiredModule(moduleName), false)) {
        if (ideClasspath.add(pathElement)) {
          context.messages.debug(" $pathElement from $moduleName")
        }
      }
    }

    List<String> jvmArgs = new ArrayList<>(BuildUtils.propertiesToJvmArgs(new HashMap<String, Object>([
      "idea.home.path"   : context.paths.projectHome,
      "idea.system.path" : "${FileUtilRt.toSystemIndependentName(tempDir.toString())}/system",
      "idea.config.path" : "${FileUtilRt.toSystemIndependentName(tempDir.toString())}/config"
    ])))
    if (context.productProperties.platformPrefix != null) {
      //noinspection SpellCheckingInspection
      jvmArgs.add("-Didea.platform.prefix=" + context.productProperties.platformPrefix)
    }
    jvmArgs.addAll(BuildUtils.propertiesToJvmArgs(systemProperties))
    jvmArgs.addAll(vmOptions)

    List<Path> additionalPluginPaths = context.productProperties.getAdditionalPluginPaths(context)
    for (Path pluginPath : additionalPluginPaths) {
      File libFile = pluginPath.resolve("lib").toFile()
      for (String jarName : libFile.list { _, name -> FileUtil.extensionEquals(name, "jar") }) {
        File jarFile = new File(libFile, jarName)
        if (ideClasspath.add(jarFile.absolutePath)) {
          context.messages.debug("$jarFile from plugin ${libFile.parentFile.name}")
        }
      }
    }

    disableCompatibleIgnoredPlugins(context, tempDir.resolve("config"), pluginsToDisable)

    BuildHelper.runJava(
      context,
      "com.intellij.idea.Main",
      arguments,
      jvmArgs,
      ideClasspath,
      timeoutMillis)
  }

  private static void disableCompatibleIgnoredPlugins(@NotNull BuildContext context,
                                                      @NotNull Path configDir,
                                                      @NotNull List<String> pluginsToDisable) {
    Set<String> toDisable = new HashSet<>(pluginsToDisable)
    for (String moduleName : context.productProperties.productLayout.compatiblePluginsToIgnore) {
      Path pluginXml = context.findFileInModuleSources(moduleName, "META-INF/plugin.xml")
      toDisable.add(JDOMUtil.load(pluginXml).getChildTextTrim("id"))
    }
    if (!toDisable.isEmpty()) {
      Files.createDirectories(configDir)
      Files.writeString(configDir.resolve("disabled_plugins.txt"), String.join("\n", toDisable))
    }
  }

  private Path patchIdeaPropertiesFile() {
    StringBuilder builder = new StringBuilder(Files.readString(buildContext.paths.communityHomeDir.resolve("bin/idea.properties")))

    buildContext.productProperties.additionalIDEPropertiesFilePaths.each {
      builder.append('\n').append(Files.readString(Paths.get(it)))
    }

    //todo[nik] introduce special systemSelectorWithoutVersion instead?
    String settingsDir = buildContext.systemSelector.replaceFirst("\\d+(\\.\\d+)?", "")
    String temp = builder.toString()
    builder.setLength(0)
    builder.append(BuildUtils.replaceAll(temp, ["settings_dir": settingsDir], "@@"))

    if (buildContext.applicationInfo.isEAP) {
      builder.append("""
#-----------------------------------------------------------------------
# Change to 'disabled' if you don't want to receive instant visual notifications
# about fatal errors that happen to an IDE or plugins installed.
#-----------------------------------------------------------------------
idea.fatal.error.notification=enabled
""")
    }
    else {
      builder.append("""
#-----------------------------------------------------------------------
# Change to 'enabled' if you want to receive instant visual notifications
# about fatal errors that happen to an IDE or plugins installed.
#-----------------------------------------------------------------------
idea.fatal.error.notification=disabled
""")
    }

    Path propertiesFile = buildContext.paths.tempDir.resolve("idea.properties")
    Files.writeString(propertiesFile, builder)
    return propertiesFile
  }

  @NotNull Path patchApplicationInfo() {
    Path sourceFile = BuildContextImpl.findApplicationInfoInSources(buildContext.project, buildContext.productProperties, buildContext.messages)
    Path targetFile = Paths.get(buildContext.paths.temp).resolve(sourceFile.fileName)
    def date = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("uuuuMMddHHmm"))

    def artifactsServer = buildContext.proprietaryBuildTools.artifactsServer
    def builtinPluginsRepoUrl = ""
    if (artifactsServer != null && buildContext.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
      builtinPluginsRepoUrl = artifactsServer.urlToArtifact(buildContext, "${buildContext.applicationInfo.productCode}-plugins/plugins.xml")
      if (builtinPluginsRepoUrl.startsWith("http:")) {
        buildContext.messages.error("Insecure artifact server: " + builtinPluginsRepoUrl)
      }
    }
    BuildUtils.copyAndPatchFile(sourceFile, targetFile,
                                ["BUILD_NUMBER": buildContext.fullBuildNumber, "BUILD_DATE": date, "BUILD": buildContext.buildNumber,
                                "BUILTIN_PLUGINS_URL": builtinPluginsRepoUrl ?: ""])
    return targetFile
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void layoutShared() {
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
        Path from = findBrandingResource(buildContext.applicationInfo.svgRelativePath)
        Path to = buildContext.paths.distAllDir.resolve("bin/${buildContext.productProperties.baseFileName}.svg")
        Files.createDirectories(to.parent)
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
      }

      buildContext.productProperties.copyAdditionalFiles(buildContext, buildContext.paths.distAll)
    }
  }

  static void generateBuildTxt(@NotNull BuildContext buildContext, @NotNull Path targetDirectory) {
    Files.writeString(targetDirectory.resolve("build.txt"), buildContext.fullBuildNumber)
  }

  private @NotNull Path findBrandingResource(@NotNull String relativePath) {
    String normalizedRelativePath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath
    Path inModule = buildContext.findFileInModuleSources(buildContext.productProperties.applicationInfoModule, normalizedRelativePath)
    if (inModule != null) {
      return inModule
    }

    for (String brandingResourceDir : buildContext.productProperties.brandingResourcePaths) {
      Path file = Paths.get(brandingResourceDir, normalizedRelativePath)
      if (Files.exists(file)) {
        return file
      }
    }
    buildContext.messages.error("Cannot find '$normalizedRelativePath' in sources of '$buildContext.productProperties.applicationInfoModule' and in $buildContext.productProperties.brandingResourcePaths")
    return null
  }

  private void copyLogXml() {
    Path src = buildContext.paths.communityHomeDir.resolve("bin/log.xml")
    Path dst = buildContext.paths.distAllDir.resolve("bin/log.xml")
    Files.createDirectories(dst.parent)
    Files.newBufferedWriter(dst).withCloseable {
      src.filterLine { String line -> !line.contains('appender-ref ref="CONSOLE-WARN"') }.writeTo(it)
    }
  }

  private static @NotNull BuildTaskRunnable<Path> createDistributionForOsTask(@NotNull String taskName,
                                                                              @NotNull Function<BuildContext, OsSpecificDistributionBuilder> factory) {
    return BuildTaskRunnable.<Path>taskWithResult(taskName) { BuildContext context ->
      OsSpecificDistributionBuilder builder = factory.apply(context)
      if (builder == null || !context.shouldBuildDistributionForOS(builder.targetOs.osId)) {
        return null
      }

      return context.messages.block("Build $builder.targetOs.osName Distribution") {
        Path osSpecificDistDirectory = DistributionJARsBuilder.getOsSpecificDistDirectory(builder.targetOs, context)
        builder.buildArtifacts(osSpecificDistDirectory)
        osSpecificDistDirectory
      }
    }
  }

  @Override
  void compileModulesFromProduct() {
    checkProductProperties()
    Path patchedApplicationInfo = patchApplicationInfo()
    compileModulesForDistribution(patchedApplicationInfo)
  }

  private DistributionJARsBuilder compileModulesForDistribution(@NotNull Path patchedApplicationInfo) {
    def productLayout = buildContext.productProperties.productLayout
    List<String> moduleNames = DistributionJARsBuilder.getModulesToCompile(buildContext)
    def mavenArtifacts = buildContext.productProperties.mavenArtifacts
    compileModules(moduleNames + ((buildContext.proprietaryBuildTools.scrambleTool?.additionalModulesToCompile ?: Collections.emptyList()) as List<String>) +
                   productLayout.mainModules + mavenArtifacts.additionalModules + mavenArtifacts.proprietaryModules,
                   buildContext.productProperties.modulesToCompileTests)

    def pluginsToPublish = new LinkedHashSet<>(
      DistributionJARsBuilder.getPluginsByModules(buildContext, buildContext.productProperties.productLayout.pluginModulesToPublish))

    if (buildContext.shouldBuildDistributions()) {
      Path providedModulesFile = Paths.get(buildContext.paths.artifacts, "${buildContext.applicationInfo.productCode}-builtinModules.json")
      buildProvidedModulesList(providedModulesFile, moduleNames)
      if (buildContext.productProperties.productLayout.buildAllCompatiblePlugins) {
        if (!buildContext.options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
          pluginsToPublish.addAll(new PluginsCollector(buildContext, providedModulesFile.toString()).collectCompatiblePluginsToPublish())
        }
        else {
          buildContext.messages.info("Skipping collecting compatible plugins because PROVIDED_MODULES_LIST_STEP was skipped")
        }
      }
    }
    return compilePlatformAndPluginModules(patchedApplicationInfo, pluginsToPublish)
  }

  private DistributionJARsBuilder compilePlatformAndPluginModules(@NotNull Path patchedApplicationInfo, @NotNull Set<PluginLayout> pluginsToPublish) {
    def distributionJARsBuilder = new DistributionJARsBuilder(buildContext, patchedApplicationInfo, pluginsToPublish)
    compileModules(distributionJARsBuilder.getModulesForPluginsToPublish())

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

    Path patchedApplicationInfo = patchApplicationInfo()
    logFreeDiskSpace("before compilation")
    DistributionJARsBuilder distributionJARsBuilder = compileModulesForDistribution(patchedApplicationInfo)
    logFreeDiskSpace("after compilation")
    def mavenArtifacts = buildContext.productProperties.mavenArtifacts
    if (mavenArtifacts.forIdeModules || !mavenArtifacts.additionalModules.isEmpty() || !mavenArtifacts.proprietaryModules.isEmpty()) {
      buildContext.executeStep("Generate Maven artifacts", BuildOptions.MAVEN_ARTIFACTS_STEP) {
        def mavenArtifactsBuilder = new MavenArtifactsBuilder(buildContext)
        List<String> ideModuleNames
        if (mavenArtifacts.forIdeModules) {
          def bundledPlugins = buildContext.productProperties.productLayout.bundledPluginModules as Set<String>
          ideModuleNames = distributionJARsBuilder.platformModules + buildContext.productProperties.productLayout.getIncludedPluginModules(bundledPlugins)
        }
        else {
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
        DistributionJARsBuilder.buildAdditionalArtifacts(buildContext, distributionJARsBuilder.projectStructureMapping)
        scramble(buildContext)
        DistributionJARsBuilder.reorderJars(buildContext)
      }
      else {
        buildContext.messages.info("Skipped building product distributions because 'intellij.build.target.os' property is set to '$BuildOptions.OS_NONE'")
        DistributionJARsBuilder.reorderJars(buildContext)
        DistributionJARsBuilder.createBuildSearchableOptionsTask(distributionJARsBuilder.getModulesForPluginsToPublish()).execute(buildContext)
        distributionJARsBuilder.buildNonBundledPlugins(true)
      }
    }

    if (buildContext.shouldBuildDistributions()) {
      setupJBre()
      layoutShared()

      Path propertiesFile = patchIdeaPropertiesFile()
      List<BuildTaskRunnable<Path>> tasks = new ArrayList<>()
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

      List<Path> paths = runInParallel(tasks, buildContext).findAll { it != null }

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
              new ClassVersionChecker(checkerConfig).checkVersions(buildContext, Paths.get(monsterZip))
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
    checkPluginModules(mainPluginModules, "mainPluginModules", buildContext.productProperties.productLayout.allNonTrivialPlugins)
    copyDependenciesFile()
    def pluginsToPublish = new LinkedHashSet<PluginLayout>(
      DistributionJARsBuilder.getPluginsByModules(buildContext, mainPluginModules))
    def distributionJARsBuilder = compilePlatformAndPluginModules(patchApplicationInfo(), pluginsToPublish)
    DistributionJARsBuilder.createBuildSearchableOptionsTask(distributionJARsBuilder.getModulesForPluginsToPublish()).execute(buildContext)
    distributionJARsBuilder.buildNonBundledPlugins(true)
  }

  @Override
  void generateProjectStructureMapping(File targetFile) {
    def jarsBuilder = new DistributionJARsBuilder(buildContext, patchApplicationInfo())
    jarsBuilder.generateProjectStructureMapping(targetFile)
  }

  private void setupJBre(String targetArch = null) {
    logFreeDiskSpace("before downloading JREs")
    String[] args = [
      'setupJbre', "-Dintellij.build.target.os=$buildContext.options.targetOS",
      "-Dintellij.build.bundled.jre.version=$buildContext.options.bundledJreVersion"
    ]
    if (targetArch != null) {
      args += "-Dintellij.build.target.arch=" + targetArch
    }
    String prefix = System.getProperty("intellij.build.bundled.jre.prefix")
    if (prefix != null) {
      args += "-Dintellij.build.bundled.jre.prefix=" + prefix
    }
    if (buildContext.options.bundledJreBuild != null) {
      args += "-Dintellij.build.bundled.jre.build=" + buildContext.options.bundledJreBuild
    }
    buildContext.gradle.run('Setting up JetBrains JREs', args)
    logFreeDiskSpace("after downloading JREs")
  }

  private void setupBundledMaven() {
    logFreeDiskSpace("before downloading Maven")
    buildContext.gradle.run('Setting up Bundled Maven', 'setupBundledMaven')
    logFreeDiskSpace("after downloading Maven")
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  static def unpackPty4jNative(BuildContext buildContext, @NotNull Path distDir, String pty4jOsSubpackageName) {
    def pty4jNativeDir = "$distDir/lib/pty4j-native"
    def nativePkg = "resources/com/pty4j/native"
    def includedNativePkg = Strings.trimEnd(nativePkg + "/" + Strings.notNullize(pty4jOsSubpackageName), '/')
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

  //dbus-java is used only on linux for KWallet integration.
  //It relies on native libraries, causing notarization issues on mac.
  //So it is excluded from all distributions and manually re-included on linux.
  static void addDbusJava(BuildContext buildContext, @NotNull Path distDir) {
    JpsLibrary library = buildContext.findModule("intellij.platform.credentialStore").libraryCollection.findLibrary("dbus-java")
    Path destLibDir = distDir.resolve("lib")
    Files.createDirectories(destLibDir)
    for (File file : library.getFiles(JpsOrderRootType.COMPILED)) {
      Files.copy(file.toPath(), destLibDir.resolve(file.name), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private void logFreeDiskSpace(String phase) {
    CompilationContextImpl.logFreeDiskSpace(buildContext.messages, buildContext.paths.buildOutputRoot, phase)
  }


  private void copyDependenciesFile() {
    File outputFile = new File(buildContext.paths.artifacts, "dependencies.txt")
    FileUtil.copy(buildContext.dependenciesProperties.file, outputFile)
    buildContext.notifyArtifactWasBuilt(outputFile.toPath())
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void scramble(BuildContext buildContext) {
    if (!buildContext.productProperties.scrambleMainJar) {
      return
    }

    if (buildContext.proprietaryBuildTools.scrambleTool == null) {
      buildContext.messages.warning("Scrambling skipped: 'scrambleTool' isn't defined")
    }
    else {
      buildContext.proprietaryBuildTools.scrambleTool.scramble(buildContext.productProperties.productLayout.mainJarName, buildContext)
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
    checkPluginDuplicates(nonTrivialPlugins)

    checkPluginModules(layout.bundledPluginModules, "productProperties.productLayout.bundledPluginModules", nonTrivialPlugins)
    checkPluginModules(layout.pluginModulesToPublish, "productProperties.productLayout.pluginModulesToPublish", nonTrivialPlugins)

    if (!layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
      buildContext.messages.warning("layout.buildAllCompatiblePlugins option isn't enabled. Value of " +
                                    "layout.compatiblePluginsToIgnore property will be ignored ($layout.compatiblePluginsToIgnore)")
    }
    if (layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
      checkPluginModules(layout.compatiblePluginsToIgnore, "productProperties.productLayout.compatiblePluginsToIgnore", nonTrivialPlugins)
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
    nonTrivialPlugins.each { plugin ->
      checkModules(plugin.moduleJars.values(), "'$plugin.mainModule' plugin")
      checkModules(plugin.moduleExcludes.keySet(), "'$plugin.mainModule' plugin")
      checkProjectLibraries(plugin.includedProjectLibraries.collect {it.libraryName}, "'$plugin.mainModule' plugin")
      checkArtifacts(plugin.includedArtifacts.keySet(), "'$plugin.mainModule' plugin")
    }
  }

  private void checkPluginDuplicates(List<PluginLayout> nonTrivialPlugins) {
    def pluginsGroupedByMainModule = nonTrivialPlugins.groupBy { it.mainModule }.values()
    for (List<PluginLayout> duplicatedPlugins : pluginsGroupedByMainModule) {
      if (duplicatedPlugins.size() > 1) {
        buildContext.messages.warning("Duplicated plugin description in productLayout.allNonTrivialPlugins: ${duplicatedPlugins[0].mainModule}")
      }
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

  private void checkPluginModules(List<String> pluginModules, String fieldName, List<PluginLayout> pluginLayoutList) {
    if (pluginModules == null) {
      return
    }
    checkModules(pluginModules, fieldName)

    def unspecifiedLayoutPluginModules = pluginModules.findAll { mainModuleName ->
      pluginLayoutList.find { it.mainModule == mainModuleName } == null
    }
    if (!unspecifiedLayoutPluginModules.empty) {
      buildContext.messages.info("No plugin layout specified in productProperties.productLayout.allNonTrivialPlugins for following plugin main modules. " +
                                    "Assuming simple layout. Modules list: $unspecifiedLayoutPluginModules")
    }

    List<String> unknownBundledPluginModules = pluginModules.findAll { buildContext.findFileInModuleSources(it, "META-INF/plugin.xml") == null }
    if (!unknownBundledPluginModules.empty) {
      buildContext.messages.error(
        "The following modules from $fieldName don't contain META-INF/plugin.xml file and aren't specified as optional plugin modules " +
        "in productProperties.productLayout.allNonTrivialPlugins: $unknownBundledPluginModules. "
      )
    }
  }

  private void checkPaths(@NotNull Collection<String> paths, String fieldName) {
    Collection<String> nonExistingFiles = paths.findAll { it != null && !Files.exists(Paths.get(it)) }
    if (!nonExistingFiles.empty) {
      buildContext.messages.error("$fieldName contains non-existing path${nonExistingFiles.size() > 1 ? "s" : ""}: ${String.join(",", nonExistingFiles)}")
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

  static <V> List<V> runInParallel(List<BuildTaskRunnable<V>> tasks, BuildContext buildContext) {
    if (tasks.empty) return Collections.emptyList()
    if (!buildContext.options.runBuildStepsInParallel) {
      return tasks.collect {
        it.execute(buildContext)
      }
    }

    try {
      return buildContext.messages.block("Run parallel tasks") {
        buildContext.messages.info("Started ${tasks.size()} tasks in parallel: ${tasks.collect { it.stepId }}")
        ExecutorService executorService = Executors.newWorkStealingPool()
        List<Pair<BuildTaskRunnable<V>, Future<Pair<V, Long>>>> futures = new ArrayList<Pair<BuildTaskRunnable<V>, Future<Pair<V, Long>>>>(tasks.size())
        for (BuildTaskRunnable<V> task : tasks) {
          futures.add(new Pair<>(task, executorService.submit(createTaskWrapper(task, buildContext.forkForParallelTask(task.stepId)))))
        }

        executorService.shutdown()

        // wait until all tasks finishes
        List<Throwable> errors = new ArrayList<>()

        List<V> results = new ArrayList<>(futures.size())
        for (Pair<BuildTaskRunnable<V>, Future<Pair<V, Long>>> item : futures) {
          try {
            Pair<V, Long> result = item.second.get()
            if (result == null) {
              throw new IllegalStateException("Result from build step wrapper must not be null")
            }

            results.add(result.first)
            buildContext.messages.info("'${item.first.stepId}' task successfully finished in ${Formats.formatDuration(result.second)}")
          }
          catch (Throwable t) {
            buildContext.messages.info("'${item.first.stepId}' task failed")
            errors.add(new Exception("Cannot execute task ${item.first.stepId}", t))
          }
        }

        if (errors.size() > 0) {
          Throwable aggregateException = errors.remove(0)
          for (error in errors) {
            aggregateException.addSuppressed(error)
          }

          // Will throw an exception
          buildContext.messages.error("Some tasks failed", aggregateException)
        }

        return results
      }
    }
    catch (ExecutionException e) {
      throw e.cause
    }
    finally {
      buildContext.messages.onAllForksFinished()
    }
  }

  private static <T> Callable<Pair<T, Long>> createTaskWrapper(BuildTaskRunnable<T> task, BuildContext buildContext) {
    return new Callable<Pair<T, Long>>() {
      @Override
      Pair<T, Long> call() throws Exception {
        long start = System.currentTimeMillis()
        buildContext.messages.onForkStarted()
        try {
          T result = task.execute(buildContext)
          long duration = System.currentTimeMillis() - start
          return new Pair<T, Long>(result, duration)
        }
        finally {
          buildContext.messages.onForkFinished()
        }
      }
    }
  }

  @Override
  @CompileStatic(TypeCheckingMode.SKIP)
  void buildUpdaterJar() {
    new LayoutBuilder(buildContext, false).layout(buildContext.paths.artifacts) {
      jar("updater.jar") {
        module("intellij.platform.updater")
      }
    }
  }

  @Override
  @CompileStatic(TypeCheckingMode.SKIP)
  void buildFullUpdaterJar() {
    String updaterModule = "intellij.platform.updater"
    List<File> libraryFiles = JpsJavaExtensionService.dependencies(buildContext.findRequiredModule(updaterModule))
      .productionOnly()
      .runtimeOnly()
      .libraries.collectMany {it.getFiles(JpsOrderRootType.COMPILED)}
    new LayoutBuilder(buildContext, false).layout(buildContext.paths.artifacts) {
      jar("updater-full.jar") {
        module(updaterModule)
        for (file in libraryFiles) {
          ant.zipfileset(src: file.absolutePath)
        }
      }
    }
  }

  @Override
  void runTestBuild() {
    checkProductProperties()
    Path patchedApplicationInfo = patchApplicationInfo()
    DistributionJARsBuilder distributionJARsBuilder = compileModulesForDistribution(patchedApplicationInfo)
    distributionJARsBuilder.buildJARs()
    DistributionJARsBuilder.buildInternalUtilities(buildContext)
    scramble(buildContext)
    DistributionJARsBuilder.reorderJars(buildContext)
    layoutShared()
    Map<String, String> checkerConfig = buildContext.productProperties.versionCheckerConfig
    if (checkerConfig != null) {
      new ClassVersionChecker(checkerConfig).checkVersions(buildContext, buildContext.paths.distAllDir)
    }
  }

  @Override
  @CompileStatic(TypeCheckingMode.SKIP)
  void buildUnpackedDistribution(@NotNull Path targetDirectory, boolean includeBinAndRuntime) {
    buildContext.paths.distAllDir = targetDirectory.toAbsolutePath().normalize()
    buildContext.paths.distAll = FileUtilRt.toSystemIndependentName(buildContext.paths.distAllDir.toString())
    OsFamily currentOs = SystemInfoRt.isWindows ? OsFamily.WINDOWS :
                         SystemInfoRt.isMac ? OsFamily.MACOS :
                         SystemInfoRt.isLinux ? OsFamily.LINUX : null
    if (currentOs == null) {
      buildContext.messages.error("Update from source isn't supported for '$SystemInfoRt.OS_NAME'")
    }
    buildContext.options.targetOS = currentOs.osId

    setupBundledMaven()
    Path patchedApplicationInfo = patchApplicationInfo()
    compileModulesForDistribution(patchedApplicationInfo).buildJARs(true)
    def osSpecificPlugins = DistributionJARsBuilder.getOsSpecificDistDirectory(currentOs, buildContext).resolve("plugins")
    if (Files.isDirectory(osSpecificPlugins)) {
      Files.newDirectoryStream(osSpecificPlugins).withCloseable { children ->
        children.each { Files.move(it, buildContext.paths.distAllDir.resolve("plugins").resolve(it.fileName)) }
      }
    }

    DistributionJARsBuilder.reorderJars(buildContext)
    JvmArchitecture arch = SystemInfo.isArm64 ? JvmArchitecture.aarch64 : SystemInfo.is64Bit ? JvmArchitecture.x64 : JvmArchitecture.x32
    if (includeBinAndRuntime) {
      setupJBre(arch.name())
    }
    layoutShared()

    if (includeBinAndRuntime) {
      Path propertiesFile = patchIdeaPropertiesFile()
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
      builder.copyFilesForOsDistribution(targetDirectory, arch)
      Path jbrTargetDir = buildContext.bundledJreManager.extractJre(currentOs)
      if (currentOs == OsFamily.WINDOWS) {
        buildContext.ant.move(todir: targetDirectory.toString()) {
          fileset(dir: jbrTargetDir.toString())
        }
      }
      else {
        BuildHelper.runProcess(buildContext, List.of("/bin/sh", "-c", "mv \"" + jbrTargetDir + "\"/* \"" + targetDirectory + '"'), null)
      }

      List<String> executableFilesPatterns = builder.generateExecutableFilesPatterns(true)
      buildContext.ant.chmod(perm: "755") {
        fileset(dir: targetDirectory.toString()) {
          for (String pattern in executableFilesPatterns) {
            include(name: pattern)
          }
        }
      }
    }
    else {
      copyDistFiles(buildContext, targetDirectory)
      unpackPty4jNative(buildContext, targetDirectory, null)
    }
  }

  static copyDistFiles(@NotNull BuildContext buildContext, @NotNull Path newDir) {
    Files.createDirectories(newDir)
    for (Pair<Path, String> item : buildContext.distFiles) {
      Path file = item.getFirst()

      Path dir = newDir.resolve(item.getSecond())
      Files.createDirectories(dir)
      Files.copy(file, dir.resolve(file.fileName), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  static void copyInspectScript(@NotNull BuildContext buildContext, @NotNull Path distBinDir) {
    String inspectScript = buildContext.productProperties.inspectCommandName
    if (inspectScript != "inspect") {
      Path targetPath = distBinDir.resolve("${inspectScript}.sh")
      Files.move(distBinDir.resolve("inspect.sh"), targetPath, StandardCopyOption.REPLACE_EXISTING)
      buildContext.patchInspectScript(targetPath)
    }
  }
}
