// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.buildData.productInfo.ProductInfoLaunchData
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.system.CpuArch
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.DistFileContent
import org.jetbrains.intellij.build.InMemoryDistFileContent
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LocalDistFileContent
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.SoftwareBillOfMaterials
import org.jetbrains.intellij.build.buildSearchableOptions
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.impl.maven.MavenArtifactData
import org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder
import org.jetbrains.intellij.build.impl.moduleBased.findProductModulesFile
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.impl.productInfo.generateProductInfoJson
import org.jetbrains.intellij.build.impl.productInfo.validateProductJson
import org.jetbrains.intellij.build.impl.projectStructureMapping.ContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.getIncludedModules
import org.jetbrains.intellij.build.impl.sbom.SoftwareBillOfMaterialsImpl
import org.jetbrains.intellij.build.io.DEFAULT_TIMEOUT
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.logFreeDiskSpace
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.productRunner.IntellijProductRunner
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.block
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.intellij.build.zipSourcesOfModules
import org.jetbrains.jps.model.artifact.JpsArtifactService
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.util.Collections
import java.util.EnumSet
import java.util.SortedSet
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import kotlin.io.path.relativeTo

internal const val PROPERTIES_FILE_NAME: String = "idea.properties"

internal class BuildTasksImpl(private val context: BuildContextImpl) : BuildTasks {
  override suspend fun buildDistributions() {
    buildDistributions(context)
  }

  override suspend fun buildNonBundledPlugins(mainPluginModules: List<String>, dependencyModules: List<String>) {
    checkProductProperties(context)
    checkPluginModules(mainPluginModules, "mainPluginModules", context)
    copyDependenciesFile(context)
    val pluginsToPublish = getPluginLayoutsByJpsModuleNames(mainPluginModules, context.productProperties.productLayout, toPublish = true)
    val distState = createDistributionBuilderState(pluginsToPublish, context)
    context.compileModules(null)

    buildProjectArtifacts(distState.platform, getEnabledPluginModules(distState.pluginsToPublish, context), context)

    val searchableOptionSet = buildSearchableOptions(context.createProductRunner(mainPluginModules + dependencyModules), context)
    buildNonBundledPlugins(pluginsToPublish, context.options.compressZipFiles, null, distState, searchableOptionSet, context)
  }

  override suspend fun buildUnpackedDistribution(targetDirectory: Path, includeBinAndRuntime: Boolean) {
    val currentOs = OsFamily.currentOs
    context.paths.distAllDir = targetDirectory
    context.options.targetOs = persistentListOf(currentOs)
    context.options.buildStepsToSkip += sequenceOf(
      SoftwareBillOfMaterials.STEP_ID,
    )
    context.reportDistributionBuildNumber()
    BundledMavenDownloader.downloadMaven4Libs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMaven3Libs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMavenDistribution(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMavenTelemetryDependencies(context.paths.communityHomeDirRoot)
    buildDistribution(state = createDistributionState(context), context, isUpdateFromSources = true)
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
      val builder = getOsDistributionBuilder(currentOs, context, propertiesFile)!!
      builder.copyFilesForOsDistribution(targetDirectory, arch)
      context.bundledRuntime.extractTo(currentOs, arch, targetDirectory.resolve("jbr"))
      updateExecutablePermissions(targetDirectory, builder.generateExecutableFilesMatchers(includeRuntime = true, arch).keys)
      builder.checkExecutablePermissions(targetDirectory, root = "", includeRuntime = true, arch)
      builder.writeProductInfoFile(targetDirectory, arch)
    }
    else {
      copyDistFiles(context, targetDirectory, currentOs, arch)
    }
  }
}

data class SupportedDistribution(@JvmField val os: OsFamily, @JvmField val arch: JvmArchitecture)

@JvmField
val SUPPORTED_DISTRIBUTIONS: List<SupportedDistribution> = listOf(
  SupportedDistribution(OsFamily.WINDOWS, JvmArchitecture.x64),
  SupportedDistribution(OsFamily.WINDOWS, JvmArchitecture.aarch64),
  SupportedDistribution(OsFamily.MACOS, JvmArchitecture.x64),
  SupportedDistribution(OsFamily.MACOS, JvmArchitecture.aarch64),
  SupportedDistribution(OsFamily.LINUX, JvmArchitecture.x64),
  SupportedDistribution(OsFamily.LINUX, JvmArchitecture.aarch64),
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
  map["settings_dir"] = settingsDir
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
  checkClassFiles(root = context.paths.distAllDir, context, isDistAll = true)
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
    "Cannot find '$normalizedRelativePath' " +
    "neither in sources of '${context.productProperties.applicationInfoModule}' " +
    "nor in ${context.productProperties.brandingResourcePaths}"
  )
}

internal suspend fun updateExecutablePermissions(destinationDir: Path, executableFilesMatchers: Collection<PathMatcher>) {
  spanBuilder("update executable permissions").setAttribute("dir", "$destinationDir").use(Dispatchers.IO) {
    val executable = EnumSet.of(
      PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE
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
            recursivelySignMacBinaries(root = file, context)
          }
        }
      }
    }

    val ideaPropertyFileContent = createIdeaPropertyFile(context)

    spanBuilder("Adjust executable permissions on common dist").use {
      val matchers = SUPPORTED_DISTRIBUTIONS.mapNotNull {
        getOsDistributionBuilder(it.os, context)
      }.flatMap { builder ->
        JvmArchitecture.entries.flatMap { arch ->
          builder.generateExecutableFilesMatchers(includeRuntime = true, arch).keys
        }
      }
      updateExecutablePermissions(context.paths.distAllDir, matchers)
    }

    supervisorScope {
      SUPPORTED_DISTRIBUTIONS.mapNotNull { (os, arch) ->
        if (!context.shouldBuildDistributionForOS(os, arch)) {
          return@mapNotNull null
        }

        val builder = getOsDistributionBuilder(os, context, ideaPropertyFileContent) ?: return@mapNotNull null

        val stepId = "${os.osId} ${arch.name}"
        if (context.options.buildStepsToSkip.contains(stepId)) {
          Span.current().addEvent("skip step", Attributes.of(AttributeKey.stringKey("id"), stepId))
          return@mapNotNull null
        }

        async(CoroutineName("$stepId build step")) {
          spanBuilder(stepId).use {
            val osAndArchSpecificDistDirectory = getOsAndArchSpecificDistDirectory(os, arch, context)
            builder.buildArtifacts(osAndArchSpecificDistDirectory, arch)
            checkClassFiles(osAndArchSpecificDistDirectory, context, isDistAll = false)
            DistributionForOsTaskResult(builder, arch, osAndArchSpecificDistDirectory)
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

  error?.let { throw it }

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
    "The following libraries from $fieldName aren't found in the project: $unknownLibraries"
  }
}

private suspend fun buildSourcesArchive(contentReport: ContentReport, context: BuildContext) {
  val productProperties = context.productProperties
  val archiveName = "${productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)}-sources.zip"
  val openSourceModules = getIncludedModules(contentReport.bundled()).filter { moduleName ->
    productProperties.includeIntoSourcesArchiveFilter.test(context.findRequiredModule(moduleName), context)
  }.toList()
  zipSourcesOfModules(openSourceModules, targetFile = context.paths.artifactDir.resolve(archiveName), includeLibraries = true, context)
}

private suspend fun createDistributionState(context: BuildContext): DistributionBuilderState {
  val productLayout = context.productProperties.productLayout
  val pluginsToPublish = getPluginLayoutsByJpsModuleNames(productLayout.pluginModulesToPublish, productLayout, toPublish = true)
  filterPluginsToPublish(pluginsToPublish, context)

  val enabledPluginModules = getEnabledPluginModules(pluginsToPublish, context)
  // computed only based on a bundled and plugins to publish lists, compatible plugins are not taken in an account by intention
  val projectLibrariesUsedByPlugins = computeProjectLibsUsedByPlugins(enabledPluginModules, context)

  if (!context.shouldBuildDistributions() || context.isStepSkipped(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
    return distributionState(pluginsToPublish, projectLibrariesUsedByPlugins, enabledPluginModules, context)
  }

  return spanBuilder("collecting compatible plugins").use {
    val providedModuleFile = context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-builtinModules.json")
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
      collectCompatiblePluginsToPublish(builtinModuleData, pluginsToPublish, context)
      filterPluginsToPublish(pluginsToPublish, context)

      // update enabledPluginModules to reflect changes in pluginsToPublish - used for buildProjectArtifacts
      distributionState(pluginsToPublish, projectLibrariesUsedByPlugins, getEnabledPluginModules(pluginsToPublish, context), context)
    }
    else {
      val platform = createPlatformLayout(context)
      buildProjectArtifacts(platform, enabledPluginModules, context)
      DistributionBuilderState(platform, pluginsToPublish, context)
    }
  }
}

private suspend fun distributionState(
  pluginsToPublish: Set<PluginLayout>,
  projectLibrariesUsedByPlugins: SortedSet<ProjectLibraryData>,
  enabledPluginModules: Set<String>,
  context: BuildContext,
): DistributionBuilderState {
  val platform = createPlatformLayout(projectLibrariesUsedByPlugins, context)
  val distState = DistributionBuilderState(platform, pluginsToPublish, context)
  buildProjectArtifacts(platform, enabledPluginModules, context)
  return distState
}

private suspend fun buildProjectArtifacts(platform: PlatformLayout, enabledPluginModules: Set<String>, context: BuildContext) {
  val artifactNames = LinkedHashSet<String>()
  artifactNames.addAll(platform.includedArtifacts.keys)
  getPluginLayoutsByJpsModuleNames(enabledPluginModules, context.productProperties.productLayout)
    .flatMapTo(artifactNames) { it.includedArtifacts.keys }

  CompilationTasks.create(context).buildProjectArtifacts(artifactNames)
}

suspend fun buildDistributions(context: BuildContext): Unit = block("build distributions") {
  context.reportDistributionBuildNumber()

  checkProductProperties(context)
  checkLibraryUrls(context)

  copyDependenciesFile(context)

  logFreeDiskSpace("before compilation", context)
  context.compileModules(moduleNames = null) // compile all
  logFreeDiskSpace("after compilation", context)

  val distributionState = createDistributionState(context)

  coroutineScope {
    createMavenArtifactJob(context, distributionState)

    if (!context.shouldBuildDistributions()) {
      Span.current().addEvent("skip building product distributions because 'intellij.build.target.os' property is set to '${BuildOptions.OS_NONE}'")
      val pluginsToPublish = getPluginLayoutsByJpsModuleNames(
        context.productProperties.productLayout.pluginModulesToPublish,
        context.productProperties.productLayout,
        toPublish = true
      )
      buildNonBundledPlugins(
        pluginsToPublish, context.options.compressZipFiles, buildPlatformLibJob = null, distributionState, buildSearchableOptions(context), context
      )
      return@coroutineScope
    }

    val contentReport = spanBuilder("build platform and plugin JARs").use {
      val contentReport = buildDistribution(distributionState, context)
      if (context.productProperties.buildSourcesArchive) {
        buildSourcesArchive(contentReport, context)
      }
      contentReport
    }

    layoutShared(context)

    val distDirs = buildOsSpecificDistributions(context)

    lookForJunkFiles(context, listOf(context.paths.distAllDir) + distDirs.map { it.outDir })

    launch(Dispatchers.IO + CoroutineName("generate software bill of materials")) {
      context.executeStep(spanBuilder("generate software bill of materials"), SoftwareBillOfMaterials.STEP_ID) {
        SoftwareBillOfMaterialsImpl(context, distributions = distDirs, distributionFiles = contentReport.bundled().toList()).generate()
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
      collectIncludedPluginModules(context.getBundledPluginModules(), result = platformModules, context)
    }

    val mavenArtifactsBuilder = MavenArtifactsBuilder(context)
    val builtArtifacts = mutableMapOf<MavenArtifactData, List<Path>>()
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
    mavenArtifactsBuilder.validate(builtArtifacts)
  }
}

private suspend fun checkProductProperties(context: BuildContext) {
  checkProductLayout(context)

  val properties = context.productProperties
  checkPaths2(properties.brandingResourcePaths, "productProperties.brandingResourcePaths")
  checkPaths2(properties.additionalIDEPropertiesFilePaths, "productProperties.additionalIDEPropertiesFilePaths")
  checkPaths2(properties.additionalDirectoriesWithLicenses, "productProperties.additionalDirectoriesWithLicenses")
  checkModule(properties.applicationInfoModule, "productProperties.applicationInfoModule", context)
  properties.embeddedFrontendRootModule?.let { embeddedFrontendRootModule ->
    checkModule(embeddedFrontendRootModule, "productProperties.embeddedFrontendRootModule", context)
    if (findProductModulesFile(context, embeddedFrontendRootModule) == null) {
      context.messages.error(
        "Cannot find product-modules.xml file in sources of '$embeddedFrontendRootModule' module specified as " +
        "'productProperties.embeddedFrontendRootModule'."
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
      checkModules(modules = it.namesOfModulesRequiredToBeScrambled, fieldName = "ProprietaryBuildTools.scrambleTool.namesOfModulesRequiredToBeScrambled", context)
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
    checkBaseLayout(plugin, "'${plugin.mainModule}' plugin", context)
  }
  checkPlatformSpecificPluginResources(pluginLayouts, layout.pluginModulesToPublish)
}

private fun checkBaseLayout(layout: BaseLayout, description: String, context: BuildContext) {
  checkModules(layout.includedModules.asSequence().map { it.moduleName }.distinct().toList(), "moduleJars in $description", context)
  checkArtifacts(layout.includedArtifacts.keys, "includedArtifacts in $description", context)
  checkModules(layout.resourcePaths.map { it.moduleName }, "resourcePaths in $description", context)
  checkModules(layout.moduleExcludes.keys, "moduleExcludes in $description", context)

  checkProjectLibraries(names = layout.includedProjectLibraries.map { it.libraryName }, fieldName = "includedProjectLibraries in $description", context)

  for ((moduleName, libraryName) in layout.includedModuleLibraries) {
    checkModules(listOf(moduleName), "includedModuleLibraries in $description", context)
    check(context.findRequiredModule(moduleName).libraryCollection.libraries.any { getLibraryFileName(it) == libraryName }) {
      "Cannot find library '$libraryName' in '$moduleName' (used in $description)"
    }
  }

  if (layout is PluginLayout) {
    checkModules(modules = layout.excludedLibraries.keys, fieldName = "excludedModuleLibraries in $description", context)
    for ((key, value) in layout.excludedLibraries.entries) {
      val libraries = (if (key == null) context.project.libraryCollection else context.findRequiredModule(key).libraryCollection).libraries
      for (libraryName in value) {
        check(libraries.any { getLibraryFileName(it) == libraryName }) {
          val where = key?.let { "module '$it'" } ?: "project"
          "Cannot find library '$libraryName' in $where (used in 'excludedModuleLibraries' in $description)"
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
      "The following modules from $fieldName aren't found in the project: $unknownModules, ensure you use module name instead of plugin id"
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
    "The following artifacts from $fieldName aren't found in the project: $unknownArtifacts"
  }
}

private fun checkPluginModules(pluginModules: Collection<String>?, fieldName: String, context: BuildContext) {
  if (pluginModules == null) {
    return
  }

  checkModules(pluginModules, fieldName, context)

  val unknownBundledPluginModules = pluginModules.filter { name ->
    context.findModule(name)?.let { findFileInModuleSources(it, "META-INF/plugin.xml") } == null
  }
  check(unknownBundledPluginModules.isEmpty()) {
    "The following modules from $fieldName don't contain META-INF/plugin.xml file and aren't specified as optional plugin modules" +
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
    "Mandatory property '$fieldName' is not specified"
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
        ProductInfoLaunchData.create(
          OsFamily.WINDOWS.osName,
          arch.dirName,
          launcherPath = "bin/${executableName}.bat",
          javaExecutablePath = null,
          vmOptionsFilePath = "bin/win/${executableName}64.exe.vmoptions",
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.WINDOWS, arch, isPortableDist = true),
          mainClass = context.ideMainClassName
        ),
        ProductInfoLaunchData.create(
          OsFamily.LINUX.osName,
          arch.dirName,
          launcherPath = "bin/${executableName}.sh",
          javaExecutablePath = null,
          vmOptionsFilePath = "bin/linux/${executableName}64.vmoptions",
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.LINUX, arch, isPortableDist = true),
          mainClass = context.ideMainClassName,
          startupWmClass = getLinuxFrameClass(context)
        ),
        ProductInfoLaunchData.create(
          OsFamily.MACOS.osName,
          arch.dirName,
          launcherPath = "bin/${executableName}.sh",
          javaExecutablePath = null,
          vmOptionsFilePath = "bin/mac/${executableName}.vmoptions",
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.MACOS, arch, isPortableDist = true),
          mainClass = context.ideMainClassName
        )
      )
    }.toList(),
    context,
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
  val extraFiles = mapOf("dependencies.txt" to copyDependenciesFile(context))
  crossPlatformZip(context, distResults, targetFile, executableName, productJson, extraFiles, runtimeModuleRepositoryPath)

  validateProductJson(targetFile, pathInArchive = "", context)

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
      checkClassFiles(versionCheckerConfig, forbiddenSubPaths, forbiddenSubPathExceptions, root)
    }

    if (forbiddenSubPaths.isNotEmpty()) {
      span.addEvent("SUCCESS for forbiddenSubPaths at '$root': ${forbiddenSubPaths.joinToString()}")
    }
  }
}

private fun checkPlatformSpecificPluginResources(pluginLayouts: List<PluginLayout>, pluginModulesToPublish: Set<String>) {
  val offenders = pluginLayouts.filter { it.hasPlatformSpecificResources && it.mainModule in pluginModulesToPublish }
  check(offenders.isEmpty()) {
    "Non-bundled plugins are not allowed yet to specify platform-specific resources. Offenders:\n  ${offenders.joinToString("  \n")}"
  }
}

fun getOsDistributionBuilder(os: OsFamily, context: BuildContext, ideaProperties: CharSequence? = null): OsSpecificDistributionBuilder? = when (os) {
  OsFamily.WINDOWS -> context.windowsDistributionCustomizer?.let {
    WindowsDistributionBuilder(context, customizer = it, ideaProperties)
  }
  OsFamily.MACOS -> context.macDistributionCustomizer?.let {
    MacDistributionBuilder(context, customizer = it, ideaProperties)
  }
  OsFamily.LINUX -> context.linuxDistributionCustomizer?.let {
    LinuxDistributionBuilder(context, customizer = it, ideaProperties)
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
  context: BuildContext,
  distResults: List<DistributionForOsTaskResult>,
  targetFile: Path,
  executableName: String,
  productJson: String,
  extraFiles: Map<String, Path>,
  runtimeModuleRepositoryPath: Path?,
) {
  val winX64DistDir = distResults.first { it.builder.targetOs == OsFamily.WINDOWS && it.arch == JvmArchitecture.x64 }.outDir
  val macArm64DistDir = distResults.first { it.builder.targetOs == OsFamily.MACOS && it.arch == JvmArchitecture.aarch64 }.outDir
  val linuxX64DistDir = distResults.first { it.builder.targetOs == OsFamily.LINUX && it.arch == JvmArchitecture.x64 }.outDir

  val executablePatterns = distResults.flatMap {
    it.builder.generateExecutableFilesMatchers(includeRuntime = false, JvmArchitecture.x64).keys +
    it.builder.generateExecutableFilesMatchers(includeRuntime = false, JvmArchitecture.aarch64).keys
  }
  val entryCustomizer: (ZipArchiveEntry, Path, String) -> Unit = { entry, _, relativePathString ->
    val relativePath = Path.of(relativePathString)
    if (executablePatterns.any { it.matches(relativePath) }) {
      entry.unixMode = executableFileUnixMode
    }
  }

  writeNewFile(targetFile) { outFileChannel ->
    NoDuplicateZipArchiveOutputStream(outFileChannel, context.options.compressZipFiles).use { out ->
      out.setUseZip64(
        if (context.options.useZip64ForCrossPlatformDistribution) {
          Zip64Mode.AlwaysWithCompatibility
        }
        else {
          Zip64Mode.Never
        }
      )

      // for the `bin/` directory layout, see `PathManager.getBinDirectories(Path)`

      out.entryToDir(winX64DistDir.resolve("bin/${executableName}.bat"), "bin")
      out.entryToDir(linuxX64DistDir.resolve("bin/${executableName}.sh"), "bin", executableFileUnixMode)

      out.entryToDir(winX64DistDir.resolve("bin/idea.properties"), "bin/win")
      out.entryToDir(macArm64DistDir.resolve("bin/idea.properties"), "bin/mac")
      out.entryToDir(linuxX64DistDir.resolve("bin/idea.properties"), "bin/linux")

      out.entryToDir(winX64DistDir.resolve("bin/${executableName}64.exe.vmoptions"), "bin/win")
      out.entryToDir(macArm64DistDir.resolve("bin/${executableName}.vmoptions"), "bin/mac")
      out.entryToDir(linuxX64DistDir.resolve("bin/${executableName}64.vmoptions"), "bin/linux")

      val zipFileUniqueGuard = HashMap<String, DistFileContent>()

      val nonConflictingBinDirs = when (context.applicationInfo.productCode) {
        "CL" -> listOf("clang/", "cmake/", "gdb/", "lldb/", "mingw/", "ninja/", "profiler/")
        else -> emptyList()
      }
      val binEntryCustomizer = { entry: ZipArchiveEntry, path: Path, relative: String ->
        entryCustomizer.invoke(entry, path, "bin/${relative}")
      }
      distResults.forEach {
        val prefix = "bin/${it.builder.targetOs.dirName}/${it.arch.dirName}/"
        out.dir(it.outDir.resolve("bin"), prefix, fileFilter = { _, relPath ->
          relPath != "brokenPlugins.db" &&
          !(relPath.startsWith(executableName) && relPath.endsWith(".exe")) &&
          relPath != "${executableName}.bat" &&
          relPath != executableName &&
          relPath != "${executableName}.sh" &&
          relPath != "idea.properties" &&
          !relPath.endsWith(".vmoptions") &&
          !relPath.startsWith("repair") &&
          !relPath.startsWith("restart") &&
          !nonConflictingBinDirs.any(relPath::startsWith)
        }, binEntryCustomizer)

        out.dir(it.outDir.resolve("bin"), prefix = "bin/", fileFilter = { file, relPath ->
          nonConflictingBinDirs.any(relPath::startsWith)&&
          filterFileIfAlreadyInZip(relPath, file, zipFileUniqueGuard)
        }, binEntryCustomizer)
      }

      out.dir(context.paths.distAllDir, prefix = "", fileFilter = { _, relPath -> relPath != "bin/idea.properties" }, entryCustomizer)

      // not extracted into product properties because it (hopefully) will become obsolete soon
      val productFilter = when {
        context.applicationInfo.fullProductName.contains("Rider") -> { dist, _, relPath ->
          !relPath.startsWith("tools/") || dist.builder.targetOs == OsFamily.WINDOWS && dist.arch == JvmArchitecture.x64
        }
        else -> { _: DistributionForOsTaskResult, _: Path, _: String -> true }
      }

      distResults.forEach {
        out.dir(it.outDir, prefix = "", fileFilter = { file, relPath ->
          !relPath.startsWith("bin/") &&
          !relPath.startsWith("help/") &&
          relPath != MODULE_DESCRIPTORS_JAR_PATH &&
          relPath != PLUGIN_CLASSPATH &&
          !relPath.startsWith("bin/remote-dev-server") &&
          !relPath.startsWith("license/remote-dev-server") &&
          !relPath.startsWith("plugins/remote-dev-server") &&
          !relPath.startsWith("MacOS/") &&
          !relPath.startsWith("Resources/") &&
          !relPath.startsWith("Info.plist") &&
          !relPath.startsWith("Helpers/") &&
          !relPath.startsWith("lib/build-marker") &&
          productFilter(it, file, relPath) &&
          filterFileIfAlreadyInZip(relPath, file, zipFileUniqueGuard)
        }, entryCustomizer)
      }

      val distFiles = context.getDistFiles(os = null, arch = null)
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

      for ((p, f) in extraFiles) {
        out.entry(p, f)
      }

      out.entry(PRODUCT_INFO_FILE_NAME, productJson.encodeToByteArray())

      if (runtimeModuleRepositoryPath != null) {
        out.entry(MODULE_DESCRIPTORS_JAR_PATH, runtimeModuleRepositoryPath)
      }
    }
  }
}

private suspend fun lookForJunkFiles(context: BuildContext, paths: List<Path>) {
  val junk = CollectionFactory.createCaseInsensitiveStringSet(setOf("__MACOSX", ".DS_Store"))
  val result = Collections.synchronizedSet(mutableSetOf<Path>())

  withContext(Dispatchers.IO + CoroutineName("Looking for junk files")) {
    paths.forEach {
      launch {
        Files.walk(it).use { stream ->
          stream.forEach { path ->
            if (path.fileName.toString() in junk) {
              result.add(path)
            }
          }
        }
      }
    }
  }

  if (result.isNotEmpty()) {
    context.messages.error(result.joinToString("\n", prefix = "Junk files:\n"))
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
internal suspend fun collectIncludedPluginModules(enabledPluginModules: Collection<String>, result: MutableSet<String>, context: BuildContext) {
  result.addAll(enabledPluginModules)
  val pluginLayouts = getPluginLayoutsByJpsModuleNames(modules = enabledPluginModules, productLayout = context.productProperties.productLayout)
  val contentModuleFilter = context.getContentModuleFilter()
  for (plugin in pluginLayouts) {
    plugin.includedModules.mapTo(result) { it.moduleName }
    val mainModule = context.findRequiredModule(plugin.mainModule)
    result.addAll((context as BuildContextImpl).jarPackagerDependencyHelper.readPluginIncompleteContentFromDescriptor(mainModule, contentModuleFilter))
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
