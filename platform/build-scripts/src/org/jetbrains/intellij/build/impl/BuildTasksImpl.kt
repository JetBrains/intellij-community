// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.Formats
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope2
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
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.checkInArchive
import org.jetbrains.intellij.build.impl.productInfo.generateProductInfoJson
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.includedModules
import org.jetbrains.intellij.build.impl.projectStructureMapping.writeProjectStructureReport
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.logFreeDiskSpace
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.io.zipWithCompression
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import java.util.zip.Deflater

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

  override suspend fun buildNonBundledPlugins(mainPluginModules: List<String>) {
    checkProductProperties(context)
    checkPluginModules(mainPluginModules, "mainPluginModules", context)
    copyDependenciesFile(context)
    val pluginsToPublish = getPluginLayoutsByJpsModuleNames(mainPluginModules, context.productProperties.productLayout)
    val distState = createDistributionBuilderState(pluginsToPublish = pluginsToPublish,
                                                   context = context)
    val compilationTasks = CompilationTasks.create(context = context)
    compilationTasks.compileModules(
      moduleNames = distState.getModulesForPluginsToPublish() + listOf("intellij.idea.community.build.tasks",
                                                                       "intellij.platform.images.build",
                                                                       "intellij.tools.launcherGenerator"),
    )

    buildProjectArtifacts(
      platform = distState.platform,
      enabledPluginModules = getEnabledPluginModules(pluginsToPublish = distState.pluginsToPublish,
                                                     productProperties = context.productProperties),
      compilationTasks = compilationTasks,
      context = context,
    )
    buildSearchableOptions(distState.platform, context)
    buildNonBundledPlugins(pluginsToPublish = pluginsToPublish,
                           compressPluginArchive = context.options.compressZipFiles,
                           buildPlatformLibJob = null,
                           state = distState,
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

  override suspend fun buildFullUpdaterJar() {
    buildUpdaterJar(context, "updater-full.jar")
  }

  override suspend fun buildUnpackedDistribution(targetDirectory: Path, includeBinAndRuntime: Boolean) {
    val currentOs = OsFamily.currentOs
    context.paths.distAllDir = targetDirectory
    context.options.targetOs = persistentListOf(currentOs)
    context.options.buildStepsToSkip.add(BuildOptions.GENERATE_JAR_ORDER_STEP)
    BundledMavenDownloader.downloadMaven4Libs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMaven3Libs(context.paths.communityHomeDirRoot)
    BundledMavenDownloader.downloadMavenDistribution(context.paths.communityHomeDirRoot)
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
      val propertiesFile = patchIdeaPropertiesFile(context)
      val builder = getOsDistributionBuilder(os = currentOs, ideaProperties = propertiesFile, context = context)!!
      builder.copyFilesForOsDistribution(targetDirectory, arch)
      context.bundledRuntime.extractTo(prefix = BundledRuntimeImpl.getProductPrefix(context),
                                       os = currentOs,
                                       destinationDir = targetDirectory.resolve("jbr"),
                                       arch = arch)
      updateExecutablePermissions(targetDirectory, builder.generateExecutableFilesMatchers(includeRuntime = true, arch).keys)
      builder.checkExecutablePermissions(targetDirectory, root = "", includeRuntime = true, arch = arch)
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
  writeProjectStructureReport(
    entries = generateProjectStructureMapping(context = context, platform = createPlatformLayout(pluginsToPublish = emptySet(),
                                                                                                 context = context)),
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
  return path.endsWith(".java") && path != "module-info.java" || path.endsWith(".groovy") || path.endsWith(".kt")
}

private fun getLocalArtifactRepositoryRoot(global: JpsGlobal): Path {
  JpsModelSerializationDataService.getPathVariablesConfiguration(global)!!.getUserVariableValue("MAVEN_REPOSITORY")?.let {
    return Path.of(it)
  }

  val root = System.getProperty("user.home", null)
  return if (root == null) Path.of(".m2/repository") else Path.of(root, ".m2/repository")
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
  checkClassFiles(context.paths.distAllDir, context, isDistAll = true)
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

internal suspend fun updateExecutablePermissions(destinationDir: Path, executableFilesMatchers: Collection<PathMatcher>) {
  spanBuilder("update executable permissions").setAttribute("dir", "$destinationDir").useWithScope(Dispatchers.IO) {
    val executable = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
                                PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
                                PosixFilePermission.OTHERS_EXECUTE)
    val regular = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                             PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)
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

private suspend fun buildOsSpecificDistributions(context: BuildContext): List<DistributionForOsTaskResult> {
  if (context.isStepSkipped(BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP)) {
    Span.current().addEvent("skip step", Attributes.of(AttributeKey.stringKey("name"), "build OS-specific distributions"))
    return emptyList()
  }

  setLastModifiedTime(context.paths.distAllDir, context)

  if (context.isMacCodeSignEnabled) {
    withContext(Dispatchers.IO) {
      for (file in Files.newDirectoryStream(context.paths.distAllDir).use { stream ->
        stream.filter { !it.endsWith("lib") && !it.endsWith("help") }
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

  val propertiesFile = patchIdeaPropertiesFile(context)
  return supervisorScope {
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
          checkClassFiles(osAndArchSpecificDistDirectory, context, isDistAll = false)
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
        // We pack sources of libraries which are included in compilation classpath for platform API modules.
        // This way we'll get sources of all libraries useful for plugin developers, and the size of the archive will be reasonable.
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

    context.notifyArtifactBuilt(targetFile)
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

private suspend fun compileModulesForDistribution(context: BuildContext): DistributionBuilderState {
  val productProperties = context.productProperties
  val mavenArtifacts = productProperties.mavenArtifacts

  val toCompile = LinkedHashSet<String>()
  collectModulesToCompile(context = context, result = toCompile)
  context.proprietaryBuildTools.scrambleTool?.let {
    toCompile.addAll(it.additionalModulesToCompile)
  }
  toCompile.addAll(productProperties.productLayout.mainModules)
  toCompile.addAll(mavenArtifacts.additionalModules)
  toCompile.addAll(mavenArtifacts.squashedModules)
  toCompile.addAll(mavenArtifacts.proprietaryModules)
  toCompile.addAll(productProperties.modulesToCompileTests)
  toCompile.add("intellij.tools.launcherGenerator")

  val compilationTasks = CompilationTasks.create(context)
  compilationTasks.compileModules(toCompile)

  val pluginsToPublish = getPluginLayoutsByJpsModuleNames(modules = context.productProperties.productLayout.pluginModulesToPublish,
                                                          productLayout = context.productProperties.productLayout)
  filterPluginsToPublish(pluginsToPublish, context)

  var enabledPluginModules = getEnabledPluginModules(pluginsToPublish = pluginsToPublish, productProperties = context.productProperties)
  // computed only based on a bundled and plugins to publish lists, compatible plugins are not taken in an account by intention
  val projectLibrariesUsedByPlugins = computeProjectLibsUsedByPlugins(enabledPluginModules = enabledPluginModules, context = context)
  val productLayout = context.productProperties.productLayout
  val addPlatformCoverage = !productLayout.excludedModuleNames.contains("intellij.platform.coverage") &&
                            hasPlatformCoverage(productLayout = productLayout,
                                                enabledPluginModules = enabledPluginModules,
                                                context = context)

  if (context.shouldBuildDistributions()) {
    if (context.options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
      Span.current().addEvent("skip collecting compatible plugins because PROVIDED_MODULES_LIST_STEP was skipped")
    }
    else {
      val providedModuleFile = context.paths.artifactDir.resolve("${context.applicationInfo.productCode}-builtinModules.json")
      val platform = createPlatformLayout(pluginsToPublish, context)
      compilationTasks.compileModules(moduleNames = getModulesForPluginsToPublish(platform, pluginsToPublish))
      val builtinModuleData = spanBuilder("build provided module list").useWithScope2 {
        val ideClasspath = createIdeClassPath(platform = platform, context = context)

        Files.deleteIfExists(providedModuleFile)
        // start the product in headless mode using com.intellij.ide.plugins.BundledPluginsLister
        runApplicationStarter(context = context,
                              tempDir = context.paths.tempDir.resolve("builtinModules"),
                              ideClasspath = ideClasspath,
                              arguments = listOf("listBundledPlugins", providedModuleFile.toString()))
        context.productProperties.customizeBuiltinModules(context = context, builtinModulesFile = providedModuleFile)
        try {
          val builtinModuleData = readBuiltinModulesFile(file = providedModuleFile)
          context.builtinModule = builtinModuleData
          builtinModuleData
        }
        catch (e: NoSuchFileException) {
          throw IllegalStateException("Failed to build provided modules list: $providedModuleFile doesn\'t exist")
        }
      }

      context.notifyArtifactBuilt(artifactPath = providedModuleFile)
      if (!productProperties.productLayout.buildAllCompatiblePlugins) {
        val distState = DistributionBuilderState(platform = platform, pluginsToPublish = pluginsToPublish, context = context)
        buildProjectArtifacts(platform = distState.platform,
                              enabledPluginModules = enabledPluginModules,
                              compilationTasks = compilationTasks,
                              context = context)
        return distState
      }

      collectCompatiblePluginsToPublish(builtinModuleData = builtinModuleData, context = context, result = pluginsToPublish)
      filterPluginsToPublish(pluginsToPublish, context)

      // update enabledPluginModules to reflect changes in pluginsToPublish - used for buildProjectArtifacts
      enabledPluginModules = getEnabledPluginModules(pluginsToPublish = pluginsToPublish, productProperties = context.productProperties)
    }
  }

  val platform = createPlatformLayout(
    addPlatformCoverage = addPlatformCoverage,
    projectLibrariesUsedByPlugins = projectLibrariesUsedByPlugins,
    context = context,
  )
  val distState = DistributionBuilderState(platform = platform, pluginsToPublish = pluginsToPublish, context = context)
  compilationTasks.compileModules(distState.getModulesForPluginsToPublish())

  buildProjectArtifacts(platform = distState.platform,
                        enabledPluginModules = enabledPluginModules,
                        compilationTasks = compilationTasks,
                        context = context)
  return distState
}

private fun buildProjectArtifacts(platform: PlatformLayout,
                                  enabledPluginModules: Set<String>,
                                  compilationTasks: CompilationTasks,
                                  context: BuildContext) {
  val artifactNames = LinkedHashSet<String>()
  artifactNames.addAll(platform.includedArtifacts.keys)
  getPluginLayoutsByJpsModuleNames(modules = enabledPluginModules, productLayout = context.productProperties.productLayout)
    .flatMapTo(artifactNames) { it.includedArtifacts.keys }

  compilationTasks.buildProjectArtifacts(artifactNames)
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
      if (context.shouldBuildDistributions()) {
        val entries = buildDistribution(state = distributionState, context)
        if (context.productProperties.buildSourcesArchive) {
          buildSourcesArchive(entries, context)
        }
      }
      else {
        Span.current().addEvent("skip building product distributions because " +
                                "\"intellij.build.target.os\" property is set to \"${BuildOptions.OS_NONE}\"")
        buildSearchableOptions(distributionState.platform, context)
        buildNonBundledPlugins(pluginsToPublish = pluginsToPublish,
                               compressPluginArchive = context.options.compressZipFiles,
                               buildPlatformLibJob = null,
                               state = distributionState,
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
        context.executeStep(spanBuilder("build toolbox lite-gen links"), BuildOptions.TOOLBOX_LITE_GEN_STEP) {
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
    val moduleNames = HashSet<String>()
    if (mavenArtifacts.forIdeModules) {
      moduleNames.addAll(distributionState.platformModules)
      val productLayout = context.productProperties.productLayout
      collectIncludedPluginModules(enabledPluginModules = productLayout.bundledPluginModules, product = productLayout, result = moduleNames)
    }

    val mavenArtifactsBuilder = MavenArtifactsBuilder(context)
    moduleNames.addAll(mavenArtifacts.additionalModules)
    if (!moduleNames.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(moduleNamesToPublish = moduleNames,
                                                   moduleNamesToSquashAndPublish = mavenArtifacts.squashedModules,
                                                   outputDir = "maven-artifacts")
    }
    if (!mavenArtifacts.proprietaryModules.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(moduleNamesToPublish = mavenArtifacts.proprietaryModules,
                                                   moduleNamesToSquashAndPublish = emptyList(),
                                                   outputDir = "proprietary-maven-artifacts")
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
      context.messages.error("Cannot find product-modules.xml file in sources of '$embeddedJetBrainsClientMainModule' module specified as " +
                             "'productProperties.embeddedJetBrainsClientMainModule'.")
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
  checkModules(layout.moduleExcludes.keys, "productProperties.productLayout.moduleExcludes", context)
  checkModules(layout.mainModules, "productProperties.productLayout.mainModules", context)
  for (plugin in pluginLayouts) {
    checkBaseLayout(plugin, "\'${plugin.mainModule}\' plugin", context)
  }
}

private fun checkBaseLayout(layout: BaseLayout, description: String, context: BuildContext) {
  checkModules(layout.includedModules.asSequence().map { it.moduleName }.distinct().toList(), "moduleJars in $description", context)
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

suspend fun buildUpdaterJar(context: BuildContext, artifactName: String = "updater.jar") {
  val updaterModule = context.findRequiredModule("intellij.platform.updater")
  val updaterModuleSource = DirSource(context.getModuleOutputDir(updaterModule), excludes = commonModuleExcludes)
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
          mainClass = context.ideMainClassName),
        ProductInfoLaunchData(
          os = OsFamily.LINUX.osName,
          arch = arch.dirName,
          launcherPath = "bin/${executableName}.sh",
          javaExecutablePath = null,
          vmOptionsFilePath = "bin/linux/${executableName}64.vmoptions",
          startupWmClass = getLinuxFrameClass(context),
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.LINUX, arch, isPortableDist = true),
          mainClass = context.ideMainClassName),
        ProductInfoLaunchData(
          os = OsFamily.MACOS.osName,
          arch = arch.dirName,
          launcherPath = "bin/${executableName}.sh",
          javaExecutablePath = null,
          vmOptionsFilePath = "bin/mac/${executableName}.vmoptions",
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.MACOS, arch, isPortableDist = true),
          mainClass = context.ideMainClassName)
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
    executablePatterns = distResults.flatMap {
      it.builder.generateExecutableFilesMatchers(includeRuntime = false, JvmArchitecture.x64).keys +
      it.builder.generateExecutableFilesMatchers(includeRuntime = false, JvmArchitecture.aarch64).keys
    },
    distFiles = context.getDistFiles(os = null, arch = null),
    extraFiles = mapOf("dependencies.txt" to dependenciesFile),
    distAllDir = context.paths.distAllDir,
    compress = context.options.compressZipFiles,
  )

  checkInArchive(archiveFile = targetFile, pathInArchive = "", context = context)
  context.notifyArtifactBuilt(targetFile)
  return targetFile
}

private suspend fun checkClassFiles(root: Path, context: BuildContext, isDistAll: Boolean) {
  // version checking patterns are only for dist all (all non-os and non-arch specific files)
  val versionCheckerConfig = if (context.isStepSkipped(BuildOptions.VERIFY_CLASS_FILE_VERSIONS) || !isDistAll) {
    emptyMap()
  }
  else {
    context.productProperties.versionCheckerConfig
  }

  val forbiddenSubPaths = context.productProperties.forbiddenClassFileSubPaths
  val forbiddenSubPathExceptions = context.productProperties.forbiddenClassFileSubPathExceptions
  if (forbiddenSubPaths.isNotEmpty()) {
    val forbiddenString = forbiddenSubPaths.let { "(${it.size}): ${it.joinToString()}" }
    val exceptionsString = forbiddenSubPathExceptions.let { "(${it.size}): ${it.joinToString()}" }
    context.messages.warning("checkClassFiles: forbiddenSubPaths $forbiddenString, exceptions $exceptionsString")
  }
  else {
    context.messages.warning("checkClassFiles: forbiddenSubPaths: EMPTY (no scrambling checks will be done)")
  }

  if (versionCheckerConfig.isNotEmpty() || forbiddenSubPaths.isNotEmpty()) {
    checkClassFiles(versionCheckConfig = versionCheckerConfig,
                    forbiddenSubPaths = forbiddenSubPaths,
                    forbiddenSubPathExceptions = forbiddenSubPathExceptions,
                    root = root,
                    messages = context.messages)
  }

  if (forbiddenSubPaths.isNotEmpty()) {
    context.messages.warning("checkClassFiles: SUCCESS for forbiddenSubPaths at '$root': ${forbiddenSubPaths.joinToString()}")
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
                             executablePatterns: List<PathMatcher>,
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
        !relPath.startsWith("bin/remote-dev-server") &&
        relPath != "license/remote-dev-server.html"
      }

      val zipFileUniqueGuard = HashMap<String, Path>()

      out.dir(distAllDir, "", fileFilter = { _, relPath -> relPath != "bin/idea.properties" }, entryCustomizer = entryCustomizer)

      for (macDistDir in arrayOf(macX64DistDir, macArm64DistDir)) {
        out.dir(macDistDir, "", fileFilter = { _, relPath ->
          commonFilter.invoke(relPath) &&
          !relPath.startsWith("MacOS/") &&
          !relPath.startsWith("Resources/") &&
          !relPath.startsWith("Info.plist") &&
          filterFileIfAlreadyInZip(relPath, macArm64DistDir.resolve(relPath), zipFileUniqueGuard)
        }, entryCustomizer = entryCustomizer)
      }

      out.dir(linuxX64DistDir, "", fileFilter = { _, relPath ->
        commonFilter.invoke(relPath) &&
        filterFileIfAlreadyInZip(relPath, linuxX64DistDir.resolve(relPath), zipFileUniqueGuard)
      }, entryCustomizer = entryCustomizer)

      out.dir(startDir = winX64DistDir, prefix = "", fileFilter = { _, relPath ->
        commonFilter.invoke(relPath) &&
        !(relPath.startsWith("bin/${executableName}") && relPath.endsWith(".exe")) &&
        filterFileIfAlreadyInZip(relPath, winX64DistDir.resolve(relPath), zipFileUniqueGuard)
      }, entryCustomizer = entryCustomizer)

      for (distFile in distFiles) {
        // Linux and Windows: we don't add specific dist dirs for ARM, so, copy dist files explicitly
        // macOS: we don't copy dist files to avoid extra copy operation
        if (zipFileUniqueGuard.putIfAbsent(distFile.relativePath, distFile.file) == null) {
          out.entry(distFile.relativePath, distFile.file)
        }
      }
    }
  }
}

fun collectModulesToCompile(context: BuildContext, result: MutableCollection<String>) {
  val productLayout = context.productProperties.productLayout
  collectIncludedPluginModules(enabledPluginModules = productLayout.bundledPluginModules.toHashSet(),
                               product = productLayout,
                               result = result)
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
// This is later used by Qodana and other tools. Keymaps are extracted as an XML file and also used in help authoring.
internal suspend fun buildAdditionalAuthoringArtifacts(ideClassPath: Set<String>, context: BuildContext) {
  context.executeStep(spanBuilder("build authoring asserts"), BuildOptions.DOC_AUTHORING_ASSETS_STEP) {
    val commands = listOf(Pair("inspectopedia-generator", "inspections-${context.applicationInfo.productCode.lowercase()}"),
                          Pair("keymap", "keymap-${context.applicationInfo.productCode.lowercase()}"))
    val temporaryBuildDirectory = context.paths.tempDir
    for (command in commands) {
      launch {
        val temporaryStepDirectory = temporaryBuildDirectory.resolve(command.first)
        val targetPath = temporaryStepDirectory.resolve(command.second)
        runApplicationStarter(context = context,
                              tempDir = temporaryStepDirectory,
                              ideClasspath = ideClassPath,
                              arguments = listOf(command.first, targetPath.toString()))

        val targetFile = context.paths.artifactDir.resolve("${command.second}.zip")
        zipWithCompression(targetFile = targetFile,
                           dirs = mapOf(targetPath to ""),
                           compressionLevel = if (context.options.compressZipFiles) Deflater.DEFAULT_COMPRESSION else Deflater.NO_COMPRESSION)
      }
    }
  }
}

internal suspend fun setLastModifiedTime(directory: Path, context: BuildContext) {
  withContext(Dispatchers.IO) {
    spanBuilder("update last modified time").setAttribute("dir", directory.toString()).useWithScope2 {
      Files.walk(directory).use { tree ->
        val fileTime = FileTime.from(context.options.buildDateInSeconds, TimeUnit.SECONDS)
        tree.forEach {
          Files.setLastModifiedTime(it, fileTime)
        }
      }
    }
  }
}

/**
 * @return list of all modules which output is included in the plugin's JARs
 */
internal fun collectIncludedPluginModules(enabledPluginModules: Collection<String>,
                                          product: ProductModulesLayout,
                                          result: MutableCollection<String>) {
  result.addAll(enabledPluginModules)
  product.pluginLayouts.asSequence()
    .filter { enabledPluginModules.contains(it.mainModule) }
    .flatMapTo(result) { layout -> layout.includedModules.asSequence().map { it.moduleName } }
}

fun copyDistFiles(context: BuildContext, newDir: Path, os: OsFamily, arch: JvmArchitecture) {
  for (item in context.getDistFiles(os, arch)) {
    val targetFile = newDir.resolve(item.relativePath)
    Files.createDirectories(targetFile.parent)
    Files.copy(item.file, targetFile, StandardCopyOption.REPLACE_EXISTING)
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
