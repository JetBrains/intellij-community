// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("BlockingMethodInNonBlockingContext")

package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.use
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.diagnostic.telemetry.useWithScope2
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.Formats
import com.intellij.util.io.Decompressor
import com.intellij.util.system.CpuArch
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.idea.maven.aether.ProgressConsumer
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.checkInArchive
import org.jetbrains.intellij.build.impl.productInfo.generateMultiPlatformProductJson
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.includedModules
import org.jetbrains.intellij.build.impl.projectStructureMapping.writeProjectStructureReport
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.tasks.*
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
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import java.util.stream.Collectors
import kotlin.io.path.setLastModifiedTime

class BuildTasksImpl(context: BuildContext) : BuildTasks {
  private val context = context as BuildContextImpl

  override suspend fun zipSourcesOfModules(modules: List<String>, targetFile: Path, includeLibraries: Boolean) {
    zipSourcesOfModules(modules = modules, targetFile = targetFile, includeLibraries = includeLibraries, context = context)
  }

  override suspend fun compileModulesFromProduct() {
    checkProductProperties(context)
    compileModulesForDistribution(context)
  }

  override suspend fun buildDistributions() {
    buildDistributions(context)
  }

  override suspend fun buildDmg(macZipDir: Path) {
    supervisorScope {
      sequenceOf(JvmArchitecture.x64, JvmArchitecture.aarch64)
        .map { arch ->
          val macZip = find(directory = macZipDir, suffix = "${arch}.zip", context = context)
          val builtModule = readBuiltinModulesFile(find(directory = macZipDir, suffix = "builtinModules.json", context = context))
          async {
            MacDistributionBuilder(context = context,
                                   customizer = context.macDistributionCustomizer!!,
                                   ideaProperties = null).buildAndSignDmgFromZip(macZip = macZip,
                                                                                 macZipWithoutRuntime = null,
                                                                                 arch = arch,
                                                                                 builtinModule = builtModule)
          }
        }
        .toList()
    }.collectCompletedOrError()
  }

  override suspend fun buildNonBundledPlugins(mainPluginModules: List<String>) {
    checkProductProperties(context)
    checkPluginModules(mainPluginModules, "mainPluginModules", context)
    copyDependenciesFile(context)
    val pluginsToPublish = getPluginLayoutsByJpsModuleNames(mainPluginModules, context.productProperties.productLayout)
    val distributionJARsBuilder = DistributionJARsBuilder(compilePlatformAndPluginModules(pluginsToPublish, context))
    distributionJARsBuilder.buildSearchableOptions(context)
    distributionJARsBuilder.buildNonBundledPlugins(pluginsToPublish = pluginsToPublish,
                                                   compressPluginArchive = context.options.compressZipFiles,
                                                   buildPlatformLibJob = null,
                                                   context = context)
  }

  override fun compileProjectAndTests(includingTestsInModules: List<String>) {
    compileModules(null, includingTestsInModules)
  }

  override fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>) {
    CompilationTasks.create(context).compileModules(moduleNames, includingTestsInModules)
  }

  override fun compileModules(moduleNames: Collection<String>?) {
    CompilationTasks.create(context).compileModules(moduleNames)
  }

  override fun buildFullUpdaterJar() {
    buildUpdaterJar(context, "updater-full.jar")
  }

  override suspend fun buildUnpackedDistribution(targetDirectory: Path, includeBinAndRuntime: Boolean) {
    val currentOs = OsFamily.currentOs
    context.paths.distAllDir = targetDirectory
    context.options.targetOs = persistentListOf(currentOs)
    context.options.buildStepsToSkip.add(BuildOptions.GENERATE_JAR_ORDER_STEP)
    BundledMavenDownloader.downloadMavenCommonLibs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMavenDistribution(context.paths.communityHomeDirRoot)
    DistributionJARsBuilder(compileModulesForDistribution(context)).buildJARs(context = context, isUpdateFromSources = true)
    val arch = if (SystemInfo.isMac && CpuArch.isIntel64() && CpuArch.isEmulated()) {
      JvmArchitecture.aarch64
    }
    else {
      JvmArchitecture.currentJvmArch
    }
    layoutShared(context)
    if (includeBinAndRuntime) {
      val propertiesFile = patchIdeaPropertiesFile(context)
      val builder = getOsDistributionBuilder(os = currentOs, ideaProperties = propertiesFile, context = context)!!
      builder.copyFilesForOsDistribution(targetDirectory, arch)
      context.bundledRuntime.extractTo(prefix = BundledRuntimeImpl.getProductPrefix(context),
                                       os = currentOs,
                                       destinationDir = targetDirectory.resolve("jbr"),
                                       arch = arch)
      updateExecutablePermissions(targetDirectory, builder.generateExecutableFilesPatterns(true))
      builder.checkExecutablePermissions(targetDirectory, root = "")
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
  val pluginLayoutRoot = withContext(Dispatchers.IO) {
    Files.createDirectories(context.paths.tempDir)
    Files.createTempDirectory(context.paths.tempDir, "pluginLayoutRoot")
  }
  writeProjectStructureReport(
    entries = generateProjectStructureMapping(context = context,
                                              state = DistributionBuilderState(pluginsToPublish = emptySet(), context = context),
                                              pluginLayoutRoot = pluginLayoutRoot),
    file = targetFile,
    buildPaths = context.paths
  )
}

data class SupportedDistribution(@JvmField val os: OsFamily, @JvmField val arch: JvmArchitecture)

@JvmField
val SUPPORTED_DISTRIBUTIONS: PersistentList<SupportedDistribution> = persistentListOf(
  SupportedDistribution(os = OsFamily.MACOS, arch = JvmArchitecture.x64),
  SupportedDistribution(os = OsFamily.MACOS, arch = JvmArchitecture.aarch64),
  SupportedDistribution(os = OsFamily.WINDOWS, arch = JvmArchitecture.x64),
  SupportedDistribution(os = OsFamily.WINDOWS, arch = JvmArchitecture.aarch64),
  SupportedDistribution(os = OsFamily.LINUX, arch = JvmArchitecture.x64),
  SupportedDistribution(os = OsFamily.LINUX, arch = JvmArchitecture.aarch64),
)

private fun isSourceFile(path: String): Boolean {
  return path.endsWith(".java") || path.endsWith(".groovy") || path.endsWith(".kt")
}

private fun getLocalArtifactRepositoryRoot(global: JpsGlobal): Path {
  JpsModelSerializationDataService.getPathVariablesConfiguration(global)!!.getUserVariableValue("MAVEN_REPOSITORY")?.let {
    return Path.of(it)
  }

  val root = System.getProperty("user.home", null)
  return if (root == null) Path.of(".m2/repository") else Path.of(root, ".m2/repository")
}

/**
 * Building a list of modules that the IDE will provide for plugins.
 */
private suspend fun buildProvidedModuleList(targetFile: Path, state: DistributionBuilderState, context: BuildContext) {
  context.executeStep(spanBuilder("build provided module list"), BuildOptions.PROVIDED_MODULES_LIST_STEP) {
    withContext(Dispatchers.IO) {
      Files.deleteIfExists(targetFile)
      val ideClasspath = DistributionJARsBuilder(state).createIdeClassPath(context)
      // start the product in headless mode using com.intellij.ide.plugins.BundledPluginsLister
      runApplicationStarter(context = context,
                            tempDir = context.paths.tempDir.resolve("builtinModules"),
                            ideClasspath = ideClasspath,
                            arguments = listOf("listBundledPlugins", targetFile.toString()))
      check(Files.exists(targetFile)) {
        "Failed to build provided modules list: $targetFile doesn\'t exist"
      }
    }
    context.productProperties.customizeBuiltinModules(context, targetFile)
    context.builtinModule = readBuiltinModulesFile(targetFile)
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
  map["settings_dir"] = settingsDir
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

private suspend fun layoutShared(context: BuildContext) {
  spanBuilder("copy files shared among all distributions").useWithScope2 {
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
      context.productProperties.copyAdditionalFiles(context, context.paths.getDistAll())
    }
  }
  checkClassFiles(context.paths.distAllDir, context)
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

internal fun updateExecutablePermissions(destinationDir: Path, executableFilesPatterns: List<String>) {
  val executable = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                              PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
                              PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
                              PosixFilePermission.OTHERS_EXECUTE)
  val regular = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                           PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)
  val executableFilesMatchers = executableFilesPatterns.map { FileSystems.getDefault().getPathMatcher("glob:$it") }
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
    .use { span ->
      val configuration = JpsRemoteRepositoryService.getInstance().getRemoteRepositoriesConfiguration(context.project)
      val repositories = configuration?.repositories?.map { ArtifactRepositoryManager.createRemoteRepository(it.id, it.url) } ?: emptyList()
      val repositoryManager = ArtifactRepositoryManager(getLocalArtifactRepositoryRoot(context.projectModel.global).toFile(), repositories,
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

private class DistributionForOsTaskResult(@JvmField val builder: OsSpecificDistributionBuilder,
                                          @JvmField val arch: JvmArchitecture,
                                          @JvmField val outDir: Path)

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

private suspend fun buildOsSpecificDistributions(context: BuildContext): List<DistributionForOsTaskResult> {
  val stepMessage = "build OS-specific distributions"
  if (context.options.buildStepsToSkip.contains(BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP)) {
    Span.current().addEvent("skip step", Attributes.of(AttributeKey.stringKey("name"), stepMessage))
    return emptyList()
  }

  val propertiesFile = patchIdeaPropertiesFile(context)

  return supervisorScope {
    withContext(Dispatchers.IO) {
      Files.walk(context.paths.distAllDir).use { tree ->
        val fileTime = FileTime.from(context.options.buildDateInSeconds, TimeUnit.SECONDS)
        tree.forEach {
          it.setLastModifiedTime(fileTime)
        }
      }
    }
    SUPPORTED_DISTRIBUTIONS.mapNotNull { (os, arch) ->
      if (!context.shouldBuildDistributionForOS(os, arch)) {
        return@mapNotNull null
      }

      val builder = getOsDistributionBuilder(os = os, ideaProperties = propertiesFile, context = context) ?: return@mapNotNull null

      val stepId = "${os.osId} ${arch.name}"
      if (context.options.buildStepsToSkip.contains(stepId)) {
        Span.current().addEvent("skip step", Attributes.of(AttributeKey.stringKey("id"), stepId))
        return@mapNotNull null
      }

      async {
        spanBuilder(stepId).useWithScope2 {
          val osAndArchSpecificDistDirectory = getOsAndArchSpecificDistDirectory(os, arch, context)
          builder.buildArtifacts(osAndArchSpecificDistDirectory, arch)
          DistributionForOsTaskResult(builder, arch, osAndArchSpecificDistDirectory)
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
  context.notifyArtifactWasBuilt(outputFile)
  return outputFile
}

private fun checkProjectLibraries(names: Collection<String>, fieldName: String, context: BuildContext) {
  val unknownLibraries = names.filter { context.project.libraryCollection.findLibrary(it) == null }
  if (!unknownLibraries.isEmpty()) {
    context.messages.error("The following libraries from $fieldName aren\'t found in the project: $unknownLibraries")
  }
}

private suspend fun buildSourcesArchive(entries: List<DistributionFileEntry>, context: BuildContext) {
  val productProperties = context.productProperties
  val archiveName = "${productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)}-sources.zip"
  val modulesFromCommunity = entries.includedModules.filter { moduleName ->
    productProperties.includeIntoSourcesArchiveFilter.test(context.findRequiredModule(moduleName), context)
  }.toList()
  zipSourcesOfModules(modules = modulesFromCommunity,
                      targetFile = context.paths.artifactDir.resolve(archiveName),
                      includeLibraries = true,
                      context = context)
}

suspend fun zipSourcesOfModules(modules: List<String>, targetFile: Path, includeLibraries: Boolean, context: BuildContext) {
  context.executeStep(spanBuilder("build module sources archives")
                        .setAttribute("path", context.paths.buildOutputDir.toString())
                        .setAttribute(AttributeKey.stringArrayKey("modules"), modules),
                      BuildOptions.SOURCES_ARCHIVE_STEP) {
    withContext(Dispatchers.IO) {
      Files.createDirectories(targetFile.parent)
      Files.deleteIfExists(targetFile)
    }
    val includedLibraries = LinkedHashSet<JpsLibrary>()
    if (includeLibraries) {
      val debugMapping = mutableListOf<String>()
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
          zipFileMap[sourceFiles] = root.properties.packagePrefix.replace(".", "/")
        }
      }
      for (root in module.getSourceRoots(JavaResourceRootType.RESOURCE)) {
        if (root.file.absoluteFile.exists()) {
          val sourceFiles = filterSourceFilesOnly(root.file.name, context) { FileUtil.copyDirContent(root.file.absoluteFile, it.toFile()) }
          zipFileMap[sourceFiles] = root.properties.relativeOutputPath
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
          zipFileMap[sourceFiles] = ""
        }
        else {
          context.messages.debug("  skipped root $file: file doesn\'t exist")
        }
      }
      else {
        context.messages.debug("  skipped root $url: not a jar file")
      }
    }

    spanBuilder("pack")
      .setAttribute("targetFile", context.paths.buildOutputDir.relativize(targetFile).toString())
      .useWithScope {
        zipWithCompression(targetFile, zipFileMap)
      }

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

private fun compilePlatformAndPluginModules(pluginsToPublish: Set<PluginLayout>, context: BuildContext): DistributionBuilderState {
  val distState = DistributionBuilderState(pluginsToPublish, context)
  val compilationTasks = CompilationTasks.create(context)
  compilationTasks.compileModules(
    distState.getModulesForPluginsToPublish() +
    listOf("intellij.idea.community.build.tasks", "intellij.platform.images.build", "intellij.tools.launcherGenerator"))
  compilationTasks.buildProjectArtifacts(distState.getIncludedProjectArtifacts())
  return distState
}

private suspend fun compileModulesForDistribution(pluginsToPublish: Set<PluginLayout>, context: BuildContext): DistributionBuilderState {
  val productProperties = context.productProperties
  val mavenArtifacts = productProperties.mavenArtifacts

  val toCompile = LinkedHashSet<String>()
  toCompile.addAll(getModulesToCompile(context))
  context.proprietaryBuildTools.scrambleTool?.let {
    toCompile.addAll(it.additionalModulesToCompile)
  }
  toCompile.addAll(productProperties.productLayout.mainModules)
  toCompile.addAll(mavenArtifacts.additionalModules)
  toCompile.addAll(mavenArtifacts.squashedModules)
  toCompile.addAll(mavenArtifacts.proprietaryModules)
  toCompile.addAll(productProperties.modulesToCompileTests)
  CompilationTasks.create(context).compileModules(toCompile)

  if (context.shouldBuildDistributions()) {
    val providedModuleFile = context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-builtinModules.json")
    val state = compilePlatformAndPluginModules(pluginsToPublish, context)
    buildProvidedModuleList(targetFile = providedModuleFile, state = state, context = context)
    if (!productProperties.productLayout.buildAllCompatiblePlugins) {
      return state
    }

    if (context.options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
      Span.current().addEvent("skip collecting compatible plugins because PROVIDED_MODULES_LIST_STEP was skipped")
    }
    else {
      return compilePlatformAndPluginModules(
        pluginsToPublish = pluginsToPublish + collectCompatiblePluginsToPublish(providedModuleFile, context),
        context = context
      )
    }
  }
  return compilePlatformAndPluginModules(pluginsToPublish, context)
}

private suspend fun compileModulesForDistribution(context: BuildContext): DistributionBuilderState {
  return compileModulesForDistribution(
    pluginsToPublish = getPluginLayoutsByJpsModuleNames(modules = context.productProperties.productLayout.pluginModulesToPublish,
                                                        productLayout = context.productProperties.productLayout),
    context = context
  )
}

suspend fun buildDistributions(context: BuildContext): Unit = spanBuilder("build distributions").useWithScope2 {
  checkProductProperties(context as BuildContextImpl)
  copyDependenciesFile(context)
  logFreeDiskSpace("before compilation", context)
  val pluginsToPublish = getPluginLayoutsByJpsModuleNames(modules = context.productProperties.productLayout.pluginModulesToPublish,
                                                          productLayout = context.productProperties.productLayout)
  val distributionState = compileModulesForDistribution(context)
  logFreeDiskSpace("after compilation", context)

  coroutineScope {
    createMavenArtifactJob(context, distributionState)

    spanBuilder("build platform and plugin JARs").useWithScope2<Unit> {
      val distributionJARsBuilder = DistributionJARsBuilder(distributionState)

      if (context.productProperties.buildDocAuthoringAssets) {
        buildInspectopediaArtifacts(distributionJARsBuilder, context)
      }
      if (context.shouldBuildDistributions()) {
        val entries = distributionJARsBuilder.buildJARs(context)
        if (context.productProperties.buildSourcesArchive) {
          buildSourcesArchive(entries, context)
        }
      }
      else {
        Span.current().addEvent("skip building product distributions because " +
                                "\"intellij.build.target.os\" property is set to \"${BuildOptions.OS_NONE}\"")
        distributionJARsBuilder.buildSearchableOptions(context)
        distributionJARsBuilder.buildNonBundledPlugins(pluginsToPublish = pluginsToPublish,
                                                       compressPluginArchive = context.options.compressZipFiles,
                                                       buildPlatformLibJob = null,
                                                       context = context)
      }
    }

    if (!context.shouldBuildDistributions()) {
      return@coroutineScope
    }

    layoutShared(context)
    val distDirs = buildOsSpecificDistributions(context)
    @Suppress("SpellCheckingInspection")
    if (java.lang.Boolean.getBoolean("intellij.build.toolbox.litegen")) {
      @Suppress("SENSELESS_COMPARISON")
      if (context.buildNumber == null) {
        Span.current().addEvent("Toolbox LiteGen is not executed - it does not support SNAPSHOT build numbers")
      }
      else if (context.options.targetOs != OsFamily.ALL) {
        Span.current().addEvent("Toolbox LiteGen is not executed - it doesn't support installers are being built only for specific OS")
      }
      else {
        context.executeStep("build toolbox lite-gen links", BuildOptions.TOOLBOX_LITE_GEN_STEP) {
          val toolboxLiteGenVersion = System.getProperty("intellij.build.toolbox.litegen.version")
          checkNotNull(toolboxLiteGenVersion) {
            "Toolbox Lite-Gen version is not specified!"
          }

          ToolboxLiteGen.runToolboxLiteGen(context.paths.communityHomeDirRoot, context.messages,
                                           toolboxLiteGenVersion, "/artifacts-dir=" + context.paths.artifacts,
                                           "/product-code=" + context.applicationInfo.productCode,
                                           "/isEAP=" + context.applicationInfo.isEAP.toString(),
                                           "/output-dir=" + context.paths.buildOutputRoot + "/toolbox-lite-gen")
        }
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
    val moduleNames = ArrayList<String>()
    if (mavenArtifacts.forIdeModules) {
      moduleNames.addAll(distributionState.platformModules)
      val productLayout = context.productProperties.productLayout
      moduleNames.addAll(productLayout.getIncludedPluginModules(productLayout.bundledPluginModules))
    }

    val mavenArtifactsBuilder = MavenArtifactsBuilder(context)
    moduleNames.addAll(mavenArtifacts.additionalModules)
    if (!moduleNames.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(moduleNames, mavenArtifacts.squashedModules, "maven-artifacts")
    }
    if (!mavenArtifacts.proprietaryModules.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(mavenArtifacts.proprietaryModules, emptyList(), "proprietary-maven-artifacts")
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
    checkMandatoryPath(macCustomizer.dmgImagePath, "productProperties.macCustomizer.dmgImagePath")
    checkPaths(listOfNotNull(macCustomizer.dmgImagePathForEAP), "productProperties.macCustomizer.dmgImagePathForEAP")
  }

  checkModules(properties.mavenArtifacts.additionalModules, "productProperties.mavenArtifacts.additionalModules", context)
  checkModules(properties.mavenArtifacts.squashedModules, "productProperties.mavenArtifacts.squashedModules", context)
  if (context.productProperties.scrambleMainJar) {
    context.proprietaryBuildTools.scrambleTool?.let {
      checkModules(modules = it.namesOfModulesRequiredToBeScrambled,
                   fieldName = "ProprietaryBuildTools.scrambleTool.namesOfModulesRequiredToBeScrambled",
                   context = context)
    }
  }
}

private fun checkProductLayout(context: BuildContext) {
  val layout = context.productProperties.productLayout
  // todo mainJarName type specified as not-null - does it work?
  val messages = context.messages
  @Suppress("SENSELESS_COMPARISON")
  check(layout.mainJarName != null) {
    "productProperties.productLayout.mainJarName is not specified"
  }

  val pluginLayouts = layout.pluginLayouts
  checkScrambleClasspathPlugins(pluginLayouts)
  checkPluginDuplicates(pluginLayouts)
  checkPluginModules(layout.bundledPluginModules, "productProperties.productLayout.bundledPluginModules", context)
  checkPluginModules(layout.pluginModulesToPublish, "productProperties.productLayout.pluginModulesToPublish", context)
  checkPluginModules(layout.compatiblePluginsToIgnore, "productProperties.productLayout.compatiblePluginsToIgnore", context)
  if (!layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
    messages.warning("layout.buildAllCompatiblePlugins option isn't enabled. Value of " +
                     "layout.compatiblePluginsToIgnore property will be ignored (${layout.compatiblePluginsToIgnore})")
  }
  if (layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
    checkPluginModules(layout.compatiblePluginsToIgnore, "productProperties.productLayout.compatiblePluginsToIgnore", context)
  }
  if (!context.shouldBuildDistributions() && layout.buildAllCompatiblePlugins) {
    messages.warning("Distribution is not going to build. Hence all compatible plugins won't be built despite " +
                     "layout.buildAllCompatiblePlugins option is enabled. layout.pluginModulesToPublish will be used (" +
                     layout.pluginModulesToPublish + ")")
  }
  check(!layout.prepareCustomPluginRepositoryForPublishedPlugins ||
        !layout.pluginModulesToPublish.isEmpty() ||
        layout.buildAllCompatiblePlugins) {
    "productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins option is enabled" +
    " but no pluginModulesToPublish are specified"
  }
  checkModules(layout.productApiModules, "productProperties.productLayout.productApiModules", context)
  checkModules(layout.productImplementationModules, "productProperties.productLayout.productImplementationModules", context)
  checkModules(layout.additionalPlatformJars.values(), "productProperties.productLayout.additionalPlatformJars", context)
  checkModules(layout.moduleExcludes.keys, "productProperties.productLayout.moduleExcludes", context)
  checkModules(layout.mainModules, "productProperties.productLayout.mainModules", context)
  checkProjectLibraries(layout.projectLibrariesToUnpackIntoMainJar,
                        "productProperties.productLayout.projectLibrariesToUnpackIntoMainJar", context)
  for (plugin in pluginLayouts) {
    checkBaseLayout(plugin, "\'${plugin.mainModule}\' plugin", context)
  }
}

private fun checkBaseLayout(layout: BaseLayout, description: String, context: BuildContext) {
  checkModules(layout.includedModuleNames.toList(), "moduleJars in $description", context)
  checkArtifacts(layout.includedArtifacts.keys, "includedArtifacts in $description", context)
  checkModules(layout.resourcePaths.map { it.moduleName }, "resourcePaths in $description", context)
  checkModules(layout.moduleExcludes.keys, "moduleExcludes in $description", context)

  checkProjectLibraries(layout.includedProjectLibraries.map { it.libraryName }, "includedProjectLibraries in $description", context)

  for ((moduleName, libraryName) in layout.includedModuleLibraries) {
    checkModules(listOf(moduleName), "includedModuleLibraries in $description", context)
    check(context.findRequiredModule(moduleName).libraryCollection.libraries.any { getLibraryFileName(it) == libraryName }) {
      "Cannot find library \'$libraryName\' in \'$moduleName\' (used in $description)"
    }
  }

  checkModules(layout.excludedModuleLibraries.keySet(), "excludedModuleLibraries in $description", context)
  for ((key, value) in layout.excludedModuleLibraries.entrySet()) {
    val libraries = context.findRequiredModule(key).libraryCollection.libraries
    for (libraryName in value) {
      check(libraries.any { getLibraryFileName(it) == libraryName }) {
        "Cannot find library \'$libraryName\' in \'$key\' (used in \'excludedModuleLibraries\' in $description)"
      }
    }
  }

  checkProjectLibraries(layout.projectLibrariesToUnpack.values(), "projectLibrariesToUnpack in $description", context)
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

private fun checkModules(modules: Collection<String>?, fieldName: String, context: CompilationContext) {
  if (modules != null) {
    val unknownModules = modules.filter { context.findModule(it) == null }
    check(unknownModules.isEmpty()) {
      "The following modules from $fieldName aren\'t found in the project: $unknownModules"
    }
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

  checkModules(pluginModules, fieldName, context)

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

internal fun logFreeDiskSpace(dir: Path, phase: String) {
  Span.current().addEvent("free disk space", Attributes.of(
    AttributeKey.stringKey("phase"), phase,
    AttributeKey.stringKey("usableSpace"), Formats.formatFileSize(Files.getFileStore(dir).usableSpace),
    AttributeKey.stringKey("dir"), dir.toString(),
  ))
}

fun buildUpdaterJar(context: BuildContext, artifactName: String = "updater.jar") {
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

private fun buildCrossPlatformZip(distResults: List<DistributionForOsTaskResult>, context: BuildContext): Path {
  val executableName = context.productProperties.baseFileName

  val productJson = generateMultiPlatformProductJson(
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
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.WINDOWS, arch, isPortableDist = true)),
        ProductInfoLaunchData(
          os = OsFamily.LINUX.osName,
          arch = arch.dirName,
          launcherPath = "bin/${executableName}.sh",
          javaExecutablePath = null,
          vmOptionsFilePath = "bin/linux/${executableName}64.vmoptions",
          startupWmClass = getLinuxFrameClass(context),
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.LINUX, arch, isPortableDist = true)),
        ProductInfoLaunchData(
          os = OsFamily.MACOS.osName,
          arch = arch.dirName,
          launcherPath = "MacOS/$executableName",
          javaExecutablePath = null,
          vmOptionsFilePath = "bin/mac/${executableName}.vmoptions",
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.MACOS, arch, isPortableDist = true))
      )
    }.toList(),
    context = context,
  )

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
    executablePatterns = distResults.flatMap { it.builder.generateExecutableFilesPatterns(includeRuntime = false) },
    distFiles = context.getDistFiles(os = null, arch = null),
    extraFiles = mapOf("dependencies.txt" to dependenciesFile),
    distAllDir = context.paths.distAllDir,
    compress = context.options.compressZipFiles,
  )

  checkInArchive(archiveFile = targetFile, pathInArchive = "", context = context)
  context.notifyArtifactBuilt(targetFile)
  return targetFile
}

private suspend fun checkClassFiles(targetFile: Path, context: BuildContext) {
  val versionCheckerConfig = if (context.isStepSkipped(BuildOptions.VERIFY_CLASS_FILE_VERSIONS)) {
    emptyMap()
  }
  else {
    context.productProperties.versionCheckerConfig
  }

  val forbiddenSubPaths = if (context.options.validateClassFileSubpaths) {
    context.productProperties.forbiddenClassFileSubPaths
  }
  else {
    emptyList()
  }

  val classFileCheckRequired = (versionCheckerConfig.isNotEmpty() || forbiddenSubPaths.isNotEmpty())
  if (classFileCheckRequired) {
    checkClassFiles(versionCheckerConfig, forbiddenSubPaths, targetFile, context.messages)
  }
}

private fun getOsDistributionBuilder(os: OsFamily, ideaProperties: Path? = null, context: BuildContext): OsSpecificDistributionBuilder? {
  return when (os) {
    OsFamily.WINDOWS -> WindowsDistributionBuilder(context = context,
                                                   customizer = context.windowsDistributionCustomizer ?: return null,
                                                   ideaProperties = ideaProperties)
    OsFamily.LINUX -> LinuxDistributionBuilder(context = context,
                                               customizer = context.linuxDistributionCustomizer ?: return null,
                                               ideaProperties = ideaProperties)
    OsFamily.MACOS -> MacDistributionBuilder(context = context,
                                             customizer = (context as BuildContextImpl).macDistributionCustomizer ?: return null,
                                             ideaProperties = ideaProperties)
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

private fun crossPlatformZip(macX64DistDir: Path,
                             macArm64DistDir: Path,
                             linuxX64DistDir: Path,
                             winX64DistDir: Path,
                             targetFile: Path,
                             executableName: String,
                             productJson: ByteArray,
                             executablePatterns: List<String>,
                             distFiles: Collection<DistFile>,
                             extraFiles: Map<String, Path>,
                             distAllDir: Path,
                             compress: Boolean) {
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

      out.entry("product-info.json", productJson)

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
            name.endsWith(".sh") || name.endsWith(".py") -> out.entry("bin/${file.fileName}", file, unixMode = executableFileUnixMode)
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
            if (fileName.startsWith("restarter") || fileName.startsWith("printenv")) {
              out.entry("bin/$fileName", file, unixMode = executableFileUnixMode)
            }
            else if (fileName.startsWith("fsnotifier")) {
              out.entry("bin/mac/$fileName", file, unixMode = executableFileUnixMode)
            }
          }
        }
      }

      val patterns = executablePatterns.map {
        FileSystems.getDefault().getPathMatcher("glob:$it")
      }
      val entryCustomizer: (ZipArchiveEntry, Path, String) -> Unit = { entry, _, relativePathString ->
        val relativePath = Path.of(relativePathString)
        if (patterns.any { it.matches(relativePath) }) {
          entry.unixMode = executableFileUnixMode
        }
      }

      val commonFilter: (String) -> Boolean = { relPath ->
        !relPath.startsWith("Info.plist") &&
        !relPath.startsWith("bin/fsnotifier") &&
        !relPath.startsWith("bin/repair") &&
        !relPath.startsWith("bin/restart") &&
        !relPath.startsWith("bin/printenv") &&
        !(relPath.startsWith("bin/") && (relPath.endsWith(".sh") || relPath.endsWith(".vmoptions")) && relPath.count { it == '/' } == 1) &&
        relPath != "bin/idea.properties" &&
        !relPath.startsWith("help/")
      }

      val zipFileUniqueGuard = HashMap<String, Path>()

      out.dir(distAllDir, "", fileFilter = { _, relPath -> relPath != "bin/idea.properties" }, entryCustomizer = entryCustomizer)

      out.dir(macX64DistDir, "", fileFilter = { _, relativePath ->
        commonFilter.invoke(relativePath) &&
        filterFileIfAlreadyInZip(relativePath, macX64DistDir.resolve(relativePath), zipFileUniqueGuard)
      }, entryCustomizer = entryCustomizer)

      out.dir(macArm64DistDir, "", fileFilter = { _, relPath ->
        commonFilter.invoke(relPath) &&
        filterFileIfAlreadyInZip(relPath, macArm64DistDir.resolve(relPath), zipFileUniqueGuard)
      }, entryCustomizer = entryCustomizer)

      out.dir(linuxX64DistDir, "", fileFilter = { _, relPath ->
        commonFilter.invoke(relPath) &&
        filterFileIfAlreadyInZip(relPath, linuxX64DistDir.resolve(relPath), zipFileUniqueGuard)
      }, entryCustomizer = entryCustomizer)

      out.dir(startDir = winX64DistDir, prefix = "", fileFilter = { _, relativePath ->
        commonFilter.invoke(relativePath) &&
        !(relativePath.startsWith("bin/${executableName}") && relativePath.endsWith(".exe")) &&
        filterFileIfAlreadyInZip(relativePath, winX64DistDir.resolve(relativePath), zipFileUniqueGuard)
      }, entryCustomizer = entryCustomizer)

      for (distFile in distFiles.sortedWith(compareBy<DistFile> { it.relativePath }.thenBy { it.os }.thenBy { it.arch })) {
        // linux and windows: we don't add win and linux specific dist dirs for ARM, so, copy distFiles explicitly
        // macOS: we don't copy dist files for macOS distribution to avoid extra copy operation
        if (zipFileUniqueGuard.putIfAbsent(distFile.relativePath, distFile.file) == null) {
          out.entry(distFile.relativePath, distFile.file)
        }
      }
    }
  }
}

fun getModulesToCompile(buildContext: BuildContext): Set<String> {
  val productLayout = buildContext.productProperties.productLayout
  val result = LinkedHashSet<String>()
  result.addAll(productLayout.getIncludedPluginModules(java.util.Set.copyOf(productLayout.bundledPluginModules)))
  PlatformModules.collectPlatformModules(result)
  result.addAll(productLayout.productApiModules)
  result.addAll(productLayout.productImplementationModules)
  result.addAll(productLayout.additionalPlatformJars.values())
  result.addAll(getToolModules())
  result.addAll(buildContext.productProperties.additionalModulesToCompile)
  result.add("intellij.idea.community.build.tasks")
  result.add("intellij.platform.images.build")
  result.removeAll(productLayout.excludedModuleNames)
  return result
}

// Captures information about all available inspections in a JSON format as part of Inspectopedia project. This is later used by Qodana and other tools.
private suspend fun buildInspectopediaArtifacts(builder: DistributionJARsBuilder,
                                                context: BuildContext) {

  val ideClasspath = builder.createIdeClassPath(context)
  val tempDir = context.paths.tempDir.resolve("inspectopedia-generator")
  val inspectionsPath = tempDir.resolve("inspections-${context.applicationInfo.productCode.lowercase()}")

  runApplicationStarter(context = context,
                        tempDir = tempDir,
                        ideClasspath = ideClasspath,
                        arguments = listOf("inspectopedia-generator", inspectionsPath.toAbsolutePath().toString()))

  val targetFile = context.paths.artifactDir.resolve("inspections-${context.applicationInfo.productCode.lowercase()}.zip")

  zipWithCompression(targetFile = targetFile, dirs = mapOf(inspectionsPath to ""))
}
