// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.Formats
import com.intellij.util.io.Decompressor
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.system.CpuArch
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.idea.maven.aether.ProgressConsumer
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.JarPackager.Companion.getLibraryName
import org.jetbrains.intellij.build.impl.TracerManager.finish
import org.jetbrains.intellij.build.impl.TracerManager.spanBuilder
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.LibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping
import org.jetbrains.intellij.build.tasks.*
import org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder.getLocalArtifactRepositoryRoot
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.*
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.lang.reflect.UndeclaredThrowableException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors

internal fun copyInspectScript(context: BuildContext, distBinDir: Path) {
  val inspectScript = context.productProperties.inspectCommandName
  if (inspectScript != "inspect") {
    val targetPath = distBinDir.resolve("$inspectScript.sh")
    Files.move(distBinDir.resolve("inspect.sh"), targetPath, StandardCopyOption.REPLACE_EXISTING)
    context.patchInspectScript(targetPath)
  }
}

internal fun copyDistFiles(buildContext: BuildContext, newDir: Path) {
  Files.createDirectories(newDir)
  for ((file, value) in buildContext.getDistFiles()) {
    val dir = newDir.resolve(value)
    Files.createDirectories(dir)
    Files.copy(file, dir.resolve(file.fileName), StandardCopyOption.REPLACE_EXISTING)
  }
}

internal fun generateBuildTxt(buildContext: BuildContext, targetDirectory: Path) {
  Files.writeString(targetDirectory.resolve("build.txt"), buildContext.fullBuildNumber)
}

internal fun addDbusJava(context: CompilationContext, libDir: Path): List<String> {
  val library = context.findRequiredModule("intellij.platform.credentialStore").libraryCollection.findLibrary("dbus-java")!!
  val extraJars = ArrayList<String>()
  Files.createDirectories(libDir)
  for (file in library.getFiles(JpsOrderRootType.COMPILED)) {
    BuildHelper.copyFileToDir(file.toPath(), libDir)
    extraJars.add(file.name)
  }
  return extraJars
}

internal fun unpackPty4jNative(buildContext: BuildContext, distDir: Path, pty4jOsSubpackageName: String?): Path {
  val pty4jNativeDir = distDir.resolve("lib/pty4j-native")
  val nativePkg = "resources/com/pty4j/native"
  for (file in buildContext.project.libraryCollection.findLibrary("pty4j")!!.getFiles(JpsOrderRootType.COMPILED)) {
    val tempDir = Files.createTempDirectory(buildContext.paths.tempDir, file.name)
    try {
      Decompressor.Zip(file).withZipExtensions().extract(tempDir)
      val nativeDir = tempDir.resolve(nativePkg)
      if (Files.isDirectory(nativeDir)) {
        for (child in nativeDir.toFile().listFiles()!!) {
          val childName = child.name
          if (pty4jOsSubpackageName == null || pty4jOsSubpackageName == childName) {
            val dest = File(pty4jNativeDir.toFile(), childName)
            FileUtilRt.createDirectory(dest)
            FileUtil.copyDir(child, dest)
          }
        }
      }
    }
    finally {
      NioFiles.deleteRecursively(tempDir)
    }
  }

  val files = Files.newDirectoryStream(pty4jNativeDir).use { dirStream ->
    dirStream.asSequence().filter { Files.isRegularFile(it) }.toList()
  }
  if (files.isEmpty()) {
    buildContext.messages.error("Cannot layout pty4j native: no files extracted")
  }
  return pty4jNativeDir
}

class BuildTasksImpl(val context: BuildContext) : BuildTasks() {
  override fun zipSourcesOfModules(modules: Collection<String>, targetFile: Path, includeLibraries: Boolean) {
    zipSourcesOfModules(modules, targetFile, includeLibraries, context)
  }

  override fun compileModulesFromProduct() {
    checkProductProperties(context)
    compileModulesForDistribution(context)
  }

  private fun compileModulesForDistribution(context: BuildContext): DistributionBuilderState {
    val pluginsToPublish = getPluginsByModules(context.productProperties.productLayout.getPluginModulesToPublish(), context)
    return compileModulesForDistribution(pluginsToPublish, context)
  }

  override fun buildDistributions() {
    try {
      spanBuilder("build distributions").startSpan().useWithScope {
        doBuildDistributions(context)
      }
    }
    catch (e: Throwable) {
      try {
        finish()
      }
      catch (ignore: Throwable) {
      }
      throw e
    }
  }

  private fun doBuildDistributions(context: BuildContext) {
    checkProductProperties(context)
    copyDependenciesFile(context)
    logFreeDiskSpace("before compilation")
    val pluginsToPublish = getPluginsByModules(context.productProperties.productLayout.getPluginModulesToPublish(), context)
    val distributionState = compileModulesForDistribution(this.context)
    logFreeDiskSpace("after compilation")
    val mavenArtifacts = context.productProperties.mavenArtifacts
    if (mavenArtifacts.forIdeModules ||
        !mavenArtifacts.additionalModules.isEmpty() ||
        !mavenArtifacts.squashedModules.isEmpty() ||
        !mavenArtifacts.proprietaryModules.isEmpty()) {
      context.executeStep("generate maven artifacts", BuildOptions.MAVEN_ARTIFACTS_STEP) {
        val mavenArtifactsBuilder = MavenArtifactsBuilder(context)
        val moduleNames: MutableList<String> = ArrayList()
        if (mavenArtifacts.forIdeModules) {
          val bundledPlugins = java.util.Set.copyOf(context.productProperties.productLayout.bundledPluginModules)
          moduleNames.addAll(distributionState.platformModules)
          moduleNames.addAll(context.productProperties.productLayout.getIncludedPluginModules(bundledPlugins))
        }
        moduleNames.addAll(mavenArtifacts.additionalModules)
        if (!moduleNames.isEmpty()) {
          mavenArtifactsBuilder.generateMavenArtifacts(moduleNames, mavenArtifacts.squashedModules, "maven-artifacts")
        }
        if (!mavenArtifacts.proprietaryModules.isEmpty()) {
          mavenArtifactsBuilder.generateMavenArtifacts(mavenArtifacts.proprietaryModules, emptyList(), "proprietary-maven-artifacts")
        }
      }
    }
    context.messages.block("build platform and plugin JARs") {
      val distributionJARsBuilder = DistributionJARsBuilder(distributionState)
      if (context.shouldBuildDistributions()) {
        val projectStructureMapping = distributionJARsBuilder.buildJARs(context)
        buildAdditionalArtifacts(projectStructureMapping, context)
      }
      else {
        Span.current().addEvent("skip building product distributions because " +
                                "\"intellij.build.target.os\" property is set to \"${BuildOptions.OS_NONE}\"")
        distributionJARsBuilder.buildSearchableOptions(context, context.classpathCustomizer)
        distributionJARsBuilder.createBuildNonBundledPluginsTask(pluginsToPublish, true, null, context)!!.fork().join()
      }
      null
    }

    if (context.shouldBuildDistributions()) {
      layoutShared(context)
      val distDirs = buildOsSpecificDistributions(context)
      if (java.lang.Boolean.getBoolean("intellij.build.toolbox.litegen")) {
        @Suppress("SENSELESS_COMPARISON")
        if (context.buildNumber == null) {
          context.messages.warning("Toolbox LiteGen is not executed - it does not support SNAPSHOT build numbers")
        }
        else if (context.options.targetOS != BuildOptions.OS_ALL) {
          context.messages.warning("Toolbox LiteGen is not executed - it doesn't support installers are being built only for specific OS")
        }
        else {
          context.executeStep("build toolbox lite-gen links", BuildOptions.TOOLBOX_LITE_GEN_STEP) {
            val toolboxLiteGenVersion = System.getProperty("intellij.build.toolbox.litegen.version")
            if (toolboxLiteGenVersion == null) {
              context.messages.error("Toolbox Lite-Gen version is not specified!")
            }
            else {
              ToolboxLiteGen.runToolboxLiteGen(context.paths.buildDependenciesCommunityRoot, context.messages,
                                               toolboxLiteGenVersion, "/artifacts-dir=" + context.paths.artifacts,
                                               "/product-code=" + context.applicationInfo.productCode,
                                               "/isEAP=" + context.applicationInfo.isEAP.toString(),
                                               "/output-dir=" + context.paths.buildOutputRoot + "/toolbox-lite-gen")
            }
          }
        }
      }
      if (context.productProperties.buildCrossPlatformDistribution) {
        if (distDirs.size == SUPPORTED_DISTRIBUTIONS!!.size) {
          context.executeStep("build cross-platform distribution", BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP) {
            CrossPlatformDistributionBuilder.buildCrossPlatformZip(distDirs, context)
          }
        }
        else {
          Span.current().addEvent("skip building cross-platform distribution because some OS/arch-specific distributions were skipped")
        }
      }
    }
    logFreeDiskSpace("after building distributions")
  }

  override fun buildDmg(macZipDir: Path) {
    val createDistTasks = listOfNotNull(
      createDistributionForOsTask(OsFamily.MACOS, JvmArchitecture.x64) { context ->
        (context as BuildContextImpl)
          .setBuiltinModules(BuiltinModulesFileUtils.readBuiltinModulesFile(find(macZipDir, "builtinModules.json", context)))
        val macZip = find(macZipDir, "${JvmArchitecture.x64}.zip", context)
        (context.getOsDistributionBuilder(OsFamily.MACOS) as MacDistributionBuilder).buildAndSignDmgFromZip(macZip, JvmArchitecture.x64)
      },
      createDistributionForOsTask(OsFamily.MACOS, JvmArchitecture.aarch64) { context ->
        (context as BuildContextImpl)
          .setBuiltinModules(BuiltinModulesFileUtils.readBuiltinModulesFile(find(macZipDir, "builtinModules.json", context)))
        val macZip = find(macZipDir, "${JvmArchitecture.aarch64}.zip", context)
        (context.getOsDistributionBuilder(OsFamily.MACOS) as MacDistributionBuilder)
          .buildAndSignDmgFromZip(macZip, JvmArchitecture.aarch64)
      }
    )
    runInParallel(createDistTasks, context)
  }

  override fun buildNonBundledPlugins(mainPluginModules: List<String>) {
    checkProductProperties(context)
    checkPluginModules(mainPluginModules, "mainPluginModules", context.productProperties.productLayout.allNonTrivialPlugins)
    copyDependenciesFile(context)
    val pluginsToPublish = getPluginsByModules(mainPluginModules, context)
    val distributionJARsBuilder = DistributionJARsBuilder(compilePlatformAndPluginModules(pluginsToPublish, context))
    distributionJARsBuilder.buildSearchableOptions(context)
    distributionJARsBuilder.createBuildNonBundledPluginsTask(pluginsToPublish, true, null, context)!!.fork().join()
  }

  override fun generateProjectStructureMapping(targetFile: Path) {
    Files.createDirectories(context.paths.tempDir)
    val pluginLayoutRoot = Files.createTempDirectory(context.paths.tempDir, "pluginLayoutRoot")
    DistributionJARsBuilder(context).generateProjectStructureMapping(targetFile, context, pluginLayoutRoot)
  }

  private fun logFreeDiskSpace(phase: String) {
    CompilationContextImpl.logFreeDiskSpace(context.messages, context.paths.buildOutputDir, phase)
  }

  private fun checkProductProperties(context: BuildContext) {
    checkProductLayout()

    val properties = context.productProperties
    checkPaths2(properties.brandingResourcePaths, "productProperties.brandingResourcePaths")
    checkPaths2(properties.additionalIDEPropertiesFilePaths, "productProperties.additionalIDEPropertiesFilePaths")
    checkPaths2(properties.additionalDirectoriesWithLicenses, "productProperties.additionalDirectoriesWithLicenses")
    checkModules(properties.additionalModulesToCompile, "productProperties.additionalModulesToCompile")
    checkModules(properties.modulesToCompileTests, "productProperties.modulesToCompileTests")

    val winCustomizer = context.windowsDistributionCustomizer
    checkPaths(listOfNotNull(winCustomizer.icoPath), "productProperties.windowsCustomizer.icoPath")
    checkPaths(listOfNotNull(winCustomizer.icoPathForEAP), "productProperties.windowsCustomizer.icoPathForEAP")
    checkPaths(listOfNotNull(winCustomizer.installerImagesPath), "productProperties.windowsCustomizer.installerImagesPath")

    checkPaths(listOfNotNull(context.linuxDistributionCustomizer.iconPngPath), "productProperties.linuxCustomizer.iconPngPath")
    checkPaths(listOfNotNull(context.linuxDistributionCustomizer.iconPngPathForEAP), "productProperties.linuxCustomizer.iconPngPathForEAP")

    val macCustomizer = context.macDistributionCustomizer
    checkMandatoryField(macCustomizer.bundleIdentifier, "productProperties.macCustomizer.bundleIdentifier")
    checkMandatoryPath(macCustomizer.icnsPath, "productProperties.macCustomizer.icnsPath")
    checkPaths(listOfNotNull(macCustomizer.icnsPathForEAP), "productProperties.macCustomizer.icnsPathForEAP")
    checkMandatoryPath(macCustomizer.dmgImagePath, "productProperties.macCustomizer.dmgImagePath")
    checkPaths(listOfNotNull(macCustomizer.dmgImagePathForEAP), "productProperties.macCustomizer.dmgImagePathForEAP")

    checkModules(properties.mavenArtifacts.additionalModules, "productProperties.mavenArtifacts.additionalModules")
    checkModules(properties.mavenArtifacts.squashedModules, "productProperties.mavenArtifacts.squashedModules")
    if (context.productProperties.scrambleMainJar) {
      context.proprietaryBuildTools.scrambleTool?.let {
        checkModules(it.getNamesOfModulesRequiredToBeScrambled(), "ProprietaryBuildTools.scrambleTool.namesOfModulesRequiredToBeScrambled")
      }
    }
  }

  private fun checkProductLayout() {
    val layout = context.productProperties.productLayout
    // todo mainJarName type specified as not-null - does it work?
    @Suppress("SENSELESS_COMPARISON")
    if (layout.mainJarName == null) {
      context.messages.error("productProperties.productLayout.mainJarName is not specified")
    }

    val nonTrivialPlugins = layout.allNonTrivialPlugins
    checkPluginDuplicates(nonTrivialPlugins)
    checkPluginModules(layout.bundledPluginModules, "productProperties.productLayout.bundledPluginModules", nonTrivialPlugins)
    checkPluginModules(layout.getPluginModulesToPublish(), "productProperties.productLayout.pluginModulesToPublish", nonTrivialPlugins)
    checkPluginModules(layout.compatiblePluginsToIgnore, "productProperties.productLayout.compatiblePluginsToIgnore", nonTrivialPlugins)
    if (!layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
      context.messages.warning("layout.buildAllCompatiblePlugins option isn't enabled. Value of " +
                               "layout.compatiblePluginsToIgnore property will be ignored (" + layout.compatiblePluginsToIgnore.toString() +
                               ")")
    }
    if (layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
      checkPluginModules(layout.compatiblePluginsToIgnore, "productProperties.productLayout.compatiblePluginsToIgnore",
                         nonTrivialPlugins)
    }
    if (!context.shouldBuildDistributions() && layout.buildAllCompatiblePlugins) {
      context.messages.warning("Distribution is not going to build. Hence all compatible plugins won't be built despite " +
                               "layout.buildAllCompatiblePlugins option is enabled. layout.pluginModulesToPublish will be used (" +
                               layout.getPluginModulesToPublish().toString() +
                               ")")
    }
    if (layout.prepareCustomPluginRepositoryForPublishedPlugins &&
        layout.getPluginModulesToPublish().isEmpty() &&
        !layout.buildAllCompatiblePlugins) {
      context.messages.error("productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins option is enabled" +
                             " but no pluginModulesToPublish are specified")
    }
    checkModules(layout.productApiModules, "productProperties.productLayout.productApiModules")
    checkModules(layout.productImplementationModules, "productProperties.productLayout.productImplementationModules")
    checkModules(layout.additionalPlatformJars.values(), "productProperties.productLayout.additionalPlatformJars")
    checkModules(layout.moduleExcludes.keySet(), "productProperties.productLayout.moduleExcludes")
    checkModules(layout.mainModules, "productProperties.productLayout.mainModules")
    checkProjectLibraries(layout.projectLibrariesToUnpackIntoMainJar,
                          "productProperties.productLayout.projectLibrariesToUnpackIntoMainJar", context)
    for (plugin in nonTrivialPlugins) {
      checkBaseLayout(plugin as BaseLayout, "\'${plugin.mainModule}\' plugin", context)
    }
  }

  private fun checkBaseLayout(layout: BaseLayout, description: String, context: BuildContext) {
    checkModules(layout.getIncludedModuleNames(), "moduleJars in $description")
    checkArtifacts(layout.includedArtifacts.keys, "includedArtifacts in $description")
    checkModules(layout.resourcePaths.map { it.moduleName }, "resourcePaths in $description")
    checkModules(layout.moduleExcludes.keySet(), "moduleExcludes in $description")

    checkProjectLibraries(layout.includedProjectLibraries.map { it.libraryName }, "includedProjectLibraries in $description", context)

    for ((moduleName, libraryName) in layout.includedModuleLibraries) {
      checkModules(listOf(moduleName), "includedModuleLibraries in $description")
      if (!context.findRequiredModule(moduleName).libraryCollection.libraries.any { getLibraryName(it) == libraryName }) {
        context.messages.error("Cannot find library \'$libraryName\' in \'$moduleName\' (used in $description)")
      }
    }

    checkModules(layout.excludedModuleLibraries.keySet(), "excludedModuleLibraries in $description")
    for ((key, value) in layout.excludedModuleLibraries.entrySet()) {
      val libraries = context.findRequiredModule(key).libraryCollection.libraries
      for (libraryName in value) {
        if (!libraries.any { getLibraryName(it) == libraryName }) {
          context.messages.error("Cannot find library \'$libraryName\' in \'$key\' (used in \'excludedModuleLibraries\' in $description)")
        }
      }
    }

    checkProjectLibraries(layout.projectLibrariesToUnpack.values(), "projectLibrariesToUnpack in $description", context)
    checkModules(layout.modulesWithExcludedModuleLibraries, "modulesWithExcludedModuleLibraries in $description")
  }

  private fun checkPluginDuplicates(nonTrivialPlugins: List<PluginLayout>) {
    val pluginsGroupedByMainModule = nonTrivialPlugins.groupBy { it.mainModule }.values
    for (duplicatedPlugins in pluginsGroupedByMainModule) {
      if (duplicatedPlugins.size > 1) {
        context.messages.warning("Duplicated plugin description in productLayout.allNonTrivialPlugins: " +
                                 duplicatedPlugins.first().mainModule)
      }
    }
  }

  private fun checkModules(modules: Collection<String>?, fieldName: String) {
    if (modules != null) {
      val unknownModules = modules.filter { context.findModule(it) == null }
      if (!unknownModules.isEmpty()) {
        context.messages.error("The following modules from $fieldName aren\'t found in the project: $unknownModules")
      }
    }
  }

  private fun checkArtifacts(names: Collection<String>, fieldName: String) {
    val unknownArtifacts = names - JpsArtifactService.getInstance().getArtifacts(context.project).map { it.name }.toSet()
    if (!unknownArtifacts.isEmpty()) {
      context.messages.error("The following artifacts from $fieldName aren\'t found in the project: $unknownArtifacts")
    }
  }

  private fun checkPluginModules(pluginModules: List<String>?, fieldName: String, pluginLayoutList: List<PluginLayout>) {
    if (pluginModules == null) {
      return
    }

    checkModules(pluginModules, fieldName)

    val unspecifiedLayoutPluginModules = pluginModules.filter { mainModuleName ->
      pluginLayoutList.any { it.mainModule == mainModuleName }
    }
    if (!unspecifiedLayoutPluginModules.isEmpty()) {
      context.messages.info("No plugin layout specified in productProperties.productLayout.allNonTrivialPlugins for " +
                            "following plugin main modules. Assuming simple layout. " +
                            "Modules list: ${unspecifiedLayoutPluginModules.joinToString()}")
    }

    val unknownBundledPluginModules = pluginModules.filter { context.findFileInModuleSources(it, "META-INF/plugin.xml") == null }
    if (!unknownBundledPluginModules.isEmpty()) {
      context.messages.error("The following modules from $fieldName don\'t contain META-INF/plugin.xml file and" +
                             " aren\'t specified as optional plugin modules in productProperties.productLayout.allNonTrivialPlugins: " +
                             "${unknownBundledPluginModules.joinToString()}}. ")
    }
  }

  private fun checkPaths(paths: Collection<String>, propertyName: String) {
    val nonExistingFiles = paths.filter { Files.notExists(Path.of(it)) }
    if (!nonExistingFiles.isEmpty()) {
      context.messages.error("$propertyName contains non-existing files: ${nonExistingFiles.joinToString()}")
    }
  }

  private fun checkPaths2(paths: Collection<Path>, propertyName: String) {
    val nonExistingFiles = paths.filter { Files.notExists(it) }
    if (!nonExistingFiles.isEmpty()) {
      context.messages.error("$propertyName contains non-existing files: ${nonExistingFiles.joinToString()}")
    }
  }

  private fun checkMandatoryField(value: String?, fieldName: String) {
    if (value == null) {
      context.messages.error("Mandatory property \'$fieldName\' is not specified")
    }
  }

  private fun checkMandatoryPath(path: String, fieldName: String) {
    checkMandatoryField(path, fieldName)
    checkPaths(listOf(path), fieldName)
  }

  override fun compileProjectAndTests(includingTestsInModules: List<String>) {
    compileModules(null, includingTestsInModules)
  }

  fun compileProjectAndTests() {
    compileProjectAndTests(emptyList())
  }

  override fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>) {
    CompilationTasks.create(context).compileModules(moduleNames, includingTestsInModules)
  }

  override fun compileModules(moduleNames: Collection<String>?) {
    CompilationTasks.create(context).compileModules(moduleNames)
  }

  override fun buildUpdaterJar() {
    doBuildUpdaterJar("updater.jar")
  }

  override fun buildFullUpdaterJar() {
    doBuildUpdaterJar("updater-full.jar")
  }

  private fun doBuildUpdaterJar(artifactName: String) {
    val updaterModule = context.findRequiredModule("intellij.platform.updater")
    val updaterModuleSource = DirSource(context.getModuleOutputDir(updaterModule))
    val librarySources = JpsJavaExtensionService.dependencies(updaterModule)
      .productionOnly()
      .runtimeOnly()
      .libraries
      .asSequence()
      .flatMap { it.getRootUrls(JpsOrderRootType.COMPILED) }
      .filter { !JpsPathUtil.isJrtUrl(it) }
      .map { ZipSource(Path.of(JpsPathUtil.urlToPath(it)), listOf(Regex("^META-INF/.*"))) }
    val updaterJar = context.paths.artifactDir.resolve(artifactName)
    buildJar(targetFile = updaterJar, sources = (sequenceOf(updaterModuleSource) + librarySources).toList(), compress = true)
    context.notifyArtifactBuilt(updaterJar)
  }

  override fun runTestBuild() {
    checkProductProperties(context)
    val context = context
    val projectStructureMapping = compileModulesForDistribution(context).buildJARs(context)
    layoutShared(context)
    context.productProperties.versionCheckerConfig?.let {
      ClassVersionChecker.checkVersions(it, context, context.paths.distAllDir)
    }
    if (context.productProperties.buildSourcesArchive) {
      buildSourcesArchive(projectStructureMapping, context)
    }
    buildOsSpecificDistributions(this.context)
  }

  override fun buildUnpackedDistribution(targetDirectory: Path, includeBinAndRuntime: Boolean) {
    val currentOs = OsFamily.currentOs
    context.paths.distAllDir = targetDirectory
    context.options.targetOS = currentOs.osId
    context.options.buildStepsToSkip.add(BuildOptions.GENERATE_JAR_ORDER_STEP)
    BundledMavenDownloader.downloadMavenCommonLibs(context.paths.buildDependenciesCommunityRoot)
    BundledMavenDownloader.downloadMavenDistribution(context.paths.buildDependenciesCommunityRoot)
    compileModulesForDistribution(context).buildJARs(context, true)
    val arch = if (CpuArch.isArm64()) JvmArchitecture.aarch64 else JvmArchitecture.x64
    layoutShared(context)
    if (includeBinAndRuntime) {
      val propertiesFile = patchIdeaPropertiesFile(context)
      val builder = context.getOsDistributionBuilder(currentOs, propertiesFile)!!
      builder.copyFilesForOsDistribution(targetDirectory, arch)
      context.bundledRuntime.extractTo(prefix = BundledRuntimeImpl.getProductPrefix(context),
                                       os = currentOs,
                                       destinationDir = targetDirectory.resolve("jbr"),
                                       arch = arch)
      updateExecutablePermissions(targetDirectory, builder.generateExecutableFilesPatterns(true))
      context.bundledRuntime.checkExecutablePermissions(distribution = targetDirectory, root = "", os = currentOs)
    }
    else {
      copyDistFiles(context, targetDirectory)
      unpackPty4jNative(context, targetDirectory, null)
    }
  }
}

private class SupportedDistribution(@JvmField val os: OsFamily, @JvmField val arch: JvmArchitecture)

private val SUPPORTED_DISTRIBUTIONS = java.util.List.of(
  SupportedDistribution(os = OsFamily.MACOS, arch = JvmArchitecture.x64),
  SupportedDistribution(os = OsFamily.MACOS, arch = JvmArchitecture.aarch64),
  SupportedDistribution(os = OsFamily.WINDOWS, arch = JvmArchitecture.x64),
  SupportedDistribution(os = OsFamily.LINUX, arch = JvmArchitecture.x64),
)

private fun isSourceFile(path: String): Boolean {
  return path.endsWith(".java") || path.endsWith(".groovy") || path.endsWith(".kt")
}

private fun getLocalArtifactRepositoryRoot(global: JpsGlobal): Path {
  val localRepoPath = JpsModelSerializationDataService.getPathVariablesConfiguration(global)!!.getUserVariableValue("MAVEN_REPOSITORY")
  if (localRepoPath != null) {
    return Path.of(localRepoPath)
  }

  val root = System.getProperty("user.home", null)
  return if (root == null) Path.of(".m2/repository") else Path.of(root, ".m2/repository")
}

/**
 * Build a list with modules that the IDE will provide for plugins.
 */
private fun buildProvidedModuleList(targetFile: Path, state: DistributionBuilderState, context: BuildContext) {
  context.executeStep(spanBuilder("build provided module list"), BuildOptions.PROVIDED_MODULES_LIST_STEP) {
    Files.deleteIfExists(targetFile)
    val ideClasspath = DistributionJARsBuilder(state).createIdeClassPath(context)
    // start the product in headless mode using com.intellij.ide.plugins.BundledPluginsLister
    BuildHelper.runApplicationStarter(context, context.paths.tempDir.resolve("builtinModules"), ideClasspath,
                                      listOf("listBundledPlugins", targetFile.toString()), emptyMap(), null,
                                      TimeUnit.MINUTES.toMillis(10L), context.classpathCustomizer)
    if (Files.notExists(targetFile)) {
      context.messages.error("Failed to build provided modules list: $targetFile doesn\'t exist")
    }
    context.productProperties.customizeBuiltinModules(context, targetFile)
    (context as BuildContextImpl).setBuiltinModules(BuiltinModulesFileUtils.readBuiltinModulesFile(targetFile))
    context.notifyArtifactWasBuilt(targetFile)
  }
}

private fun patchIdeaPropertiesFile(buildContext: BuildContext): Path {
  val builder = StringBuilder(Files.readString(buildContext.paths.communityHomeDir.resolve("bin/idea.properties")))
  for (it in buildContext.productProperties.additionalIDEPropertiesFilePaths) {
    builder.append('\n').append(Files.readString(it))
  }

  //todo[nik] introduce special systemSelectorWithoutVersion instead?
  val settingsDir = buildContext.systemSelector.replaceFirst("\\d+(\\.\\d+)?".toRegex(), "")
  val temp = builder.toString()
  builder.setLength(0)
  val map = LinkedHashMap<String, String>(1)
  map.put("settings_dir", settingsDir)
  builder.append(BuildUtils.replaceAll(temp, map, "@@"))
  if (buildContext.applicationInfo.isEAP) {
    builder.append(
      "\n#-----------------------------------------------------------------------\n" +
      "# Change to 'disabled' if you don't want to receive instant visual notifications\n" +
      "# about fatal errors that happen to an IDE or plugins installed.\n" +
      "#-----------------------------------------------------------------------\n" +
      "idea.fatal.error.notification=enabled\n")
  }
  else {
    builder.append(
      "\n#-----------------------------------------------------------------------\n" +
      "# Change to 'enabled' if you want to receive instant visual notifications\n" +
      "# about fatal errors that happen to an IDE or plugins installed.\n" +
      "#-----------------------------------------------------------------------\n" +
      "idea.fatal.error.notification=disabled\n")
  }
  val propertiesFile = buildContext.paths.tempDir.resolve("idea.properties")
  Files.writeString(propertiesFile, builder)
  return propertiesFile
}

private fun layoutShared(context: BuildContext) {
  context.messages.block(spanBuilder("copy files shared among all distributions"), Supplier<Void?> {
    val licenseOutDir = context.paths.distAllDir.resolve("license")
    BuildHelper.copyDir(context.paths.communityHomeDir.resolve("license"), licenseOutDir)
    for (additionalDirWithLicenses in context.productProperties.additionalDirectoriesWithLicenses) {
      BuildHelper.copyDir(additionalDirWithLicenses, licenseOutDir)
    }
    context.applicationInfo.svgRelativePath?.let { svgRelativePath ->
      val from = findBrandingResource(svgRelativePath, context)
      val to = context.paths.distAllDir.resolve("bin/${context.productProperties.baseFileName}.svg")
      Files.createDirectories(to.parent)
      Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
    }
    context.productProperties.copyAdditionalFiles(context, context.paths.getDistAll())
    null
  })
}

private fun findBrandingResource(relativePath: String, context: BuildContext): Path {
  val normalizedRelativePath = relativePath.removePrefix("/")
  val inModule = context.findFileInModuleSources(context.productProperties.applicationInfoModule, normalizedRelativePath)
  if (inModule != null) {
    return inModule
  }

  for (brandingResourceDir in context.productProperties.brandingResourcePaths) {
    val file = brandingResourceDir.resolve(normalizedRelativePath)
    if (Files.exists(file)) {
      return file
    }
  }

  throw RuntimeException("Cannot find \'$normalizedRelativePath\' " +
                         "neither in sources of \'${context.productProperties.applicationInfoModule}\' " +
                         "nor in ${context.productProperties.brandingResourcePaths}")
}

private fun updateExecutablePermissions(destinationDir: Path, executableFilesPatterns: List<String>) {
  val executable = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                                        PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
                                                        PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
                                                        PosixFilePermission.OTHERS_EXECUTE)
  val regular = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                                     PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)
  val executableFilesMatchers = executableFilesPatterns.map {  FileSystems.getDefault().getPathMatcher("glob:$it") }
  Files.walk(destinationDir).use { stream ->
    for (file in stream) {
      if (Files.isDirectory(file)) {
        continue
      }
      if (SystemInfoRt.isUnix) {
        val relativeFile = destinationDir.relativize(file)
        val isExecutable = Files.getPosixFilePermissions(file).contains(PosixFilePermission.OWNER_EXECUTE) ||
                           executableFilesMatchers.any { it.matches(relativeFile) }
        Files.setPosixFilePermissions(file, if (isExecutable) executable else regular)
      }
      else {
        (Files.getFileAttributeView(file, DosFileAttributeView::class.java) as DosFileAttributeView).setReadOnly(false)
      }
    }
  }
}

private fun downloadMissingLibrarySources(
  librariesWithMissingSources: List<JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>>,
  context: BuildContext,
) {
  spanBuilder("download missing sources")
    .setAttribute(AttributeKey.stringArrayKey("librariesWithMissingSources"), librariesWithMissingSources.map { it.name })
    .startSpan()
    .use { span ->
      val configuration = JpsRemoteRepositoryService.getInstance().getRemoteRepositoriesConfiguration(context.project)
      val repositories = configuration?.repositories?.map { ArtifactRepositoryManager.createRemoteRepository(it.id, it.url) } ?: emptyList()
      val repositoryManager = ArtifactRepositoryManager(getLocalArtifactRepositoryRoot(context.projectModel.global), repositories,
                                                        ProgressConsumer.DEAF)
      for (library in librariesWithMissingSources) {
        val descriptor = library.properties.data
        span.addEvent("downloading sources for library", Attributes.of(
          AttributeKey.stringKey("name"), library.name,
          AttributeKey.stringKey("mavenId"), descriptor.mavenId,
        ))
        val downloaded = repositoryManager.resolveDependencyAsArtifact(descriptor.groupId, descriptor.artifactId,
                                                                       descriptor.version, EnumSet.of(ArtifactKind.SOURCES),
                                                                       descriptor.isIncludeTransitiveDependencies,
                                                                       descriptor.excludedDependencies)
        span.addEvent("downloaded sources for library", Attributes.of(
          AttributeKey.stringArrayKey("artifacts"), downloaded.map { it.toString() },
        ))
      }
    }
}

internal class DistributionForOsTaskResult(@JvmField val os: OsFamily,
                                          @JvmField val arch: JvmArchitecture,
                                          @JvmField val outDir: Path)

// todo get rid of runParallel and use FJP pool directly - in this case no need to use ref to get result
private fun createDistributionForOsTask(os: OsFamily,
                                        arch: JvmArchitecture,
                                        ideaProperties: Path): Pair<BuildTaskRunnable, Ref<DistributionForOsTaskResult>> {
  val ref = Ref<DistributionForOsTaskResult>()
  return Pair(createDistributionForOsTask(os, arch) { context ->
    context.getOsDistributionBuilder(os, ideaProperties)?.let { builder ->
      val osAndArchSpecificDistDirectory = DistributionJARsBuilder.getOsAndArchSpecificDistDirectory(os, arch, context)
      builder.buildArtifacts(osAndArchSpecificDistDirectory, arch)
      ref.set(DistributionForOsTaskResult(os, arch, osAndArchSpecificDistDirectory))
    }
  }, ref)
}

private fun createDistributionForOsTask(os: OsFamily, arch: JvmArchitecture, buildAction: Consumer<BuildContext>): BuildTaskRunnable {
  return BuildTaskRunnable.task("${os.osId} ${arch.name}", Consumer { context ->
    if (!context.shouldBuildDistributionForOS(os.osId)) {
      return@Consumer
    }

    context.messages.block(spanBuilder("build ${os.osName} ${arch.name} distribution"), Supplier<Void> {
      buildAction.accept(context)
      null
    })
  })
}

private fun find(directory: Path, suffix: String, context: BuildContext): Path {
  Files.walk(directory).use { stream ->
    val found = stream.filter { (it.fileName.toString()).endsWith(suffix) }.collect(Collectors.toList())
    if (found.isEmpty()) {
      context.messages.error("No file with suffix $suffix is found in $directory")
    }
    if (found.size > 1) {
      context.messages.error("Multiple files with suffix $suffix are found in $directory:\n${found.joinToString(separator = "\n")}")
    }
    return found.first()
  }
}

private fun buildOsSpecificDistributions(context: BuildContext): List<DistributionForOsTaskResult?> {
  val propertiesFile = patchIdeaPropertiesFile(context)
  val createDistTasks = listOf(
    createDistributionForOsTask(OsFamily.MACOS, JvmArchitecture.x64, propertiesFile),
    createDistributionForOsTask(OsFamily.MACOS, JvmArchitecture.aarch64, propertiesFile),
    createDistributionForOsTask(OsFamily.WINDOWS, JvmArchitecture.x64, propertiesFile),
    createDistributionForOsTask(OsFamily.LINUX, JvmArchitecture.x64, propertiesFile)
  )

  context.executeStep("Building OS-specific distributions", BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP) {
    runInParallel(createDistTasks.map { it.first }, context)
  }
  return createDistTasks.map { it.second.get() }
}

private fun runInParallel(tasks: List<BuildTaskRunnable>, context: BuildContext) {
  if (tasks.isEmpty()) {
    return
  }

  if (!context.options.runBuildStepsInParallel) {
    for (task in tasks) {
      task.task.accept(context)
    }
    return
  }

  val span = spanBuilder("run tasks in parallel")
    .setAttribute(AttributeKey.stringArrayKey("tasks"), tasks.map { it.stepId })
    .setAttribute("taskCount", tasks.size.toLong())
    .startSpan()
  try {
    span.use {
      val futures = ArrayList<ForkJoinTask<*>>(tasks.size)
      val traceContext = Context.current().with(span)
      for (task in tasks) {
        createTaskWrapper(task, context.forkForParallelTask(task.stepId), traceContext)?.let {
          futures.add(it.fork())
        }
      }

      val errors = ArrayList<Throwable>()
      // inversed order of join - better for FJP (https://shipilev.net/talks/jeeconf-May2012-forkjoin.pdf, slide 32)
      for (future in futures.asReversed()) {
        try {
          future.join()
        }
        catch (e: Throwable) {
          errors.add(if (e is UndeclaredThrowableException) e.cause!! else e)
        }
      }

      if (!errors.isEmpty()) {
        Span.current().setStatus(StatusCode.ERROR)
        if (errors.size == 1) {
          context.messages.error(errors.first().message!!, errors.first())
        }
        else {
          context.messages.error("Some tasks failed", CompoundRuntimeException(errors))
        }
      }
    }
  }
  finally {
    context.messages.onAllForksFinished()
  }
}

private fun createTaskWrapper(task: BuildTaskRunnable,
                              buildContext: BuildContext,
                              traceContext: Context): ForkJoinTask<*>? {
  if (buildContext.options.buildStepsToSkip.contains(task.stepId)) {
    val span = spanBuilder(task.stepId).setParent(traceContext).startSpan()
    span.addEvent("skip")
    span.end()
    return null
  }

  return ForkJoinTask.adapt {
    buildContext.messages.onForkStarted()
    try {
      spanBuilder(task.stepId).setParent(traceContext).startSpan().useWithScope { span ->
        if (buildContext.options.buildStepsToSkip.contains(task.stepId)) {
          span.addEvent("skip")
          null
        }
        else {
          task.task.accept(buildContext)
        }
      }
    }
    finally {
      buildContext.messages.onForkFinished()
    }
  }
}

private fun copyDependenciesFile(context: BuildContext) {
  val outputFile = context.paths.artifactDir.resolve("dependencies.txt")
  Files.createDirectories(outputFile.parent)
  Files.copy(context.dependenciesProperties.file, outputFile, StandardCopyOption.REPLACE_EXISTING)
  context.notifyArtifactWasBuilt(outputFile)
}

private fun checkProjectLibraries(names: Collection<String>, fieldName: String, context: BuildContext) {
  val unknownLibraries = names.filter { context.project.libraryCollection.findLibrary(it) == null }
  if (!unknownLibraries.isEmpty()) {
    context.messages.error("The following libraries from $fieldName aren\'t found in the project: $unknownLibraries")
  }
}

internal fun buildSourcesArchive(projectStructureMapping: ProjectStructureMapping, context: BuildContext) {
  val productProperties = context.productProperties
  val archiveName = "${productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)}-sources.zip"
  val modulesFromCommunity = projectStructureMapping.includedModules.filter { moduleName ->
    productProperties.includeIntoSourcesArchiveFilter.test(context.findRequiredModule(moduleName), context)
  }
  zipSourcesOfModules(modules = modulesFromCommunity,
                      targetFile = context.paths.artifactDir.resolve(archiveName),
                      includeLibraries = true,
                      context = context)
}

private fun zipSourcesOfModules(modules: Collection<String>, targetFile: Path, includeLibraries: Boolean, context: BuildContext) {
  context.executeStep(spanBuilder("build module sources archives")
                        .setAttribute("path", context.paths.buildOutputDir.relativize(targetFile).toString())
                        .setAttribute(AttributeKey.stringArrayKey("modules"), java.util.List.copyOf(modules)),
                      BuildOptions.SOURCES_ARCHIVE_STEP) {
    Files.createDirectories(targetFile.parent)
    Files.deleteIfExists(targetFile)
    val includedLibraries = LinkedHashSet<JpsLibrary>()
    if (includeLibraries) {
      val debugMapping = ArrayList<String>()
      for (moduleName in modules) {
        val module = context.findRequiredModule(moduleName)
        if (moduleName.startsWith("intellij.platform.") && context.findModule("$moduleName.impl") != null) {
          val libraries = JpsJavaExtensionService.dependencies(module).productionOnly().compileOnly().recursivelyExportedOnly().libraries
          includedLibraries.addAll(libraries)
          libraries.mapTo(debugMapping) { "${it.name} for $moduleName" }
        }
      }
      Span.current().addEvent("collect libraries to include into archive",
                              Attributes.of(AttributeKey.stringArrayKey("mapping"), debugMapping))
      val librariesWithMissingSources = includedLibraries
        .asSequence()
        .map { it.asTyped(JpsRepositoryLibraryType.INSTANCE) }
        .filterNotNull()
        .filter { library -> library.getFiles(JpsOrderRootType.SOURCES).any { Files.notExists(it.toPath()) } }
        .toList()
      if (!librariesWithMissingSources.isEmpty()) {
        downloadMissingLibrarySources(librariesWithMissingSources, context)
      }
    }

    val zipFileMap = LinkedHashMap<Path, String>()
    for (moduleName in modules) {
      val module = context.findRequiredModule(moduleName)
      for (root in module.getSourceRoots(JavaSourceRootType.SOURCE)) {
        if (root.file.absoluteFile.exists()) {
          val sourceFiles = filterSourceFilesOnly(root.file.name, context) { FileUtil.copyDirContent(root.file.absoluteFile, it.toFile()) }
          zipFileMap.put(sourceFiles, root.properties.packagePrefix.replace(".", "/"))
        }
      }
      for (root in module.getSourceRoots(JavaResourceRootType.RESOURCE)) {
        if (root.file.absoluteFile.exists()) {
          val sourceFiles = filterSourceFilesOnly(root.file.name, context) { FileUtil.copyDirContent(root.file.absoluteFile, it.toFile()) }
          zipFileMap.put(sourceFiles, root.properties.relativeOutputPath)
        }
      }
    }

    val libraryRootUrls = includedLibraries.flatMap { it.getRootUrls(JpsOrderRootType.SOURCES) }
    context.messages.debug(" include ${libraryRootUrls.size} roots from ${includedLibraries.size} libraries:")
    for (url in libraryRootUrls) {
      if (url.startsWith(JpsPathUtil.JAR_URL_PREFIX) && url.endsWith(JpsPathUtil.JAR_SEPARATOR)) {
        val file = JpsPathUtil.urlToFile(url).absoluteFile
        if (file.isFile) {
          context.messages.debug("  $file, ${Formats.formatFileSize(file.length())}, ${file.length().toString().padEnd(9, '0')} bytes")
          val sourceFiles = filterSourceFilesOnly(file.name, context) { tempDir ->
            Decompressor.Zip(file).filter(Predicate { isSourceFile(it) }).extract(tempDir)
          }
          zipFileMap.put(sourceFiles, "")
        }
        else {
          context.messages.debug("  skipped root $file: file doesn\'t exist")
        }
      }
      else {
        context.messages.debug("  skipped root $url: not a jar file")
      }
    }
    BuildHelper.zipWithPrefixes(context, targetFile, zipFileMap, true)
    context.notifyArtifactWasBuilt(targetFile)
  }
}

private inline fun filterSourceFilesOnly(name: String, context: BuildContext, configure: (Path) -> Unit): Path {
  val sourceFiles = context.paths.tempDir.resolve("$name-${UUID.randomUUID()}")
  NioFiles.deleteRecursively(sourceFiles)
  Files.createDirectories(sourceFiles)
  configure(sourceFiles)
  Files.walk(sourceFiles).use { stream ->
    stream.forEach {
      if (!Files.isDirectory(it) && !isSourceFile(it.toString())) {
        Files.delete(it)
      }
    }
  }
  return sourceFiles
}

private fun buildAdditionalArtifacts(projectStructureMapping: ProjectStructureMapping, context: BuildContext) {
  val productProperties = context.productProperties

  if (productProperties.generateLibrariesLicensesTable &&
      !context.options.buildStepsToSkip.contains(BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP)) {
    val artifactNamePrefix = productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)
    val artifactDir = context.paths.artifactDir
    Files.createDirectories(artifactDir)
    Files.copy(getThirdPartyLibrariesHtmlFilePath(context), artifactDir.resolve(artifactNamePrefix + "-third-party-libraries.html"))
    Files.copy(getThirdPartyLibrariesJsonFilePath(context), artifactDir.resolve(artifactNamePrefix + "-third-party-libraries.json"))
    context.notifyArtifactBuilt(artifactDir.resolve(artifactNamePrefix + "-third-party-libraries.html"))
    context.notifyArtifactBuilt(artifactDir.resolve(artifactNamePrefix + "-third-party-libraries.json"))
  }

  if (productProperties.buildSourcesArchive) {
    buildSourcesArchive(projectStructureMapping, context)
  }
}

internal fun getThirdPartyLibrariesHtmlFilePath(context: BuildContext): Path {
  return context.paths.distAllDir.resolve("license/third-party-libraries.html")
}

internal fun getThirdPartyLibrariesJsonFilePath(context: BuildContext): Path {
  return context.paths.tempDir.resolve("third-party-libraries.json")
}

private fun compilePlatformAndPluginModules(pluginsToPublish: Set<PluginLayout>, context: BuildContext): DistributionBuilderState {
  val distState = DistributionBuilderState(pluginsToPublish, context)
  val compilationTasks = CompilationTasks.create(context)
  compilationTasks.compileModules(distState.getModulesForPluginsToPublish() + listOf("intellij.idea.community.build.tasks",
                                                                                   "intellij.platform.images.build"))

  // we need this to ensure that all libraries which may be used in the distribution are resolved,
  // even if product modules don't depend on them (e.g. JUnit5)
  compilationTasks.resolveProjectDependencies()
  compilationTasks.buildProjectArtifacts(distState.getIncludedProjectArtifacts())
  return distState
}

private fun compileModulesForDistribution(pluginsToPublish: Set<PluginLayout>, context: BuildContext): DistributionBuilderState {
  val productProperties = context.productProperties
  val mavenArtifacts = productProperties.mavenArtifacts

  val toCompile = LinkedHashSet<String>()
  toCompile.addAll(DistributionJARsBuilder.getModulesToCompile(context))
  context.proprietaryBuildTools.scrambleTool?.getAdditionalModulesToCompile()?.let {
    toCompile.addAll(it)
  }
  toCompile.addAll(productProperties.productLayout.mainModules)
  toCompile.addAll(mavenArtifacts.additionalModules)
  toCompile.addAll(mavenArtifacts.squashedModules)
  toCompile.addAll(mavenArtifacts.proprietaryModules)
  toCompile.addAll(productProperties.modulesToCompileTests)
  CompilationTasks.create(context).compileModules(toCompile)

  if (context.shouldBuildDistributions()) {
    val providedModulesFile = context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-builtinModules.json")
    val state = compilePlatformAndPluginModules(pluginsToPublish, context)
    buildProvidedModuleList(targetFile = providedModulesFile, state = state, context = context)
    if (productProperties.productLayout.buildAllCompatiblePlugins) {
      if (context.options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
        context.messages.info("Skipping collecting compatible plugins because PROVIDED_MODULES_LIST_STEP was skipped")
      }
      else {
        return compilePlatformAndPluginModules(
          pluginsToPublish = pluginsToPublish + PluginsCollector.collectCompatiblePluginsToPublish(providedModulesFile, context),
          context = context
        )
      }
    }
    else {
      return state
    }
  }
  return compilePlatformAndPluginModules(pluginsToPublish, context)
}

///**
// * Build index which is used to search options in the Settings dialog.
// */
//@JvmOverloads
//fun buildSearchableOptions(buildContext: BuildContext,
//                           classpathCustomizer: UnaryOperator<Set<String>>? = null,
//                           systemProperties: Map<String, Any> = emptyMap()): Path? {
//  val span = Span.current()
//  if (buildContext.options.buildStepsToSkip.contains(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)) {
//    span.addEvent("skip building searchable options index")
//    return null
//  }
//
//  val ideClasspath = createIdeClassPath(buildContext)
//  val targetDirectory = JarPackager.getSearchableOptionsDir(buildContext)
//  val messages = buildContext.messages
//  NioFiles.deleteRecursively(targetDirectory)
//  // Start the product in headless mode using com.intellij.ide.ui.search.TraverseUIStarter.
//  // It'll process all UI elements in Settings dialog and build index for them.
//  //noinspection SpellCheckingInspection
//  BuildHelper.runApplicationStarter(buildContext,
//                                    buildContext.paths.tempDir.resolve("searchableOptions"),
//                                    ideClasspath, listOf("traverseUI", targetDirectory.toString(), "true"),
//                                    systemProperties,
//                                    emptyList(),
//                                    TimeUnit.MINUTES.toMillis(10L), classpathCustomizer)
//
//  if (!Files.isDirectory(targetDirectory)) {
//    messages.error("Failed to build searchable options index: $targetDirectory does not exist. See log above for error output from traverseUI run.")
//  }
//
//  val modules = Files.newDirectoryStream(targetDirectory).use { it.toList() }
//  if (modules.isEmpty()) {
//    messages.error("Failed to build searchable options index: $targetDirectory is empty. See log above for error output from traverseUI run.")
//  }
//  else {
//    span.setAttribute(AttributeKey.longKey("moduleCountWithSearchableOptions"), modules.size)
//    span.setAttribute(AttributeKey.stringArrayKey("modulesWithSearchableOptions"),
//                      modules.map { targetDirectory.relativize(it).toString() })
//  }
//  return targetDirectory
//}

//fun createIdeClassPath(context: BuildContext): LinkedHashSet<String> {
//  // for some reasons maybe duplicated paths - use set
//  val classPath = LinkedHashSet<String>()
//  Files.createDirectories(context.paths.tempDir)
//  val pluginLayoutRoot = Files.createTempDirectory(context.paths.tempDir, "pluginLayoutRoot")
//  val nonPluginsEntries = ArrayList<DistributionFileEntry>()
//  val pluginsEntries = ArrayList<DistributionFileEntry>()
//  for(e in generateProjectStructureMapping(context, pluginLayoutRoot)) {
//    if (e.getPath().startsWith(pluginLayoutRoot)) {
//      val relPath = pluginLayoutRoot.relativize(e.path)
//      // For plugins our classloader load jars only from lib folder
//      if (relPath.parent?.parent == null && relPath.parent?.toString() == "lib") {
//        pluginsEntries.add(e)
//      }
//    } else {
//      nonPluginsEntries.add(e)
//    }
//  }
//
//  for (entry in nonPluginsEntries + pluginsEntries) {
//    when (entry) {
//      is ModuleOutputEntry -> classPath.add(context.getModuleOutputDir(context.findRequiredModule(entry.moduleName)).toString())
//      is LibraryFileEntry -> classPath.add(entry.libraryFile.toString())
//      else -> throw UnsupportedOperationException("Entry $entry is not supported")
//    }
//  }
//  return classPath
//}
