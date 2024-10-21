// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.system.CpuArch
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.moduleBased.findProductModulesFile
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.checkInArchive
import org.jetbrains.intellij.build.impl.productInfo.generateProductInfoJson
import org.jetbrains.intellij.build.impl.projectStructureMapping.ContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.getIncludedModules
import org.jetbrains.intellij.build.impl.sbom.SoftwareBillOfMaterialsImpl
import org.jetbrains.intellij.build.io.*
import org.jetbrains.intellij.build.productRunner.IntellijProductRunner
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.block
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.artifact.JpsArtifactService
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import kotlin.io.path.relativeTo

internal const val PROPERTIES_FILE_NAME: String = "idea.properties"

internal class BuildTasksImpl(private val context: BuildContextImpl) : BuildTasks {
  override suspend fun buildDistributions() {
    buildDistributions(context)
  }

  override suspend fun buildNonBundledPlugins(mainPluginModules: List<String>) {
    checkProductProperties(context)
    checkPluginModules(mainPluginModules, "mainPluginModules", context)
    copyDependenciesFile(context)
    val pluginsToPublish = getPluginLayoutsByJpsModuleNames(mainPluginModules, context.productProperties.productLayout)
    val distState = createDistributionBuilderState(pluginsToPublish = pluginsToPublish, context = context)
    context.compileModules(null)

    buildProjectArtifacts(
      platform = distState.platform,
      enabledPluginModules = getEnabledPluginModules(pluginsToPublish = distState.pluginsToPublish, context = context),
      context = context,
    )
    val searchableOptionSet = buildSearchableOptions(context)
    buildNonBundledPlugins(
      pluginsToPublish = pluginsToPublish,
      compressPluginArchive = context.options.compressZipFiles,
      buildPlatformLibJob = null,
      state = distState,
      searchableOptionSet = searchableOptionSet,
      context = context
    )
  }

  override suspend fun buildUnpackedDistribution(targetDirectory: Path, includeBinAndRuntime: Boolean) {
    val currentOs = OsFamily.currentOs
    context.paths.distAllDir = targetDirectory
    context.options.targetOs = persistentListOf(currentOs)
    context.options.buildStepsToSkip += sequenceOf(
      BuildOptions.GENERATE_JAR_ORDER_STEP,
      SoftwareBillOfMaterials.STEP_ID,
    )
    context.reportDistributionBuildNumber()
    BundledMavenDownloader.downloadMaven4Libs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMaven3Libs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMavenDistribution(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMavenTelemetryDependencies(context.paths.communityHomeDirRoot)
    buildDistribution(state = compileAllModulesAndCreateDistributionState(context), context = context, isUpdateFromSources = true)
    val arch = if (SystemInfoRt.isMac && CpuArch.isIntel64() && CpuArch.isEmulated()) {
      JvmArchitecture.aarch64
    }
    else {
      JvmArchitecture.currentJvmArch
    }
    context.options.targetArch = arch
    layoutShared(context)
    if (includeBinAndRuntime) {
      val propertiesFile = createIdeaPropertyFile(context)
      val builder = getOsDistributionBuilder(os = currentOs, ideaProperties = propertiesFile, context = context)!!
      builder.copyFilesForOsDistribution(targetDirectory, arch)
      context.bundledRuntime.extractTo(os = currentOs, destinationDir = targetDirectory.resolve("jbr"), arch = arch)
      updateExecutablePermissions(targetDirectory, builder.generateExecutableFilesMatchers(includeRuntime = true, arch = arch).keys)
      builder.checkExecutablePermissions(distribution = targetDirectory, root = "", includeRuntime = true, arch = arch)
      builder.writeProductInfoFile(targetDirectory, arch)
    }
    else {
      copyDistFiles(context = context, newDir = targetDirectory, os = currentOs, arch = arch)
    }
  }
}

data class SupportedDistribution(@JvmField val os: OsFamily, @JvmField val arch: JvmArchitecture)

@JvmField
val SUPPORTED_DISTRIBUTIONS: List<SupportedDistribution> = listOf(
  SupportedDistribution(os = OsFamily.MACOS, arch = JvmArchitecture.x64),
  SupportedDistribution(os = OsFamily.MACOS, arch = JvmArchitecture.aarch64),
  SupportedDistribution(os = OsFamily.WINDOWS, arch = JvmArchitecture.x64),
  SupportedDistribution(os = OsFamily.WINDOWS, arch = JvmArchitecture.aarch64),
  SupportedDistribution(os = OsFamily.LINUX, arch = JvmArchitecture.x64),
  SupportedDistribution(os = OsFamily.LINUX, arch = JvmArchitecture.aarch64),
)

fun createIdeaPropertyFile(context: BuildContext): CharSequence {
  val builder = StringBuilder(Files.readString(context.paths.communityHomeDir.resolve("bin/idea.properties")))
  for (it in context.productProperties.additionalIDEPropertiesFilePaths) {
    builder.append('\n').append(Files.readString(it))
  }

  //todo introduce special systemSelectorWithoutVersion instead?
  val settingsDir = context.systemSelector.replaceFirst(Regex("\\d+(\\.\\d+)?"), "")
  val temp = builder.toString()
  builder.setLength(0)
  val map = LinkedHashMap<String, String>(1)
  map.put("settings_dir", settingsDir)
  builder.append(BuildUtils.replaceAll(temp, map, "@@"))
  if (context.applicationInfo.isEAP) {
    builder.append(
      "\n#-----------------------------------------------------------------------\n" +
      "# Change to 'disabled' if you don't want to receive instant visual notifications\n" +
      "# about fatal errors that happen to an IDE or plugins installed.\n" +
      "#-----------------------------------------------------------------------\n" +
      "idea.fatal.error.notification=enabled\n"
    )
  }
  else {
    builder.append(
      "\n#-----------------------------------------------------------------------\n" +
      "# Change to 'enabled' if you want to receive instant visual notifications\n" +
      "# about fatal errors that happen to an IDE or plugins installed.\n" +
      "#-----------------------------------------------------------------------\n" +
      "idea.fatal.error.notification=disabled\n"
    )
  }
  return builder
}

private suspend fun layoutShared(context: BuildContext) {
  spanBuilder("copy files shared among all distributions").use {
    val licenseOutDir = context.paths.distAllDir.resolve("license")
    withContext(Dispatchers.IO) {
      copyDir(context.paths.communityHomeDir.resolve("license"), licenseOutDir)
      for (additionalDirWithLicenses in context.productProperties.additionalDirectoriesWithLicenses) {
        copyDir(additionalDirWithLicenses, licenseOutDir)
      }
      context.applicationInfo.svgRelativePath?.let { svgRelativePath ->
        val from = findBrandingResource(svgRelativePath, context)
        val to = context.paths.distAllDir.resolve("bin/${context.productProperties.baseFileName}.svg")
        Files.createDirectories(to.parent)
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
      }
      context.productProperties.copyAdditionalFiles(context, context.paths.distAllDir)
    }
  }
  checkClassFiles(root = context.paths.distAllDir, context = context, isDistAll = true)
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

  throw RuntimeException(
    "Cannot find \'$normalizedRelativePath\' " +
    "neither in sources of \'${context.productProperties.applicationInfoModule}\' " +
    "nor in ${context.productProperties.brandingResourcePaths}"
  )
}

internal suspend fun updateExecutablePermissions(destinationDir: Path, executableFilesMatchers: Collection<PathMatcher>) {
  spanBuilder("update executable permissions").setAttribute("dir", "$destinationDir").use(Dispatchers.IO) {
    val executable = EnumSet.of(
      PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
      PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
      PosixFilePermission.OTHERS_EXECUTE
    )
    val regular = EnumSet.of(
      PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ
    )
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
}

@ApiStatus.Internal
class DistributionForOsTaskResult(
  @JvmField val builder: OsSpecificDistributionBuilder,
  @JvmField val arch: JvmArchitecture,
  @JvmField val outDir: Path
)

private suspend fun buildOsSpecificDistributions(context: BuildContext): List<DistributionForOsTaskResult> {
  return context.executeStep(spanBuilder("build OS-specific distributions"), BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP) {
    setLastModifiedTime(context.paths.distAllDir, context)

    if (context.isMacCodeSignEnabled) {
      withContext(Dispatchers.IO) {
        for (file in Files.newDirectoryStream(context.paths.distAllDir).use { stream ->
          stream.filter { !it.endsWith("help") && !it.endsWith("license") && !it.endsWith("lib") }
        }) {
          launch(CoroutineName("recursively signing macOS binaries in ${file.relativeTo(context.paths.distAllDir)}")) {
            // todo exclude plugins - layoutAdditionalResources should perform codesign -
            //  that's why we process files and zip in plugins (but not JARs)
            // and also kotlin compiler includes JNA
            recursivelySignMacBinaries(root = file, context = context)
          }
        }
      }
    }

    val ideaPropertyFileContent = createIdeaPropertyFile(context)

    spanBuilder("Adjust executable permissions on common dist").use {
      val matchers = SUPPORTED_DISTRIBUTIONS.mapNotNull {
        getOsDistributionBuilder(it.os, null, context)
      }.flatMap { builder ->
        JvmArchitecture.entries.flatMap { arch ->
          builder.generateExecutableFilesMatchers(includeRuntime = true, arch = arch).keys
        }
      }
      updateExecutablePermissions(context.paths.distAllDir, matchers)
    }

    supervisorScope {
      SUPPORTED_DISTRIBUTIONS.mapNotNull { (os, arch) ->
        if (!context.shouldBuildDistributionForOS(os, arch)) {
          return@mapNotNull null
        }

        val builder = getOsDistributionBuilder(os = os, ideaProperties = ideaPropertyFileContent, context = context) ?: return@mapNotNull null

        val stepId = "${os.osId} ${arch.name}"
        if (context.options.buildStepsToSkip.contains(stepId)) {
          Span.current().addEvent("skip step", Attributes.of(AttributeKey.stringKey("id"), stepId))
          return@mapNotNull null
        }

        async(CoroutineName("$stepId build step")) {
          spanBuilder(stepId).use {
            val osAndArchSpecificDistDirectory = getOsAndArchSpecificDistDirectory(osFamily = os, arch = arch, context = context)
            builder.buildArtifacts(osAndArchSpecificDistPath = osAndArchSpecificDistDirectory, arch = arch)
            checkClassFiles(root = osAndArchSpecificDistDirectory, context = context, isDistAll = false)
            DistributionForOsTaskResult(builder = builder, arch = arch, outDir = osAndArchSpecificDistDirectory)
          }
        }
      }
    }.collectCompletedOrError()
  } ?: emptyList()
}

// call only after supervisorScope
private fun <T> List<Deferred<T>>.collectCompletedOrError(): List<T> {
  var error: Throwable? = null
  for (deferred in this) {
    val e = deferred.getCompletionExceptionOrNull() ?: continue
    if (error == null) {
      error = e
    }
    else {
      error.addSuppressed(e)
    }
  }

  error?.let {
    throw it
  }
  return map { it.getCompleted() }
}

private fun copyDependenciesFile(context: BuildContext): Path {
  val outputFile = context.paths.artifactDir.resolve("dependencies.txt")
  Files.createDirectories(outputFile.parent)
  context.dependenciesProperties.copy(outputFile)
  context.notifyArtifactBuilt(outputFile)
  return outputFile
}

private fun checkProjectLibraries(names: Collection<String>, fieldName: String, context: BuildContext) {
  val unknownLibraries = names.filter { context.project.libraryCollection.findLibrary(it) == null }
  check(unknownLibraries.isEmpty()) {
    "The following libraries from $fieldName aren\'t found in the project: $unknownLibraries"
  }
}

private suspend fun buildSourcesArchive(contentReport: ContentReport, context: BuildContext) {
  val productProperties = context.productProperties
  val archiveName = "${productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)}-sources.zip"
  val openSourceModules = getIncludedModules(contentReport.bundled()).filter { moduleName ->
    productProperties.includeIntoSourcesArchiveFilter.test(context.findRequiredModule(moduleName), context)
  }.toList()
  zipSourcesOfModules(
    modules = openSourceModules,
    targetFile = context.paths.artifactDir.resolve(archiveName),
    includeLibraries = true,
    context = context,
  )
}

private suspend fun compileAllModulesAndCreateDistributionState(context: BuildContext): DistributionBuilderState {
  // compile all
  context.compileModules(null)

  val productLayout = context.productProperties.productLayout
  val pluginsToPublish = getPluginLayoutsByJpsModuleNames(modules = productLayout.pluginModulesToPublish, productLayout = productLayout)
  filterPluginsToPublish(plugins = pluginsToPublish, context = context)

  val enabledPluginModules = getEnabledPluginModules(pluginsToPublish = pluginsToPublish, context = context)
  // computed only based on a bundled and plugins to publish lists, compatible plugins are not taken in an account by intention
  val projectLibrariesUsedByPlugins = computeProjectLibsUsedByPlugins(enabledPluginModules = enabledPluginModules, context = context)

  if (!context.shouldBuildDistributions() || context.isStepSkipped(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
    return distributionState(
      pluginsToPublish = pluginsToPublish,
      projectLibrariesUsedByPlugins = projectLibrariesUsedByPlugins,
      enabledPluginModules = enabledPluginModules,
      context = context,
    )
  }

  return spanBuilder("collecting compatible plugins").use {
    val providedModuleFile = context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-builtinModules.json")
    val platform = createPlatformLayout(context = context)
    val builtinModuleData = spanBuilder("build provided module list").use {
      Files.deleteIfExists(providedModuleFile)
      // start the product in headless mode using com.intellij.ide.plugins.BundledPluginsLister
      context.createProductRunner().runProduct(listOf("listBundledPlugins", providedModuleFile.toString()))

      context.productProperties.customizeBuiltinModules(context = context, builtinModulesFile = providedModuleFile)
      try {
        val builtinModuleData = readBuiltinModulesFile(providedModuleFile)
        context.builtinModule = builtinModuleData
        builtinModuleData
      }
      catch (_: NoSuchFileException) {
        throw IllegalStateException("Failed to build provided modules list: $providedModuleFile doesn't exist")
      }
    }
    context.notifyArtifactBuilt(providedModuleFile)
    if (productLayout.buildAllCompatiblePlugins) {
      collectCompatiblePluginsToPublish(builtinModuleData = builtinModuleData, result = pluginsToPublish, context = context)
      filterPluginsToPublish(plugins = pluginsToPublish, context = context)

      // update enabledPluginModules to reflect changes in pluginsToPublish - used for buildProjectArtifacts
      distributionState(
        pluginsToPublish = pluginsToPublish,
        projectLibrariesUsedByPlugins = projectLibrariesUsedByPlugins,
        enabledPluginModules = getEnabledPluginModules(pluginsToPublish = pluginsToPublish, context = context),
        context = context,
      )
    }
    else {
      buildProjectArtifacts(platform = platform, enabledPluginModules = enabledPluginModules, context = context)
      DistributionBuilderState(platform = platform, pluginsToPublish = pluginsToPublish, context = context)
    }
  }
}

private suspend fun distributionState(
  pluginsToPublish: Set<PluginLayout>,
  projectLibrariesUsedByPlugins: SortedSet<ProjectLibraryData>,
  enabledPluginModules: Set<String>,
  context: BuildContext,
): DistributionBuilderState {
  val platform = createPlatformLayout(projectLibrariesUsedByPlugins = projectLibrariesUsedByPlugins, context = context)
  val distState = DistributionBuilderState(platform = platform, pluginsToPublish = pluginsToPublish, context = context)
  buildProjectArtifacts(platform = platform, enabledPluginModules = enabledPluginModules, context = context)
  return distState
}

private suspend fun buildProjectArtifacts(platform: PlatformLayout, enabledPluginModules: Set<String>, context: BuildContext) {
  val artifactNames = LinkedHashSet<String>()
  artifactNames.addAll(platform.includedArtifacts.keys)
  getPluginLayoutsByJpsModuleNames(modules = enabledPluginModules, productLayout = context.productProperties.productLayout)
    .flatMapTo(artifactNames) { it.includedArtifacts.keys }

  CompilationTasks.create(context).buildProjectArtifacts(artifactNames)
}

suspend fun buildDistributions(context: BuildContext): Unit = block("build distributions") {
  context.reportDistributionBuildNumber()

  checkProductProperties(context)
  checkLibraryUrls(context)

  copyDependenciesFile(context)

  logFreeDiskSpace("before compilation", context)
  val distributionState = compileAllModulesAndCreateDistributionState(context)
  logFreeDiskSpace("after compilation", context)

  coroutineScope {
    createMavenArtifactJob(context, distributionState)

    if (!context.shouldBuildDistributions()) {
      Span.current().addEvent("skip building product distributions because 'intellij.build.target.os' property is set to '${BuildOptions.OS_NONE}'")
      val pluginsToPublish = getPluginLayoutsByJpsModuleNames(
        modules = context.productProperties.productLayout.pluginModulesToPublish,
        productLayout = context.productProperties.productLayout,
      )
      buildNonBundledPlugins(
        pluginsToPublish = pluginsToPublish,
        compressPluginArchive = context.options.compressZipFiles,
        buildPlatformLibJob = null,
        state = distributionState,
        searchableOptionSet = buildSearchableOptions(context),
        context = context
      )
      return@coroutineScope
    }

    val contentReport = spanBuilder("build platform and plugin JARs").use {
      val contentReport = buildDistribution(state = distributionState, context)
      if (context.productProperties.buildSourcesArchive) {
        buildSourcesArchive(contentReport, context)
      }
      contentReport
    }

    layoutShared(context)

    val distDirs = buildOsSpecificDistributions(context)
    launch(Dispatchers.IO + CoroutineName("generate software bill of materials")) {
      context.executeStep(spanBuilder("generate software bill of materials"), SoftwareBillOfMaterials.STEP_ID) {
        SoftwareBillOfMaterialsImpl(context = context, distributions = distDirs, distributionFiles = contentReport.bundled().toList()).generate()
      }
    }

    if (context.productProperties.buildCrossPlatformDistribution) {
      if (distDirs.size == SUPPORTED_DISTRIBUTIONS.size) {
        context.executeStep(spanBuilder("build cross-platform distribution"), BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP) {
          buildCrossPlatformZip(distDirs, context)
        }
      }
      else {
        Span.current().addEvent("skip building cross-platform distribution because some OS/arch-specific distributions were skipped")
      }
    }

    logFreeDiskSpace("after building distributions", context)
  }
}

private fun CoroutineScope.createMavenArtifactJob(context: BuildContext, distributionState: DistributionBuilderState): Job? {
  val mavenArtifacts = context.productProperties.mavenArtifacts
  if (!mavenArtifacts.forIdeModules &&
      mavenArtifacts.additionalModules.isEmpty() &&
      mavenArtifacts.squashedModules.isEmpty() &&
      mavenArtifacts.proprietaryModules.isEmpty()) {
    return null
  }

  return createSkippableJob(spanBuilder("generate maven artifacts"), BuildOptions.MAVEN_ARTIFACTS_STEP, context) {
    val platformModules = HashSet<String>()
    if (mavenArtifacts.forIdeModules) {
      platformModules.addAll(distributionState.platformModules)
      collectIncludedPluginModules(enabledPluginModules = context.getBundledPluginModules(), result = platformModules, context = context)
    }

    val mavenArtifactsBuilder = MavenArtifactsBuilder(context)
    val builtArtifacts = mutableSetOf<MavenArtifactData>()
    @Suppress("UsePropertyAccessSyntax")
    if (!platformModules.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(
        moduleNamesToPublish = platformModules,
        outputDir = "maven-artifacts",
        builtArtifacts = builtArtifacts,
        ignoreNonMavenizable = true,
      )
    }
    if (!mavenArtifacts.additionalModules.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(
        moduleNamesToPublish = mavenArtifacts.additionalModules,
        moduleNamesToSquashAndPublish = mavenArtifacts.squashedModules,
        builtArtifacts = builtArtifacts,
        outputDir = "maven-artifacts"
      )
    }
    if (!mavenArtifacts.proprietaryModules.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(
        moduleNamesToPublish = mavenArtifacts.proprietaryModules,
        builtArtifacts = builtArtifacts,
        outputDir = "proprietary-maven-artifacts"
      )
    }
  }
}

private suspend fun checkProductProperties(context: BuildContext) {
  checkProductLayout(context)

  val properties = context.productProperties
  checkPaths2(properties.brandingResourcePaths, "productProperties.brandingResourcePaths")
  checkPaths2(properties.additionalIDEPropertiesFilePaths, "productProperties.additionalIDEPropertiesFilePaths")
  checkPaths2(properties.additionalDirectoriesWithLicenses, "productProperties.additionalDirectoriesWithLicenses")
  checkModule(properties.applicationInfoModule, "productProperties.applicationInfoModule", context)
  properties.embeddedJetBrainsClientMainModule?.let { embeddedJetBrainsClientMainModule ->
    checkModule(embeddedJetBrainsClientMainModule, "productProperties.embeddedJetBrainsClientMainModule", context)
    if (findProductModulesFile(context, embeddedJetBrainsClientMainModule) == null) {
      context.messages.error(
        "Cannot find product-modules.xml file in sources of '$embeddedJetBrainsClientMainModule' module specified as " +
        "'productProperties.embeddedJetBrainsClientMainModule'."
      )
    }
  }
  properties.rootModuleForModularLoader?.let { rootModule ->
    checkModule(rootModule, "productProperties.rootModuleForModularLoader", context)
    if (properties.productLayout.bundledPluginModules.isNotEmpty()) {
      context.messages.error(
        """
        |'${properties.javaClass.name}' uses module-based loader, so the following bundled plugins must be specified in product-modules.xml file 
        |located in '$rootModule', not via 'productLayout.bundledPluginModules' property: 
        |${properties.productLayout.bundledPluginModules.joinToString("\n")}
        |""".trimMargin()
      )
    }
  }

  checkModules(properties.modulesToCompileTests, "productProperties.modulesToCompileTests", context)

  context.windowsDistributionCustomizer?.let { winCustomizer ->
    checkPaths(listOfNotNull(winCustomizer.icoPath), "productProperties.windowsCustomizer.icoPath")
    checkPaths(listOfNotNull(winCustomizer.icoPathForEAP), "productProperties.windowsCustomizer.icoPathForEAP")
    checkPaths(listOfNotNull(winCustomizer.installerImagesPath), "productProperties.windowsCustomizer.installerImagesPath")
  }

  context.linuxDistributionCustomizer?.let { linuxDistributionCustomizer ->
    checkPaths(listOfNotNull(linuxDistributionCustomizer.iconPngPath), "productProperties.linuxCustomizer.iconPngPath")
    checkPaths(listOfNotNull(linuxDistributionCustomizer.iconPngPathForEAP), "productProperties.linuxCustomizer.iconPngPathForEAP")
  }

  context.macDistributionCustomizer?.let { macCustomizer ->
    checkMandatoryField(macCustomizer.bundleIdentifier, "productProperties.macCustomizer.bundleIdentifier")
    checkMandatoryPath(macCustomizer.icnsPath, "productProperties.macCustomizer.icnsPath")
    checkPaths(listOfNotNull(macCustomizer.icnsPathForEAP), "productProperties.macCustomizer.icnsPathForEAP")
    checkPaths(listOfNotNull(macCustomizer.icnsPathForAlternativeIcon), "productProperties.macCustomizer.icnsPathForAlternativeIcon")
    checkPaths(
      listOfNotNull(macCustomizer.icnsPathForAlternativeIconForEAP),
      "productProperties.macCustomizer.icnsPathForAlternativeIconForEAP"
    )
    context.executeStep(spanBuilder("check .dmg images"), BuildOptions.MAC_DMG_STEP) {
      checkMandatoryPath(macCustomizer.dmgImagePath, "productProperties.macCustomizer.dmgImagePath")
      checkPaths(listOfNotNull(macCustomizer.dmgImagePathForEAP), "productProperties.macCustomizer.dmgImagePathForEAP")
    }
  }

  checkModules(properties.mavenArtifacts.additionalModules, "productProperties.mavenArtifacts.additionalModules", context)
  checkModules(properties.mavenArtifacts.squashedModules, "productProperties.mavenArtifacts.squashedModules", context)
  if (context.productProperties.scrambleMainJar) {
    context.proprietaryBuildTools.scrambleTool?.let {
      checkModules(modules = it.namesOfModulesRequiredToBeScrambled, fieldName = "ProprietaryBuildTools.scrambleTool.namesOfModulesRequiredToBeScrambled", context = context)
    }
  }
}

private fun checkProductLayout(context: BuildContext) {
  val layout = context.productProperties.productLayout
  // todo mainJarName type specified as not-null - does it work?
  val messages = context.messages

  val pluginLayouts = layout.pluginLayouts
  checkPluginDuplicates(pluginLayouts)
  checkPluginModules(layout.bundledPluginModules, "productProperties.productLayout.bundledPluginModules", context)
  checkPluginModules(layout.pluginModulesToPublish, "productProperties.productLayout.pluginModulesToPublish", context)
  checkPluginModules(layout.compatiblePluginsToIgnore, "productProperties.productLayout.compatiblePluginsToIgnore", context)
  if (!layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
    messages.warning(
      "layout.buildAllCompatiblePlugins option isn't enabled. Value of " +
      "layout.compatiblePluginsToIgnore property will be ignored (${layout.compatiblePluginsToIgnore})"
    )
  }
  if (layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
    checkPluginModules(layout.compatiblePluginsToIgnore, "productProperties.productLayout.compatiblePluginsToIgnore", context)
  }
  if (!context.shouldBuildDistributions() && layout.buildAllCompatiblePlugins) {
    messages.warning(
      "Distribution is not going to build. Hence all compatible plugins won't be built despite " +
      "layout.buildAllCompatiblePlugins option is enabled. layout.pluginModulesToPublish will be used (" +
      layout.pluginModulesToPublish + ")"
    )
  }
  check(
    !layout.prepareCustomPluginRepositoryForPublishedPlugins ||
    !layout.pluginModulesToPublish.isEmpty() ||
    layout.buildAllCompatiblePlugins
  ) {
    "productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins option is enabled" +
    " but no pluginModulesToPublish are specified"
  }
  checkModules(layout.productApiModules, "productProperties.productLayout.productApiModules", context)
  checkModules(layout.productImplementationModules, "productProperties.productLayout.productImplementationModules", context)
  checkModules(layout.moduleExcludes.keys, "productProperties.productLayout.moduleExcludes", context)
  for (plugin in pluginLayouts) {
    checkBaseLayout(plugin, "\'${plugin.mainModule}\' plugin", context)
  }
  checkPlatformSpecificPluginResources(pluginLayouts = pluginLayouts, pluginModulesToPublish = layout.pluginModulesToPublish)
}

private fun checkBaseLayout(layout: BaseLayout, description: String, context: BuildContext) {
  checkModules(layout.includedModules.asSequence().map { it.moduleName }.distinct().toList(), "moduleJars in $description", context)
  checkArtifacts(layout.includedArtifacts.keys, "includedArtifacts in $description", context)
  checkModules(layout.resourcePaths.map { it.moduleName }, "resourcePaths in $description", context)
  checkModules(layout.moduleExcludes.keys, "moduleExcludes in $description", context)

  checkProjectLibraries(names = layout.includedProjectLibraries.map { it.libraryName }, fieldName = "includedProjectLibraries in $description", context = context)

  for ((moduleName, libraryName) in layout.includedModuleLibraries) {
    checkModules(listOf(moduleName), "includedModuleLibraries in $description", context)
    check(context.findRequiredModule(moduleName).libraryCollection.libraries.any { getLibraryFileName(it) == libraryName }) {
      "Cannot find library \'$libraryName\' in \'$moduleName\' (used in $description)"
    }
  }

  if (layout is PluginLayout) {
    checkModules(modules = layout.excludedLibraries.keys, fieldName = "excludedModuleLibraries in $description", context = context)
    for ((key, value) in layout.excludedLibraries.entries) {
      val libraries = (if (key == null) context.project.libraryCollection else context.findRequiredModule(key).libraryCollection).libraries
      for (libraryName in value) {
        check(libraries.any { getLibraryFileName(it) == libraryName }) {
          val where = key?.let { "module \'$it\'" } ?: "project"
          "Cannot find library \'$libraryName\' in $where (used in \'excludedModuleLibraries\' in $description)"
        }
      }
    }

    checkModules(layout.modulesWithExcludedModuleLibraries, "modulesWithExcludedModuleLibraries in $description", context)
  }
}

private fun checkPluginDuplicates(nonTrivialPlugins: List<PluginLayout>) {
  val pluginsGroupedByMainModule = nonTrivialPlugins.groupBy { it.mainModule to it.bundlingRestrictions }.values
  for (duplicatedPlugins in pluginsGroupedByMainModule) {
    check(duplicatedPlugins.size <= 1) {
      "Duplicated plugin description in productLayout.pluginLayouts: main module ${duplicatedPlugins.first().mainModule}"
    }
  }

  // indexing-shared-ultimate has a separate layout for bundled and public plugins
  val duplicateDirectoryNameExceptions = setOf("indexing-shared-ultimate")

  val pluginsGroupedByDirectoryName = nonTrivialPlugins.groupBy { it.directoryName to it.bundlingRestrictions }.values
  for (duplicatedPlugins in pluginsGroupedByDirectoryName) {
    val pluginDirectoryName = duplicatedPlugins.first().directoryName
    if (duplicateDirectoryNameExceptions.contains(pluginDirectoryName)) {
      continue
    }

    check(duplicatedPlugins.size <= 1) {
      "Duplicated plugin description in productLayout.pluginLayouts: directory name '$pluginDirectoryName', main modules: ${duplicatedPlugins.joinToString { it.mainModule }}"
    }
  }
}

private fun checkModules(modules: Collection<String?>?, fieldName: String, context: CompilationContext) {
  if (modules != null) {
    val unknownModules = modules.filter { it != null && context.findModule(it) == null }
    check(unknownModules.isEmpty()) {
      "The following modules from $fieldName aren\'t found in the project: $unknownModules"
    }
  }
}

private fun checkModule(moduleName: String?, fieldName: String, context: CompilationContext) {
  if (moduleName != null && context.findModule(moduleName) == null) {
    context.messages.error("Module '$moduleName' from $fieldName isn't found in the project")
  }
}

private fun checkArtifacts(names: Collection<String>, fieldName: String, context: CompilationContext) {
  val unknownArtifacts = names - JpsArtifactService.getInstance().getArtifacts(context.project).map { it.name }.toSet()
  check(unknownArtifacts.isEmpty()) {
    "The following artifacts from $fieldName aren\'t found in the project: $unknownArtifacts"
  }
}

private fun checkPluginModules(pluginModules: Collection<String>?, fieldName: String, context: BuildContext) {
  if (pluginModules == null) {
    return
  }

  checkModules(modules = pluginModules, fieldName = fieldName, context = context)

  val unknownBundledPluginModules = pluginModules.filter { context.findModule(it)?.let { findFileInModuleSources(it, "META-INF/plugin.xml") } == null }
  check(unknownBundledPluginModules.isEmpty()) {
    "The following modules from $fieldName don\'t contain META-INF/plugin.xml file and aren\'t specified as optional plugin modules" +
    "in productProperties.productLayout.pluginLayouts: ${unknownBundledPluginModules.joinToString()}."
  }
}

private fun checkPaths(paths: Collection<String>, propertyName: String) {
  val nonExistingFiles = paths.filter { Files.notExists(Path.of(it)) }
  check(nonExistingFiles.isEmpty()) {
    "$propertyName contains non-existing files: ${nonExistingFiles.joinToString()}"
  }
}

private fun checkPaths2(paths: Collection<Path>, propertyName: String) {
  val nonExistingFiles = paths.filter { Files.notExists(it) }
  check(nonExistingFiles.isEmpty()) {
    "$propertyName contains non-existing files: ${nonExistingFiles.joinToString()}"
  }
}

private fun checkMandatoryField(value: String?, fieldName: String) {
  checkNotNull(value) {
    "Mandatory property \'$fieldName\' is not specified"
  }
}

private fun checkMandatoryPath(path: String, fieldName: String) {
  checkMandatoryField(path, fieldName)
  checkPaths(listOf(path), fieldName)
}

private fun logFreeDiskSpace(phase: String, context: CompilationContext) {
  if (context.options.printFreeSpace) {
    logFreeDiskSpace(context.paths.buildOutputDir, phase)
  }
}

private suspend fun buildCrossPlatformZip(distResults: List<DistributionForOsTaskResult>, context: BuildContext): Path {
  val executableName = context.productProperties.baseFileName

  val productJson = generateProductInfoJson(
    relativePathToBin = "bin",
    builtinModules = context.builtinModule,
    launch = sequenceOf(JvmArchitecture.x64, JvmArchitecture.aarch64).flatMap { arch ->
      listOf(
        ProductInfoLaunchData(
          os = OsFamily.WINDOWS.osName,
          arch = arch.dirName,
          launcherPath = "bin/${executableName}.bat",
          javaExecutablePath = null,
          vmOptionsFilePath = "bin/win/${executableName}64.exe.vmoptions",
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.WINDOWS, arch, isPortableDist = true),
          mainClass = context.ideMainClassName
        ),
        ProductInfoLaunchData(
          os = OsFamily.LINUX.osName,
          arch = arch.dirName,
          launcherPath = "bin/${executableName}.sh",
          javaExecutablePath = null,
          vmOptionsFilePath = "bin/linux/${executableName}64.vmoptions",
          startupWmClass = getLinuxFrameClass(context),
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.LINUX, arch, isPortableDist = true),
          mainClass = context.ideMainClassName
        ),
        ProductInfoLaunchData(
          os = OsFamily.MACOS.osName,
          arch = arch.dirName,
          launcherPath = "bin/${executableName}.sh",
          javaExecutablePath = null,
          vmOptionsFilePath = "bin/mac/${executableName}.vmoptions",
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.MACOS, arch, isPortableDist = true),
          mainClass = context.ideMainClassName
        )
      )
    }.toList(),
    context = context,
  )

  val runtimeModuleRepositoryPath = if (context.generateRuntimeModuleRepository) {
    spanBuilder("generate runtime repository for cross-platform distribution").use {
      generateCrossPlatformRepository(context.paths.distAllDir, distResults.filter { it.arch == JvmArchitecture.x64 }.map { it.outDir }, context)
    }
  }
  else {
    null
  }

  val zipFileName = context.productProperties.getCrossPlatformZipFileName(context.applicationInfo, context.buildNumber)
  val targetFile = context.paths.artifactDir.resolve(zipFileName)
  val dependenciesFile = copyDependenciesFile(context)
  crossPlatformZip(
    macX64DistDir = distResults.first { it.builder.targetOs == OsFamily.MACOS && it.arch == JvmArchitecture.x64 }.outDir,
    macArm64DistDir = distResults.first { it.builder.targetOs == OsFamily.MACOS && it.arch == JvmArchitecture.aarch64 }.outDir,
    linuxX64DistDir = distResults.first { it.builder.targetOs == OsFamily.LINUX && it.arch == JvmArchitecture.x64 }.outDir,
    winX64DistDir = distResults.first { it.builder.targetOs == OsFamily.WINDOWS && it.arch == JvmArchitecture.x64 }.outDir,
    targetFile = targetFile,
    executableName = executableName,
    productJson = productJson.encodeToByteArray(),
    executablePatterns = distResults.flatMap {
      it.builder.generateExecutableFilesMatchers(includeRuntime = false, JvmArchitecture.x64).keys +
      it.builder.generateExecutableFilesMatchers(includeRuntime = false, JvmArchitecture.aarch64).keys
    },
    distFiles = context.getDistFiles(os = null, arch = null),
    extraFiles = mapOf("dependencies.txt" to dependenciesFile),
    distAllDir = context.paths.distAllDir,
    compress = context.options.compressZipFiles,
    runtimeModuleRepositoryPath = runtimeModuleRepositoryPath,
  )

  checkInArchive(archiveFile = targetFile, pathInArchive = "", context = context)
  context.notifyArtifactBuilt(targetFile)
  return targetFile
}

private suspend fun checkClassFiles(root: Path, context: BuildContext, isDistAll: Boolean) {
  // version checking patterns are only for dist all (all non-os and non-arch specific files)
  if (!isDistAll) {
    return
  }

  context.executeStep(spanBuilder("checkClassFiles"), BuildOptions.VERIFY_CLASS_FILE_VERSIONS) { span ->
    val versionCheckerConfig = context.productProperties.versionCheckerConfig
    val forbiddenSubPaths = context.productProperties.forbiddenClassFileSubPaths
    val forbiddenSubPathExceptions = context.productProperties.forbiddenClassFileSubPathExceptions
    if (forbiddenSubPaths.isNotEmpty()) {
      val forbiddenString = forbiddenSubPaths.let { "(${it.size}): ${it.joinToString()}" }
      val exceptionsString = forbiddenSubPathExceptions.let { "(${it.size}): ${it.joinToString()}" }
      span.addEvent("forbiddenSubPaths $forbiddenString, exceptions $exceptionsString")
    }
    else {
      span.addEvent("forbiddenSubPaths: EMPTY (no scrambling checks will be done)")
    }

    if (versionCheckerConfig.isNotEmpty() || forbiddenSubPaths.isNotEmpty()) {
      checkClassFiles(versionCheckConfig = versionCheckerConfig, forbiddenSubPaths = forbiddenSubPaths, forbiddenSubPathExceptions = forbiddenSubPathExceptions, root = root)
    }

    if (forbiddenSubPaths.isNotEmpty()) {
      span.addEvent("SUCCESS for forbiddenSubPaths at '$root': ${forbiddenSubPaths.joinToString()}")
    }
  }
}

private fun checkPlatformSpecificPluginResources(pluginLayouts: List<PluginLayout>, pluginModulesToPublish: Set<String>) {
  val offenders = pluginLayouts.filter { it.platformResourceGenerators.isNotEmpty() && it.mainModule in pluginModulesToPublish }
  check(offenders.isEmpty()) {
    "Non-bundled plugins are not allowed yet to specify platform-specific resources. Offenders:\n  ${offenders.joinToString("  \n")}"
  }
}

fun getOsDistributionBuilder(os: OsFamily, ideaProperties: CharSequence? = null, context: BuildContext): OsSpecificDistributionBuilder? {
  return when (os) {
    OsFamily.WINDOWS -> context.windowsDistributionCustomizer?.let {
      WindowsDistributionBuilder(context = context, customizer = it, ideaProperties = ideaProperties)
    }
    OsFamily.MACOS -> context.macDistributionCustomizer?.let {
      MacDistributionBuilder(context = context, customizer = it, ideaProperties = ideaProperties)
    }
    OsFamily.LINUX -> context.linuxDistributionCustomizer?.let {
      LinuxDistributionBuilder(context = context, customizer = it, ideaProperties = ideaProperties)
    }
  }
}

// keep in sync with AppUIUtil#getFrameClass
internal fun getLinuxFrameClass(context: BuildContext): String {
  val name = context.applicationInfo.productNameWithEdition
    .lowercase()
    .replace(' ', '-')
    .replace("intellij-idea", "idea")
    .replace("android-studio", "studio")
    .replace("-community-edition", "-ce")
    .replace("-ultimate-edition", "")
    .replace("-professional-edition", "")
  return if (name.startsWith("jetbrains-")) name else "jetbrains-$name"
}

private fun crossPlatformZip(
  macX64DistDir: Path,
  macArm64DistDir: Path,
  linuxX64DistDir: Path,
  winX64DistDir: Path,
  targetFile: Path,
  executableName: String,
  productJson: ByteArray,
  executablePatterns: List<PathMatcher>,
  distFiles: Collection<DistFile>,
  extraFiles: Map<String, Path>,
  distAllDir: Path,
  compress: Boolean,
  runtimeModuleRepositoryPath: Path?,
) {
  writeNewFile(targetFile) { outFileChannel ->
    NoDuplicateZipArchiveOutputStream(outFileChannel, compress = compress).use { out ->
      out.setUseZip64(Zip64Mode.Never)

      out.entryToDir(winX64DistDir.resolve("bin/idea.properties"), "bin/win")
      out.entryToDir(linuxX64DistDir.resolve("bin/idea.properties"), "bin/linux")
      out.entryToDir(macX64DistDir.resolve("bin/idea.properties"), "bin/mac")

      out.entryToDir(macX64DistDir.resolve("bin/${executableName}.vmoptions"), "bin/mac")
      out.entry("bin/mac/${executableName}64.vmoptions", macX64DistDir.resolve("bin/${executableName}.vmoptions"))

      for ((p, f) in extraFiles) {
        out.entry(p, f)
      }

      out.entry(PRODUCT_INFO_FILE_NAME, productJson)

      Files.newDirectoryStream(winX64DistDir.resolve("bin")).use {
        for (file in it) {
          val path = file.toString()
          if (path.endsWith(".exe.vmoptions")) {
            out.entryToDir(file, "bin/win")
          }
          else {
            val fileName = file.fileName.toString()
            if (fileName.startsWith("fsnotifier") && fileName.endsWith(".exe")) {
              out.entry("bin/win/$fileName", file)
            }
          }
        }
      }

      Files.newDirectoryStream(linuxX64DistDir.resolve("bin")).use {
        for (file in it) {
          val name = file.fileName.toString()
          when {
            name.endsWith(".vmoptions") -> out.entryToDir(file, "bin/linux")
            name.endsWith(".sh") -> out.entry("bin/${file.fileName}", file, unixMode = executableFileUnixMode)
            name == "fsnotifier" -> out.entry("bin/linux/${name}", file, unixMode = executableFileUnixMode)
          }
        }
      }

      // At the moment, there is no ARM64 hardware suitable for painless IDEA plugin development,
      // so corresponding artifacts are not packed in.

      Files.newDirectoryStream(macX64DistDir.resolve("bin")).use {
        for (file in it) {
          if (file.toString().endsWith(".jnilib")) {
            out.entry("bin/mac/${file.fileName.toString().removeSuffix(".jnilib")}.dylib", file)
          }
          else {
            val fileName = file.fileName.toString()
            if (fileName.startsWith("printenv")) {
              out.entry("bin/$fileName", file, unixMode = executableFileUnixMode)
            }
            else if (fileName.startsWith("fsnotifier")) {
              out.entry("bin/mac/$fileName", file, unixMode = executableFileUnixMode)
            }
          }
        }
      }

      val entryCustomizer: (ZipArchiveEntry, Path, String) -> Unit = { entry, _, relativePathString ->
        val relativePath = Path.of(relativePathString)
        if (executablePatterns.any { it.matches(relativePath) }) {
          entry.unixMode = executableFileUnixMode
        }
      }

      val commonFilter: (String) -> Boolean = { relPath ->
        !relPath.startsWith("bin/fsnotifier") &&
        !relPath.startsWith("bin/repair") &&
        !relPath.startsWith("bin/restart") &&
        !relPath.startsWith("bin/printenv") &&
        !(relPath.startsWith("bin/") && (relPath.endsWith(".sh") || relPath.endsWith(".vmoptions")) && relPath.count { it == '/' } == 1) &&
        relPath != "bin/idea.properties" &&
        !relPath.startsWith("help/") &&
        relPath != "license/launcher-third-party-libraries.html" &&
        relPath != MODULE_DESCRIPTORS_JAR_PATH &&
        relPath != PLUGIN_CLASSPATH &&
        !relPath.startsWith("bin/remote-dev-server") &&
        relPath != "license/remote-dev-server.html"
      }

      val zipFileUniqueGuard = HashMap<String, DistFileContent>()

      out.dir(startDir = distAllDir, prefix = "", fileFilter = { _, relPath -> relPath != "bin/idea.properties" }, entryCustomizer = entryCustomizer)
      if (runtimeModuleRepositoryPath != null) {
        out.entry(MODULE_DESCRIPTORS_JAR_PATH, runtimeModuleRepositoryPath)
      }

      for (macDistDir in arrayOf(macX64DistDir, macArm64DistDir)) {
        out.dir(macDistDir, "", fileFilter = { _, relPath ->
          commonFilter.invoke(relPath) &&
          !relPath.startsWith("MacOS/") &&
          !relPath.startsWith("Resources/") &&
          !relPath.startsWith("Info.plist") &&
          !relPath.startsWith("Helpers/") &&
          filterFileIfAlreadyInZip(relativePath = relPath, file = macArm64DistDir.resolve(relPath), zipFiles = zipFileUniqueGuard)
        }, entryCustomizer = entryCustomizer)
      }

      out.dir(linuxX64DistDir, "", fileFilter = { _, relPath ->
        commonFilter.invoke(relPath) &&
        filterFileIfAlreadyInZip(relPath, linuxX64DistDir.resolve(relPath), zipFileUniqueGuard)
      }, entryCustomizer = entryCustomizer)

      out.dir(startDir = winX64DistDir, prefix = "", fileFilter = { _, relPath ->
        commonFilter.invoke(relPath) &&
        !(relPath.startsWith("bin/${executableName}") && relPath.endsWith(".exe")) &&
        filterFileIfAlreadyInZip(relativePath = relPath, file = winX64DistDir.resolve(relPath), zipFiles = zipFileUniqueGuard)
      }, entryCustomizer = entryCustomizer)

      for (distFile in distFiles) {
        // Linux and Windows: we don't add specific dist dirs for ARM, so, copy dist files explicitly
        // macOS: we don't copy dist files to avoid extra copy operation
        val content = distFile.content
        if (zipFileUniqueGuard.putIfAbsent(distFile.relativePath, content) == null) {
          when (content) {
            is LocalDistFileContent -> out.entry(distFile.relativePath, content.file)
            is InMemoryDistFileContent -> out.entry(distFile.relativePath, content.data)
          }
        }
      }
    }
  }
}

// Captures information about all available inspections in a JSON format as part of an Inspectopedia project.
// This is later used by Qodana and other tools.
// Keymaps are extracted as an XML file and also used in authoring help.
internal suspend fun buildAdditionalAuthoringArtifacts(productRunner: IntellijProductRunner, context: BuildContext) {
  context.executeStep(spanBuilder("build authoring assets"), BuildOptions.DOC_AUTHORING_ASSETS_STEP) {
    val commands = listOf(
      Pair("inspectopedia-generator", "inspections-${context.applicationInfo.productCode.lowercase()}"),
      Pair("keymap", "keymap-${context.applicationInfo.productCode.lowercase()}")
    )
    val temporaryBuildDirectory = context.paths.tempDir
    for (command in commands) {
      launch(CoroutineName("build ${command.first}")) {
        val targetPath = temporaryBuildDirectory.resolve(command.first).resolve(command.second)
        productRunner.runProduct(args = listOf(command.first, targetPath.toString()), timeout = DEFAULT_TIMEOUT)

        val targetFile = context.paths.artifactDir.resolve("${command.second}.zip")
        zipWithCompression(
          targetFile = targetFile,
          dirs = mapOf(targetPath to ""),
          compressionLevel = if (context.options.compressZipFiles) Deflater.DEFAULT_COMPRESSION else Deflater.NO_COMPRESSION,
        )
      }
    }
  }
}

internal suspend fun setLastModifiedTime(directory: Path, context: BuildContext) {
  spanBuilder("update last modified time").setAttribute("dir", directory.toString()).use(Dispatchers.IO) {
    val fileTime = FileTime.from(context.options.buildDateInSeconds, TimeUnit.SECONDS)
    Files.walkFileTree(directory, object : SimpleFileVisitor<Path>() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        Files.setLastModifiedTime(file, fileTime)
        return FileVisitResult.CONTINUE
      }
    })
  }
}

/**
 * @return list of all modules which output is included in the plugin's JARs
 */
internal fun collectIncludedPluginModules(enabledPluginModules: Collection<String>, result: MutableSet<String>, context: BuildContext) {
  result.addAll(enabledPluginModules)
  val pluginLayouts = getPluginLayoutsByJpsModuleNames(modules = enabledPluginModules, productLayout = context.productProperties.productLayout)
  for (plugin in pluginLayouts) {
    plugin.includedModules.mapTo(result) { it.moduleName }
    result.addAll((context as BuildContextImpl).jarPackagerDependencyHelper.readPluginIncompleteContentFromDescriptor(context.findRequiredModule(plugin.mainModule)))
  }
}

internal fun copyDistFiles(context: BuildContext, newDir: Path, os: OsFamily, arch: JvmArchitecture) {
  for (item in context.getDistFiles(os, arch)) {
    val targetFile = newDir.resolve(item.relativePath)
    Files.createDirectories(targetFile.parent)
    if (item.content is LocalDistFileContent) {
      Files.copy(item.content.file, targetFile, StandardCopyOption.REPLACE_EXISTING)
    }
    else {
      Files.write(targetFile, (item.content as InMemoryDistFileContent).data)
    }
  }
}

internal fun generateBuildTxt(context: BuildContext, targetDirectory: Path) {
  Files.writeString(targetDirectory.resolve("build.txt"), context.fullBuildNumber)
}

internal fun copyInspectScript(context: BuildContext, distBinDir: Path) {
  val inspectScript = context.productProperties.inspectCommandName
  if (inspectScript != "inspect") {
    val targetPath = distBinDir.resolve("$inspectScript.sh")
    Files.move(distBinDir.resolve("inspect.sh"), targetPath, StandardCopyOption.REPLACE_EXISTING)
    context.patchInspectScript(targetPath)
  }
}
