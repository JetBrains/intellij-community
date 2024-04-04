// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.Formats
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.diagnostic.telemetry.helpers.useWithoutActiveScope
import com.intellij.util.io.Decompressor
import com.intellij.util.system.CpuArch
import com.jetbrains.plugin.structure.base.utils.toList
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.jdom.CDATA
import org.jdom.Document
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.idea.maven.aether.ProgressConsumer
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.moduleBased.findProductModulesFile
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.checkInArchive
import org.jetbrains.intellij.build.impl.productInfo.generateProductInfoJson
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.includedModules
import org.jetbrains.intellij.build.impl.projectStructureMapping.writeProjectStructureReport
import org.jetbrains.intellij.build.impl.sbom.SoftwareBillOfMaterialsImpl
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.logFreeDiskSpace
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.*
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import kotlin.io.NoSuchFileException
import kotlin.io.path.*

internal const val PROPERTIES_FILE_NAME: String = "idea.properties"

class BuildTasksImpl(private val context: BuildContextImpl) : BuildTasks {
  override suspend fun zipSourcesOfModules(modules: List<String>, targetFile: Path, includeLibraries: Boolean) {
    zipSourcesOfModules(modules = modules, targetFile = targetFile, includeLibraries = includeLibraries, context = context)
  }

  override suspend fun buildDistributions() {
    buildDistributions(context)
  }

  override suspend fun buildNonBundledPlugins(mainPluginModules: List<String>) {
    checkProductProperties(context)
    checkPluginModules(mainPluginModules, "mainPluginModules", context)
    copyDependenciesFile(context)
    val pluginsToPublish = getPluginLayoutsByJpsModuleNames(mainPluginModules, context.productProperties.productLayout)
    val distState = createDistributionBuilderState(pluginsToPublish = pluginsToPublish, context = context)
    val compilationTasks = CompilationTasks.create(context = context)
    distState.getModulesForPluginsToPublish() + listOf(
      "intellij.idea.community.build.tasks",
      "intellij.platform.images.build",
      "intellij.tools.launcherGenerator"
    ).let {
      compilationTasks.compileModules(moduleNames = it)
      localizeModules(context, moduleNames = it)
    }

    buildProjectArtifacts(
      platform = distState.platform,
      enabledPluginModules = getEnabledPluginModules(
        pluginsToPublish = distState.pluginsToPublish,
        context = context
      ),
      compilationTasks = compilationTasks,
      context = context,
    )
    buildSearchableOptions(context)
    buildNonBundledPlugins(
      pluginsToPublish = pluginsToPublish,
      compressPluginArchive = context.options.compressZipFiles,
      buildPlatformLibJob = null,
      state = distState,
      context = context
    )
  }

  override fun compileProjectAndTests(includingTestsInModules: List<String>) {
    compileModules(moduleNames = null, includingTestsInModules = includingTestsInModules)
  }

  override fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>) {
    CompilationTasks.create(context).compileModules(moduleNames, includingTestsInModules)
  }

  override fun compileModules(moduleNames: Collection<String>?) {
    CompilationTasks.create(context).compileModules(moduleNames)
  }

  override suspend fun buildUnpackedDistribution(targetDirectory: Path, includeBinAndRuntime: Boolean) {
    val currentOs = OsFamily.currentOs
    context.paths.distAllDir = targetDirectory
    context.options.targetOs = persistentListOf(currentOs)
    context.options.buildStepsToSkip += listOf(
      BuildOptions.GENERATE_JAR_ORDER_STEP,
      SoftwareBillOfMaterials.STEP_ID,
    )
    context.checkDistributionBuildNumber()
    BundledMavenDownloader.downloadMaven4Libs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMaven3Libs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMavenDistribution(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMavenTelemetryDependencies(context.paths.communityHomeDirRoot)
    buildDistribution(state = compileModulesForDistribution(context), context = context, isUpdateFromSources = true)
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

/**
 * Generates a JSON file containing mapping between files in the product distribution and modules and libraries in the project configuration
 */
suspend fun generateProjectStructureMapping(targetFile: Path, context: BuildContext) {
  val entries = generateProjectStructureMapping(
    context = context,
    platformLayout = createPlatformLayout(pluginsToPublish = emptySet(), context = context),
  )
  writeProjectStructureReport(
    entries = entries.first + entries.second,
    file = targetFile,
    buildPaths = context.paths
  )
}

private suspend fun localizeModules(context: BuildContext, moduleNames: Collection<String>) {
  if (context.isStepSkipped(BuildOptions.LOCALIZE_STEP)) {
    return
  }

  val localizationDir = getLocalizationDir(context) ?: return
  val modules = if (moduleNames.isEmpty()) context.project.modules else moduleNames.mapNotNull { context.findModule(it) }
  spanBuilder("bundle localizations").setAttribute("moduleCount", modules.size.toLong()).useWithScope {
    for (module in modules) {
      launch(Dispatchers.IO) {
        val resourceRoots = module.getSourceRoots(JavaResourceRootType.RESOURCE).iterator().toList()
        if (resourceRoots.isEmpty()) {
          return@launch
        }

        spanBuilder("bundle localization").setAttribute("module", module.name).use {
          buildInBundlePropertiesLocalization(
            module = module,
            context = context,
            bundlePropertiesLocalization = localizationDir.resolve("properties"),
            resourceRoots = resourceRoots,
          )
          buildInInspectionsIntentionsLocalization(
            module = module,
            context = context,
            inspectionsIntentionsLocalization = localizationDir.resolve("inspections_intentions"),
            resourceRoots = resourceRoots,
          )
        }
      }
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

private fun isSourceFile(path: String): Boolean =
  path.endsWith(".java") && path != "module-info.java" ||
  path.endsWith(".groovy") ||
  path.endsWith(".kt") ||
  path.endsWith(".form")

private fun getLocalArtifactRepositoryRoot(global: JpsGlobal): Path {
  JpsModelSerializationDataService.getPathVariablesConfiguration(global)!!.getUserVariableValue("MAVEN_REPOSITORY")?.let {
    return Path.of(it)
  }

  val root = System.getProperty("user.home", null)
  return if (root == null) Path.of(".m2/repository") else Path.of(root, ".m2/repository")
}

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
  spanBuilder("copy files shared among all distributions").useWithScope {
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
  spanBuilder("update executable permissions").setAttribute("dir", "$destinationDir").useWithScope(Dispatchers.IO) {
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

private fun downloadMissingLibrarySources(
  librariesWithMissingSources: List<JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>>,
  context: BuildContext,
) {
  spanBuilder("download missing sources")
    .setAttribute(AttributeKey.stringArrayKey("librariesWithMissingSources"), librariesWithMissingSources.map { it.name })
    .useWithoutActiveScope { span ->
      val configuration = JpsRemoteRepositoryService.getInstance().getRemoteRepositoriesConfiguration(context.project)
      val repositories = configuration?.repositories?.map { ArtifactRepositoryManager.createRemoteRepository(it.id, it.url) } ?: emptyList()
      val repositoryManager = ArtifactRepositoryManager(
        getLocalArtifactRepositoryRoot(context.projectModel.global).toFile(), repositories,
        ProgressConsumer.DEAF
      )
      for (library in librariesWithMissingSources) {
        val descriptor = library.properties.data
        span.addEvent(
          "downloading sources for library", Attributes.of(
          AttributeKey.stringKey("name"), library.name,
          AttributeKey.stringKey("mavenId"), descriptor.mavenId,
        )
        )
        val downloaded = repositoryManager.resolveDependencyAsArtifact(
          descriptor.groupId, descriptor.artifactId,
          descriptor.version, EnumSet.of(ArtifactKind.SOURCES),
          descriptor.isIncludeTransitiveDependencies,
          descriptor.excludedDependencies
        )
        span.addEvent(
          "downloaded sources for library", Attributes.of(
          AttributeKey.stringArrayKey("artifacts"), downloaded.map { it.toString() },
        )
        )
      }
    }
}

internal class DistributionForOsTaskResult(
  @JvmField val builder: OsSpecificDistributionBuilder,
  @JvmField val arch: JvmArchitecture,
  @JvmField val outDir: Path
)

private suspend fun buildOsSpecificDistributions(context: BuildContext): List<DistributionForOsTaskResult> {
  if (context.isStepSkipped(BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP)) {
    Span.current().addEvent("skip step", Attributes.of(AttributeKey.stringKey("name"), "build OS-specific distributions"))
    return emptyList()
  }

  setLastModifiedTime(context.paths.distAllDir, context)

  if (context.isMacCodeSignEnabled) {
    withContext(Dispatchers.IO) {
      for (file in Files.newDirectoryStream(context.paths.distAllDir).use { stream ->
        stream.filter { !it.endsWith("help") && !it.endsWith("license") && !it.endsWith("lib") }
      }) {
        launch {
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

  return supervisorScope {
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

      async {
        spanBuilder(stepId).useWithScope {
          val osAndArchSpecificDistDirectory = getOsAndArchSpecificDistDirectory(osFamily = os, arch = arch, context = context)
          builder.buildArtifacts(osAndArchSpecificDistPath = osAndArchSpecificDistDirectory, arch = arch)
          checkClassFiles(root = osAndArchSpecificDistDirectory, context = context, isDistAll = false)
          DistributionForOsTaskResult(builder = builder, arch = arch, outDir = osAndArchSpecificDistDirectory)
        }
      }
    }
  }.collectCompletedOrError()
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

private suspend fun buildSourcesArchive(entries: List<DistributionFileEntry>, context: BuildContext) {
  val productProperties = context.productProperties
  val archiveName = "${productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)}-sources.zip"
  val openSourceModules = entries.includedModules.filter { moduleName ->
    productProperties.includeIntoSourcesArchiveFilter.test(context.findRequiredModule(moduleName), context)
  }.toList()
  zipSourcesOfModules(
    modules = openSourceModules,
    targetFile = context.paths.artifactDir.resolve(archiveName),
    includeLibraries = true,
    context = context
  )
}

suspend fun zipSourcesOfModules(modules: List<String>, targetFile: Path, includeLibraries: Boolean, context: BuildContext) {
  context.executeStep(
    spanBuilder("build module sources archives")
      .setAttribute("path", context.paths.buildOutputDir.toString())
      .setAttribute(AttributeKey.stringArrayKey("modules"), modules),
    BuildOptions.SOURCES_ARCHIVE_STEP
  ) {
    withContext(Dispatchers.IO) {
      Files.createDirectories(targetFile.parent)
      Files.deleteIfExists(targetFile)
    }
    val includedLibraries = LinkedHashSet<JpsLibrary>()
    val span = Span.current()
    if (includeLibraries) {
      val debugMapping = mutableListOf<String>()
      for (moduleName in modules) {
        val module = context.findRequiredModule(moduleName)
        // We pack sources of libraries which are included in compilation classpath for platform API modules.
        // This way we'll get source files of all libraries useful for plugin developers, and the size of the archive will be reasonable.
        if (moduleName.startsWith("intellij.platform.") && context.findModule("$moduleName.impl") != null) {
          val libraries = JpsJavaExtensionService.dependencies(module).productionOnly().compileOnly().recursivelyExportedOnly().libraries
          includedLibraries.addAll(libraries)
          libraries.mapTo(debugMapping) { "${it.name} for $moduleName" }
        }
      }
      span.addEvent(
        "collect libraries to include into archive",
        Attributes.of(AttributeKey.stringArrayKey("mapping"), debugMapping)
      )
      val librariesWithMissingSources = includedLibraries
        .asSequence()
        .map { it.asTyped(JpsRepositoryLibraryType.INSTANCE) }
        .filterNotNull()
        .filter { library -> library.getPaths(JpsOrderRootType.SOURCES).any { Files.notExists(it) } }
        .toList()
      if (!librariesWithMissingSources.isEmpty()) {
        withContext(Dispatchers.IO) {
          downloadMissingLibrarySources(librariesWithMissingSources, context)
        }
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
    span.addEvent("include ${libraryRootUrls.size} roots from ${includedLibraries.size} libraries")
    for (url in libraryRootUrls) {
      if (url.startsWith(JpsPathUtil.JAR_URL_PREFIX) && url.endsWith(JpsPathUtil.JAR_SEPARATOR)) {
        val file = Path.of(JpsPathUtil.urlToPath(url))
        if (Files.isRegularFile(file)) {
          val size = Files.size(file)
          span.addEvent(
            file.toString(), Attributes.of(
            AttributeKey.stringKey("formattedSize"), Formats.formatFileSize(size),
            AttributeKey.longKey("bytes"), size
          )
          )
          val sourceFiles = filterSourceFilesOnly(file.name, context) { tempDir ->
            Decompressor.Zip(file).filter { isSourceFile(it) }.extract(tempDir)
          }
          zipFileMap.put(sourceFiles, "")
        }
        else {
          span.addEvent("skip root: file doesn't exist", Attributes.of(AttributeKey.stringKey("file"), file.toString()))
        }
      }
      else {
        span.addEvent("skip root: not a jar file", Attributes.of(AttributeKey.stringKey("url"), url))
      }
    }

    spanBuilder("pack")
      .setAttribute("targetFile", context.paths.buildOutputDir.relativize(targetFile).toString())
      .use {
        zipWithCompression(targetFile = targetFile, dirs = zipFileMap)
      }

    context.notifyArtifactBuilt(targetFile)
  }
}

@OptIn(ExperimentalPathApi::class)
private inline fun filterSourceFilesOnly(name: String, context: BuildContext, configure: (Path) -> Unit): Path {
  val sourceFiles = Files.createTempDirectory(context.paths.tempDir, name)
  configure(sourceFiles)
  sourceFiles.walk().forEach {
    if (!Files.isDirectory(it) && !isSourceFile(it.toString())) {
      Files.delete(it)
    }
  }
  return sourceFiles
}

@Internal
fun collectModulesToCompileForDistribution(context: BuildContext): MutableSet<String> {
  val productProperties = context.productProperties
  val productLayout = productProperties.productLayout

  val result = LinkedHashSet<String>()
  collectModulesToCompile(context = context, result = result)
  context.proprietaryBuildTools.scrambleTool?.let {
    result.addAll(it.additionalModulesToCompile)
  }
  result.addAll(productLayout.mainModules)

  val mavenArtifacts = productProperties.mavenArtifacts
  result.addAll(mavenArtifacts.additionalModules)
  result.addAll(mavenArtifacts.squashedModules)
  result.addAll(mavenArtifacts.proprietaryModules)

  result.addAll(productProperties.modulesToCompileTests)
  result.add("intellij.tools.launcherGenerator")
  return result
}

private suspend fun compileModulesForDistribution(context: BuildContext): DistributionBuilderState {
  val productLayout = context.productProperties.productLayout

  val compilationTasks = CompilationTasks.create(context)
  collectModulesToCompileForDistribution(context).let {
    compilationTasks.compileModules(moduleNames = it)
    localizeModules(context, moduleNames = it)
  }

  val pluginsToPublish = getPluginLayoutsByJpsModuleNames(modules = productLayout.pluginModulesToPublish, productLayout = productLayout)
  filterPluginsToPublish(pluginsToPublish, context)

  var enabledPluginModules = getEnabledPluginModules(pluginsToPublish = pluginsToPublish, context = context)
  // computed only based on a bundled and plugins to publish lists, compatible plugins are not taken in an account by intention
  val projectLibrariesUsedByPlugins = computeProjectLibsUsedByPlugins(enabledPluginModules = enabledPluginModules, context = context)
  val addPlatformCoverage = !productLayout.excludedModuleNames.contains("intellij.platform.coverage") &&
                            hasPlatformCoverage(
                              productLayout = productLayout,
                              enabledPluginModules = enabledPluginModules,
                              context = context,
                            )

  if (context.shouldBuildDistributions()) {
    if (context.options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
      Span.current().addEvent("skip collecting compatible plugins because PROVIDED_MODULES_LIST_STEP was skipped")
    }
    else {
      val providedModuleFile = context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-builtinModules.json")
      val platform = createPlatformLayout(pluginsToPublish, context)
      getModulesForPluginsToPublish(platform, pluginsToPublish).let {
        compilationTasks.compileModules(moduleNames = it)
        localizeModules(context, moduleNames = it)
      }

      val builtinModuleData = spanBuilder("build provided module list").useWithScope {
        Files.deleteIfExists(providedModuleFile)
        val tempDir = context.paths.tempDir.resolve("builtinModules")
        // start the product in headless mode using com.intellij.ide.plugins.BundledPluginsLister
        createDevIdeBuild(context = context).runProduct(
          tempDir = tempDir,
          listOf("listBundledPlugins", providedModuleFile.toString()),
        )

        context.productProperties.customizeBuiltinModules(context = context, builtinModulesFile = providedModuleFile)
        try {
          val builtinModuleData = readBuiltinModulesFile(file = providedModuleFile)
          context.builtinModule = builtinModuleData
          builtinModuleData
        }
        catch (_: NoSuchFileException) {
          throw IllegalStateException("Failed to build provided modules list: $providedModuleFile doesn\'t exist")
        }
      }

      context.notifyArtifactBuilt(artifactPath = providedModuleFile)
      if (!productLayout.buildAllCompatiblePlugins) {
        val distState = DistributionBuilderState(platform = platform, pluginsToPublish = pluginsToPublish, context = context)
        buildProjectArtifacts(
          platform = distState.platform,
          enabledPluginModules = enabledPluginModules,
          compilationTasks = compilationTasks,
          context = context
        )
        return distState
      }

      collectCompatiblePluginsToPublish(builtinModuleData = builtinModuleData, context = context, result = pluginsToPublish)
      filterPluginsToPublish(pluginsToPublish, context)

      // update enabledPluginModules to reflect changes in pluginsToPublish - used for buildProjectArtifacts
      enabledPluginModules = getEnabledPluginModules(pluginsToPublish = pluginsToPublish, context = context)
    }
  }

  val platform = createPlatformLayout(
    addPlatformCoverage = addPlatformCoverage,
    projectLibrariesUsedByPlugins = projectLibrariesUsedByPlugins,
    context = context,
  )
  val distState = DistributionBuilderState(platform = platform, pluginsToPublish = pluginsToPublish, context = context)
  distState.getModulesForPluginsToPublish().let {
    compilationTasks.compileModules(moduleNames = it)
    localizeModules(context, moduleNames = it)
  }
  buildProjectArtifacts(
    platform = distState.platform,
    enabledPluginModules = enabledPluginModules,
    compilationTasks = compilationTasks,
    context = context
  )
  return distState
}

private suspend fun buildProjectArtifacts(
  platform: PlatformLayout,
  enabledPluginModules: Set<String>,
  compilationTasks: CompilationTasks,
  context: BuildContext
) {
  val artifactNames = LinkedHashSet<String>()
  artifactNames.addAll(platform.includedArtifacts.keys)
  getPluginLayoutsByJpsModuleNames(modules = enabledPluginModules, productLayout = context.productProperties.productLayout)
    .flatMapTo(artifactNames) { it.includedArtifacts.keys }

  compilationTasks.buildProjectArtifacts(artifactNames)
}

suspend fun buildDistributions(context: BuildContext): Unit = spanBuilder("build distributions").useWithScope {
  context.checkDistributionBuildNumber()
  checkProductProperties(context as BuildContextImpl)
  copyDependenciesFile(context)
  logFreeDiskSpace("before compilation", context)
  val pluginsToPublish = getPluginLayoutsByJpsModuleNames(
    modules = context.productProperties.productLayout.pluginModulesToPublish,
    productLayout = context.productProperties.productLayout
  )
  val distributionState = compileModulesForDistribution(context)
  logFreeDiskSpace("after compilation", context)

  coroutineScope {
    createMavenArtifactJob(context, distributionState)

    val distEntries = spanBuilder("build platform and plugin JARs").useWithScope {
      if (context.shouldBuildDistributions()) {
        val entries = buildDistribution(state = distributionState, context)
        if (context.productProperties.buildSourcesArchive) {
          buildSourcesArchive(entries, context)
        }
        entries
      }
      else {
        Span.current().addEvent(
          "skip building product distributions because " +
          "'intellij.build.target.os' property is set to '${BuildOptions.OS_NONE}'"
        )
        buildSearchableOptions(context)
        buildNonBundledPlugins(
          pluginsToPublish = pluginsToPublish,
          compressPluginArchive = context.options.compressZipFiles,
          buildPlatformLibJob = null,
          state = distributionState,
          context = context
        )
        emptyList()
      }
    }

    if (!context.shouldBuildDistributions()) {
      return@coroutineScope
    }

    layoutShared(context)
    val distDirs = buildOsSpecificDistributions(context)
    launch(Dispatchers.IO) {
      context.executeStep(spanBuilder("generate software bill of materials"), SoftwareBillOfMaterials.STEP_ID) {
        SoftwareBillOfMaterialsImpl(context, distDirs, distEntries).generate()
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
    val moduleNames = HashSet<String>()
    if (mavenArtifacts.forIdeModules) {
      moduleNames.addAll(distributionState.platformModules)
      val productLayout = context.productProperties.productLayout
      collectIncludedPluginModules(enabledPluginModules = context.bundledPluginModules, product = productLayout, result = moduleNames)
    }

    val mavenArtifactsBuilder = MavenArtifactsBuilder(context)
    moduleNames.addAll(mavenArtifacts.additionalModules)
    if (!moduleNames.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(
        moduleNamesToPublish = moduleNames,
        moduleNamesToSquashAndPublish = mavenArtifacts.squashedModules,
        outputDir = "maven-artifacts"
      )
    }
    if (!mavenArtifacts.proprietaryModules.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(
        moduleNamesToPublish = mavenArtifacts.proprietaryModules,
        moduleNamesToSquashAndPublish = emptyList(),
        outputDir = "proprietary-maven-artifacts"
      )
    }
  }
}

private fun checkProductProperties(context: BuildContextImpl) {
  checkProductLayout(context)

  val properties = context.productProperties
  checkPaths2(properties.brandingResourcePaths, "productProperties.brandingResourcePaths")
  checkPaths2(properties.additionalIDEPropertiesFilePaths, "productProperties.additionalIDEPropertiesFilePaths")
  checkPaths2(properties.additionalDirectoriesWithLicenses, "productProperties.additionalDirectoriesWithLicenses")
  checkModules(properties.additionalModulesToCompile, "productProperties.additionalModulesToCompile", context)
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
    if (!context.isStepSkipped(BuildOptions.MAC_DMG_STEP)) {
      checkMandatoryPath(macCustomizer.dmgImagePath, "productProperties.macCustomizer.dmgImagePath")
      checkPaths(listOfNotNull(macCustomizer.dmgImagePathForEAP), "productProperties.macCustomizer.dmgImagePathForEAP")
    }
  }

  checkModules(properties.mavenArtifacts.additionalModules, "productProperties.mavenArtifacts.additionalModules", context)
  checkModules(properties.mavenArtifacts.squashedModules, "productProperties.mavenArtifacts.squashedModules", context)
  if (context.productProperties.scrambleMainJar) {
    context.proprietaryBuildTools.scrambleTool?.let {
      checkModules(
        modules = it.namesOfModulesRequiredToBeScrambled,
        fieldName = "ProprietaryBuildTools.scrambleTool.namesOfModulesRequiredToBeScrambled",
        context = context
      )
    }
  }
}

private fun checkProductLayout(context: BuildContext) {
  val layout = context.productProperties.productLayout
  // todo mainJarName type specified as not-null - does it work?
  val messages = context.messages

  val pluginLayouts = layout.pluginLayouts
  checkScrambleClasspathPlugins(pluginLayouts)
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
  checkModules(layout.mainModules, "productProperties.productLayout.mainModules", context)
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

  checkProjectLibraries(
    names = layout.includedProjectLibraries.map { it.libraryName },
    fieldName = "includedProjectLibraries in $description",
    context = context,
  )

  for ((moduleName, libraryName) in layout.includedModuleLibraries) {
    checkModules(listOf(moduleName), "includedModuleLibraries in $description", context)
    check(context.findRequiredModule(moduleName).libraryCollection.libraries.any { getLibraryFileName(it) == libraryName }) {
      "Cannot find library \'$libraryName\' in \'$moduleName\' (used in $description)"
    }
  }

  checkModules(
    modules = layout.excludedLibraries.keys,
    fieldName = "excludedModuleLibraries in $description",
    context = context,
  )
  for ((key, value) in layout.excludedLibraries.entries) {
    val libraries = (if (key == null) context.project.libraryCollection else context.findRequiredModule(key).libraryCollection).libraries
    for (libraryName in value) {
      check(libraries.any { getLibraryFileName(it) == libraryName }) {
        "Cannot find library \'$libraryName\' in \'$key\' (used in \'excludedModuleLibraries\' in $description)"
      }
    }
  }

  checkModules(layout.modulesWithExcludedModuleLibraries, "modulesWithExcludedModuleLibraries in $description", context)
}

private fun checkPluginDuplicates(nonTrivialPlugins: List<PluginLayout>) {
  val pluginsGroupedByMainModule = nonTrivialPlugins.groupBy { it.mainModule to it.bundlingRestrictions }.values
  for (duplicatedPlugins in pluginsGroupedByMainModule) {
    check(duplicatedPlugins.size <= 1) {
      "Duplicated plugin description in productLayout.pluginLayouts: main module ${duplicatedPlugins.first().mainModule}"
    }
  }

  // indexing-shared-ultimate has a separate layout for bundled & public plugins
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

private fun checkScrambleClasspathPlugins(pluginLayoutList: List<PluginLayout>) {
  val pluginDirectories = pluginLayoutList.mapTo(HashSet()) { it.directoryName }
  for (pluginLayout in pluginLayoutList) {
    for ((pluginDirectoryName, _) in pluginLayout.scrambleClasspathPlugins) {
      check(pluginDirectories.contains(pluginDirectoryName)) {
        "Layout of plugin '${pluginLayout.mainModule}' declares an unresolved plugin directory name" +
        " in ${pluginLayout.scrambleClasspathPlugins}: $pluginDirectoryName"
      }
    }
  }
}

private fun checkPluginModules(pluginModules: Collection<String>?, fieldName: String, context: BuildContext) {
  if (pluginModules == null) {
    return
  }

  checkModules(modules = pluginModules, fieldName = fieldName, context = context)

  val unknownBundledPluginModules = pluginModules.filter { context.findFileInModuleSources(it, "META-INF/plugin.xml") == null }
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

private fun buildCrossPlatformZip(distResults: List<DistributionForOsTaskResult>, context: BuildContext): Path {
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
  if (!isDistAll || context.isStepSkipped(BuildOptions.VERIFY_CLASS_FILE_VERSIONS)) {
    return
  }

  val versionCheckerConfig = context.productProperties.versionCheckerConfig
  val forbiddenSubPaths = context.productProperties.forbiddenClassFileSubPaths
  val forbiddenSubPathExceptions = context.productProperties.forbiddenClassFileSubPathExceptions
  if (forbiddenSubPaths.isNotEmpty()) {
    val forbiddenString = forbiddenSubPaths.let { "(${it.size}): ${it.joinToString()}" }
    val exceptionsString = forbiddenSubPathExceptions.let { "(${it.size}): ${it.joinToString()}" }
    Span.current().addEvent("checkClassFiles: forbiddenSubPaths $forbiddenString, exceptions $exceptionsString")
  }
  else {
    Span.current().addEvent("checkClassFiles: forbiddenSubPaths: EMPTY (no scrambling checks will be done)")
  }

  if (versionCheckerConfig.isNotEmpty() || forbiddenSubPaths.isNotEmpty()) {
    checkClassFiles(
      versionCheckConfig = versionCheckerConfig,
      forbiddenSubPaths = forbiddenSubPaths,
      forbiddenSubPathExceptions = forbiddenSubPathExceptions,
      root = root
    )
  }

  if (forbiddenSubPaths.isNotEmpty()) {
    Span.current().addEvent("checkClassFiles: SUCCESS for forbiddenSubPaths at '$root': ${forbiddenSubPaths.joinToString()}")
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

      out.dir(distAllDir, "", fileFilter = { _, relPath -> relPath != "bin/idea.properties" }, entryCustomizer = entryCustomizer)
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

fun collectModulesToCompile(context: BuildContext, result: MutableSet<String>) {
  val productLayout = context.productProperties.productLayout
  collectIncludedPluginModules(enabledPluginModules = context.bundledPluginModules, product = productLayout, result = result)
  collectPlatformModules(result)
  result.addAll(productLayout.productApiModules)
  result.addAll(productLayout.productImplementationModules)
  result.addAll(getToolModules())
  if (context.isEmbeddedJetBrainsClientEnabled) {
    result.add(context.productProperties.embeddedJetBrainsClientMainModule!!)
  }
  result.addAll(context.productProperties.additionalModulesToCompile)
  result.add("intellij.idea.community.build.tasks")
  result.add("intellij.platform.images.build")
  result.removeAll(productLayout.excludedModuleNames)
}

// Captures information about all available inspections in a JSON format as part of an Inspectopedia project.
// This is later used by Qodana and other tools.
// Keymaps are extracted as an XML file and also used in authoring help.
internal suspend fun buildAdditionalAuthoringArtifacts(ide: DevIdeBuild, context: BuildContext) {
  context.executeStep(spanBuilder("build authoring asserts"), BuildOptions.DOC_AUTHORING_ASSETS_STEP) {
    val commands = listOf(
      Pair("inspectopedia-generator", "inspections-${context.applicationInfo.productCode.lowercase()}"),
      Pair("keymap", "keymap-${context.applicationInfo.productCode.lowercase()}")
    )
    val temporaryBuildDirectory = context.paths.tempDir
    for (command in commands) {
      launch {
        val temporaryStepDirectory = temporaryBuildDirectory.resolve(command.first)
        val targetPath = temporaryStepDirectory.resolve(command.second)
        ide.runProduct(temporaryStepDirectory, arguments = listOf(command.first, targetPath.toString()), isLongRunning = true)

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
  spanBuilder("update last modified time").setAttribute("dir", directory.toString()).useWithScope(Dispatchers.IO) {
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
internal fun collectIncludedPluginModules(
  enabledPluginModules: Collection<String>,
  product: ProductModulesLayout,
  result: MutableSet<String>
) {
  result.addAll(enabledPluginModules)
  val enabledPluginModuleSet = if (enabledPluginModules is Set<String> || enabledPluginModules.size < 2) {
    enabledPluginModules
  }
  else {
    enabledPluginModules.toHashSet()
  }
  product.pluginLayouts.asSequence()
    .filter { enabledPluginModuleSet.contains(it.mainModule) }
    .flatMapTo(result) { layout -> layout.includedModules.asSequence().map { it.moduleName } }
}

fun copyDistFiles(context: BuildContext, newDir: Path, os: OsFamily, arch: JvmArchitecture) {
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

internal fun generateLanguagePluginsXml(context: BuildContext, targetPath: Path) {
  val root = Element("plugins")
  root.addContent(createPluginNode(context, "com.intellij.ja", "ja", "7 MB"))
  root.addContent(createPluginNode(context, "com.intellij.ko", "ko", "7 MB"))
  root.addContent(createPluginNode(context, "com.intellij.zh", "zh-CN", "6 MB"))

  val document = Document()
  document.rootElement = root
  JDOMUtil.writeDocument(document, targetPath.resolve("plugins/language-plugins.xml"))
}

private fun createPluginNode(context: BuildContext, id: String, language: String, size: String): Element {
  val element = Element("plugin")
  element.setAttribute("id", id)
  element.setAttribute("language", language)
  element.setAttribute("size", size)
  element.addContent(CDATA("https://plugins.jetbrains.com/pluginManager?id=$id&build=${context.fullBuildNumber}"))

  return element
}

internal fun copyInspectScript(context: BuildContext, distBinDir: Path) {
  val inspectScript = context.productProperties.inspectCommandName
  if (inspectScript != "inspect") {
    val targetPath = distBinDir.resolve("$inspectScript.sh")
    Files.move(distBinDir.resolve("inspect.sh"), targetPath, StandardCopyOption.REPLACE_EXISTING)
    context.patchInspectScript(targetPath)
  }
}

private fun getLocalizationDir(context: BuildContext): Path? {
  val localizationDir = context.paths.communityHomeDir.parent.resolve("localization")
  if (!Files.exists(localizationDir)) {
    Span.current().addEvent("unable to find 'localization' directory, skip localization bundling")
    return null
  }
  return localizationDir
}

@OptIn(ExperimentalPathApi::class)
private fun buildInInspectionsIntentionsLocalization(
  module: JpsModule,
  context: BuildContext,
  inspectionsIntentionsLocalization: Path,
  resourceRoots: List<JpsTypedModuleSourceRoot<JavaResourceRootProperties>>,
) {
  for (resourceRoot in resourceRoots) {
    val isInspectionIntentionsPresentInModule = sequenceOf("fileTemplates", "intentionDescriptions", "inspectionDescriptions")
      .map { resourceRoot.path.resolve(it) }
      .any { Files.exists(it) }
    if (!isInspectionIntentionsPresentInModule) {
      return
    }

    inspectionsIntentionsLocalization.walk()
      .filter { Files.isDirectory(it) && it.name == module.name }
      .forEach { moduleLocalizationSources ->
        val sourcesLang = inspectionsIntentionsLocalization.relativize(moduleLocalizationSources).getName(0)
        val moduleTargetLangLocalizationDir = resourceRoot.path.relativize(resourceRoot.path.resolve("localization").resolve(sourcesLang))

        moduleLocalizationSources.walk().filter { Files.isRegularFile(it) }.forEach { localizationFileSourceByLangAndModule ->
          // e.g.
          // localization/inspections_intentions/ja/fleet.plugins.kotlin.backend/inspectionDescriptions/NewEntityRequiredProperties.html
          // ->
          // out/classes/production/fleet.plugins.kotlin.backend/localization/ja/inspectionDescriptions/NewEntityRequiredProperties.html

          val localizationFileTargetRelativePath = moduleTargetLangLocalizationDir.resolve(
            moduleLocalizationSources.relativize(localizationFileSourceByLangAndModule)
          )
          val localizationFileTargetAbsolutePath = context.getModuleOutputDir(module).resolve(localizationFileTargetRelativePath)

          Files.createDirectories(localizationFileTargetAbsolutePath.parent)
          Files.copy(localizationFileSourceByLangAndModule, localizationFileTargetAbsolutePath, StandardCopyOption.REPLACE_EXISTING)
        }
      }
  }
}

private fun buildInBundlePropertiesLocalization(
  module: JpsModule,
  context: BuildContext,
  bundlePropertiesLocalization: Path,
  resourceRoots: List<JpsTypedModuleSourceRoot<JavaResourceRootProperties>>,
) {
  val knownBundlePropertiesList = HashMap<Path, Path>()
  for (resourceRoot in resourceRoots) {
    if (!Files.isDirectory(resourceRoot.path)) {
      continue
    }

    Files.walkFileTree(resourceRoot.path, object : SimpleFileVisitor<Path>() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        val fileName = file.fileName
        if (fileName.toString().endsWith("Bundle.properties")) {
          knownBundlePropertiesList.put(fileName, resourceRoot.path.relativize(file))
        }
        return FileVisitResult.CONTINUE
      }
    })
  }

  if (knownBundlePropertiesList.isEmpty()) {
    return
  }

  Files.walkFileTree(bundlePropertiesLocalization, object : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val origRelativePath = knownBundlePropertiesList.get(file.fileName) ?: return FileVisitResult.CONTINUE

      // e.g.
      // localization/properties/ja/PersonBundle.properties
      // ->
      // out/classes/production/intellij.ae.personalization.main/messages/PersonBundle_ja.properties

      val localizedRelative = bundlePropertiesLocalization.relativize(file)
      val lang = localizedRelative.getName(0)

      val targetNameWithLangSuffix = origRelativePath.nameWithoutExtension + "_$lang." + origRelativePath.extension
      val localizedBundleDstPath = context.getModuleOutputDir(module).resolve(origRelativePath.parent.resolve(targetNameWithLangSuffix))
      Files.createDirectories(localizedBundleDstPath.parent)
      Files.copy(file, localizedBundleDstPath, StandardCopyOption.REPLACE_EXISTING)
      return FileVisitResult.CONTINUE
    }
  })
}
