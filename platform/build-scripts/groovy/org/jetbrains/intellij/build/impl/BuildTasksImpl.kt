// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.use
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
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
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.idea.maven.aether.ProgressConsumer
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.finish
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.JarPackager.Companion.getLibraryName
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.checkInArchive
import org.jetbrains.intellij.build.impl.productInfo.generateMultiPlatformProductJson
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.writeNewFile
import org.jetbrains.intellij.build.io.zip
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
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinTask
import java.util.function.BiConsumer
import java.util.function.Predicate
import java.util.stream.Collectors

class BuildTasksImpl(private val context: BuildContext) : BuildTasks {
  companion object {
    fun buildDistributions(context: BuildContext) {
      try {
        spanBuilder("build distributions").useWithScope {
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
  }

  override fun zipSourcesOfModules(modules: Collection<String>, targetFile: Path, includeLibraries: Boolean) {
    zipSourcesOfModules(modules, targetFile, includeLibraries, context)
  }

  override fun compileModulesFromProduct() {
    checkProductProperties(context)
    compileModulesForDistribution(context)
  }

  override fun buildDistributions() {
    try {
      spanBuilder("build distributions").useWithScope {
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

  override fun buildDmg(macZipDir: Path) {
    fun createTask(arch: JvmArchitecture, context: BuildContext): ForkJoinTask<*>? {
      val macZip = find(macZipDir, "${arch}.zip", context)
      val builtModule = BuiltinModulesFileUtils.readBuiltinModulesFile(find(macZipDir, "builtinModules.json", context))
      return MacDistributionBuilder(context, context.macDistributionCustomizer, null)
        .buildAndSignDmgFromZip(macZip, arch, builtModule)
    }
    invokeAllSettled(listOfNotNull(createTask(JvmArchitecture.x64, context), createTask(JvmArchitecture.aarch64, context)))
  }

  override fun buildNonBundledPlugins(mainPluginModules: List<String>) {
    checkProductProperties(context)
    checkPluginModules(mainPluginModules, "mainPluginModules", context.productProperties.productLayout.allNonTrivialPlugins, context)
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

  override fun compileProjectAndTests(includingTestsInModules: List<String>) {
    compileModules(null, includingTestsInModules)
  }

  override fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>) {
    CompilationTasks.create(context).compileModules(moduleNames, includingTestsInModules)
  }

  override fun compileModules(moduleNames: Collection<String>?) {
    CompilationTasks.create(context).compileModules(moduleNames)
  }

  override fun buildUpdaterJar() {
    doBuildUpdaterJar("updater.jar", context)
  }

  override fun buildFullUpdaterJar() {
    doBuildUpdaterJar("updater-full.jar", context)
  }

  override fun runTestBuild() {
    checkProductProperties(context)
    val projectStructureMapping = DistributionJARsBuilder(compileModulesForDistribution(context)).buildJARs(context)
    layoutShared(context)
    checkClassVersion(context.paths.distAllDir, context)
    if (context.productProperties.buildSourcesArchive) {
      buildSourcesArchive(projectStructureMapping, context)
    }
    buildOsSpecificDistributions(context)
  }

  override fun buildUnpackedDistribution(targetDirectory: Path, includeBinAndRuntime: Boolean) {
    val currentOs = OsFamily.currentOs
    context.paths.distAllDir = targetDirectory
    context.options.targetOs = currentOs.osId
    context.options.buildStepsToSkip.add(BuildOptions.GENERATE_JAR_ORDER_STEP)
    BundledMavenDownloader.downloadMavenCommonLibs(context.paths.buildDependenciesCommunityRoot)
    BundledMavenDownloader.downloadMavenDistribution(context.paths.buildDependenciesCommunityRoot)
    DistributionJARsBuilder(compileModulesForDistribution(context)).buildJARs(context, true)
    val arch = if (CpuArch.isArm64()) JvmArchitecture.aarch64 else JvmArchitecture.x64
    layoutShared(context)
    if (includeBinAndRuntime) {
      val propertiesFile = patchIdeaPropertiesFile(context)
      val builder = getOsDistributionBuilder(os = currentOs, ideaProperties = propertiesFile, context = context)
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

data class SupportedDistribution(@JvmField val os: OsFamily, @JvmField val arch: JvmArchitecture)

@JvmField
val SUPPORTED_DISTRIBUTIONS: List<SupportedDistribution> = listOf(
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
    runApplicationStarter(context = context,
                          tempDir = context.paths.tempDir.resolve("builtinModules"),
                          ideClasspath = ideClasspath,
                          arguments = listOf("listBundledPlugins", targetFile.toString()),
                          classpathCustomizer = context.classpathCustomizer)
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
  spanBuilder("copy files shared among all distributions").use {
    val licenseOutDir = context.paths.distAllDir.resolve("license")
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

internal class DistributionForOsTaskResult(@JvmField val os: OsFamily,
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

private fun buildOsSpecificDistributions(context: BuildContext): List<DistributionForOsTaskResult> {
  val stepMessage = "build OS-specific distributions"
  if (context.options.buildStepsToSkip.contains(BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP)) {
    Span.current().addEvent("skip step", Attributes.of(AttributeKey.stringKey("name"), stepMessage))
    return emptyList()
  }

  val propertiesFile = patchIdeaPropertiesFile(context)

  fun createDistributionForOsTask(os: OsFamily, arch: JvmArchitecture): BuildTaskRunnable? {
    if (!context.shouldBuildDistributionForOS(os.osId)) {
      return null
    }

    return BuildTaskRunnable("${os.osId} ${arch.name}") {
      val builder = getOsDistributionBuilder(os = os, ideaProperties = propertiesFile, context = context)
      val osAndArchSpecificDistDirectory = DistributionJARsBuilder.getOsAndArchSpecificDistDirectory(os, arch, context)
      builder.buildArtifacts(osAndArchSpecificDistDirectory, arch)
      DistributionForOsTaskResult(os, arch, osAndArchSpecificDistDirectory)
    }
  }

  val distTasks = SUPPORTED_DISTRIBUTIONS.mapNotNull {
    createDistributionForOsTask(it.os, it.arch)
  }

  return spanBuilder(stepMessage).useWithScope {
    runInParallel(distTasks, context)
  }
}

private fun runInParallel(tasks: List<BuildTaskRunnable>, context: BuildContext): List<DistributionForOsTaskResult> {
  if (tasks.isEmpty()) {
    return emptyList()
  }

  if (!context.options.runBuildStepsInParallel) {
    return tasks.map { it.task() }
  }

  val futures = ArrayList<ForkJoinTask<DistributionForOsTaskResult>>(tasks.size)
  val traceContext = Context.current()
  for (task in tasks) {
    if (context.options.buildStepsToSkip.contains(task.stepId)) {
      Span.current().addEvent("skip step", Attributes.of(AttributeKey.stringKey("id"), task.stepId))
      continue
    }

    futures.add(ForkJoinTask.adapt(Callable {
      spanBuilder(task.stepId).setParent(traceContext).useWithScope {
        task.task()
      }
    }).fork())
  }

  val errors = ArrayList<Throwable>()
  // inversed order of join - better for FJP (https://shipilev.net/talks/jeeconf-May2012-forkjoin.pdf, slide 32)
  for (future in futures.asReversed()) {
    try {
      future.join()
    }
    catch (e: Throwable) {
      errors.add(e)
    }
  }

  if (!errors.isEmpty()) {
    Span.current().setStatus(StatusCode.ERROR)
    if (errors.size == 1) {
      throw errors.first()
    }
    else {
      throw CompoundRuntimeException(errors)
    }
  }

  return futures.map { it.rawResult }
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

private fun buildSourcesArchive(projectStructureMapping: ProjectStructureMapping, context: BuildContext) {
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

fun zipSourcesOfModules(modules: Collection<String>, targetFile: Path, includeLibraries: Boolean, context: BuildContext) {
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

    spanBuilder("pack")
      .setAttribute("targetFile", context.paths.buildOutputDir.relativize(targetFile).toString())
      .useWithScope {
        zip(targetFile, zipFileMap, compress = true)
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

private fun buildAdditionalArtifacts(projectStructureMapping: ProjectStructureMapping, context: BuildContext) {
  val productProperties = context.productProperties
  if (productProperties.generateLibrariesLicensesTable &&
      !context.options.buildStepsToSkip.contains(BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP)) {
    val artifactNamePrefix = productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)
    val artifactDir = context.paths.artifactDir
    Files.createDirectories(artifactDir)
    Files.copy(getThirdPartyLibrariesHtmlFilePath(context), artifactDir.resolve("$artifactNamePrefix-third-party-libraries.html"))
    Files.copy(getThirdPartyLibrariesJsonFilePath(context), artifactDir.resolve("$artifactNamePrefix-third-party-libraries.json"))
    context.notifyArtifactBuilt(artifactDir.resolve("$artifactNamePrefix-third-party-libraries.html"))
    context.notifyArtifactBuilt(artifactDir.resolve("$artifactNamePrefix-third-party-libraries.json"))
  }

  if (productProperties.buildSourcesArchive) {
    buildSourcesArchive(projectStructureMapping, context)
  }
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
    if (!productProperties.productLayout.buildAllCompatiblePlugins) {
      return state
    }

    if (context.options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
      Span.current().addEvent("skip collecting compatible plugins because PROVIDED_MODULES_LIST_STEP was skipped")
    }
    else {
      return compilePlatformAndPluginModules(
        pluginsToPublish = pluginsToPublish + PluginsCollector.collectCompatiblePluginsToPublish(providedModulesFile, context),
        context = context
      )
    }
  }
  return compilePlatformAndPluginModules(pluginsToPublish, context)
}

internal class BuildTaskRunnable(
  @JvmField val stepId: String,
  @JvmField val task: () -> DistributionForOsTaskResult,
)

private fun compileModulesForDistribution(context: BuildContext): DistributionBuilderState {
  val pluginsToPublish = getPluginsByModules(context.productProperties.productLayout.getPluginModulesToPublish(), context)
  return compileModulesForDistribution(pluginsToPublish, context)
}

private fun doBuildDistributions(context: BuildContext) {
  checkProductProperties(context)
  copyDependenciesFile(context)
  logFreeDiskSpace("before compilation", context)
  val pluginsToPublish = getPluginsByModules(context.productProperties.productLayout.getPluginModulesToPublish(), context)
  val distributionState = compileModulesForDistribution(context)
  logFreeDiskSpace("after compilation", context)
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

  spanBuilder("build platform and plugin JARs").useWithScope {
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
  }

  if (context.shouldBuildDistributions()) {
    layoutShared(context)
    val distDirs = buildOsSpecificDistributions(context)
    if (java.lang.Boolean.getBoolean("intellij.build.toolbox.litegen")) {
      @Suppress("SENSELESS_COMPARISON")
      if (context.buildNumber == null) {
        context.messages.warning("Toolbox LiteGen is not executed - it does not support SNAPSHOT build numbers")
      }
      else if (context.options.targetOs != BuildOptions.OS_ALL) {
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
      if (distDirs.size == SUPPORTED_DISTRIBUTIONS.size) {
        context.executeStep("build cross-platform distribution", BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP) {
          buildCrossPlatformZip(distDirs, context)
        }
      }
      else {
        Span.current().addEvent("skip building cross-platform distribution because some OS/arch-specific distributions were skipped")
      }
    }
  }
  logFreeDiskSpace("after building distributions", context)
}

private fun checkProductProperties(context: BuildContext) {
  checkProductLayout(context)

  val properties = context.productProperties
  val messages = context.messages
  checkPaths2(properties.brandingResourcePaths, "productProperties.brandingResourcePaths", messages)
  checkPaths2(properties.additionalIDEPropertiesFilePaths, "productProperties.additionalIDEPropertiesFilePaths", messages)
  checkPaths2(properties.additionalDirectoriesWithLicenses, "productProperties.additionalDirectoriesWithLicenses", messages)
  checkModules(properties.additionalModulesToCompile, "productProperties.additionalModulesToCompile", context)
  checkModules(properties.modulesToCompileTests, "productProperties.modulesToCompileTests", context)

  context.windowsDistributionCustomizer?.let { winCustomizer ->
    checkPaths(listOfNotNull(winCustomizer.icoPath), "productProperties.windowsCustomizer.icoPath", messages)
    checkPaths(listOfNotNull(winCustomizer.icoPathForEAP), "productProperties.windowsCustomizer.icoPathForEAP", messages)
    checkPaths(listOfNotNull(winCustomizer.installerImagesPath), "productProperties.windowsCustomizer.installerImagesPath", messages)
  }

  context.linuxDistributionCustomizer?.let { linuxDistributionCustomizer ->
    checkPaths(listOfNotNull(linuxDistributionCustomizer.iconPngPath), "productProperties.linuxCustomizer.iconPngPath", messages)
    checkPaths(listOfNotNull(linuxDistributionCustomizer.iconPngPathForEAP), "productProperties.linuxCustomizer.iconPngPathForEAP",
               messages)
  }

  context.macDistributionCustomizer?.let { macCustomizer ->
    checkMandatoryField(macCustomizer.bundleIdentifier, "productProperties.macCustomizer.bundleIdentifier", messages)
    checkMandatoryPath(macCustomizer.icnsPath, "productProperties.macCustomizer.icnsPath", messages)
    checkPaths(listOfNotNull(macCustomizer.icnsPathForEAP), "productProperties.macCustomizer.icnsPathForEAP", messages)
    checkMandatoryPath(macCustomizer.dmgImagePath, "productProperties.macCustomizer.dmgImagePath", messages)
    checkPaths(listOfNotNull(macCustomizer.dmgImagePathForEAP), "productProperties.macCustomizer.dmgImagePathForEAP", messages)
  }

  checkModules(properties.mavenArtifacts.additionalModules, "productProperties.mavenArtifacts.additionalModules", context)
  checkModules(properties.mavenArtifacts.squashedModules, "productProperties.mavenArtifacts.squashedModules", context)
  if (context.productProperties.scrambleMainJar) {
    context.proprietaryBuildTools.scrambleTool?.let {
      checkModules(it.getNamesOfModulesRequiredToBeScrambled(),
                   "ProprietaryBuildTools.scrambleTool.namesOfModulesRequiredToBeScrambled",
                   context)
    }
  }
}

private fun checkProductLayout(context: BuildContext) {
  val layout = context.productProperties.productLayout
  // todo mainJarName type specified as not-null - does it work?
  val messages = context.messages
  @Suppress("SENSELESS_COMPARISON")
  if (layout.mainJarName == null) {
    messages.error("productProperties.productLayout.mainJarName is not specified")
  }

  val nonTrivialPlugins = layout.allNonTrivialPlugins
  checkPluginDuplicates(nonTrivialPlugins, messages)
  checkPluginModules(layout.bundledPluginModules, "productProperties.productLayout.bundledPluginModules", nonTrivialPlugins, context)
  checkPluginModules(layout.getPluginModulesToPublish(), "productProperties.productLayout.pluginModulesToPublish", nonTrivialPlugins, context)
  checkPluginModules(layout.compatiblePluginsToIgnore, "productProperties.productLayout.compatiblePluginsToIgnore", nonTrivialPlugins, context)
  if (!layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
    messages.warning("layout.buildAllCompatiblePlugins option isn't enabled. Value of " +
                     "layout.compatiblePluginsToIgnore property will be ignored (" + layout.compatiblePluginsToIgnore.toString() +
                     ")")
  }
  if (layout.buildAllCompatiblePlugins && !layout.compatiblePluginsToIgnore.isEmpty()) {
    checkPluginModules(layout.compatiblePluginsToIgnore, "productProperties.productLayout.compatiblePluginsToIgnore",
                       nonTrivialPlugins,
                       context)
  }
  if (!context.shouldBuildDistributions() && layout.buildAllCompatiblePlugins) {
    messages.warning("Distribution is not going to build. Hence all compatible plugins won't be built despite " +
                     "layout.buildAllCompatiblePlugins option is enabled. layout.pluginModulesToPublish will be used (" +
                     layout.getPluginModulesToPublish().toString() +
                     ")")
  }
  if (layout.prepareCustomPluginRepositoryForPublishedPlugins &&
      layout.getPluginModulesToPublish().isEmpty() &&
      !layout.buildAllCompatiblePlugins) {
    messages.error("productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins option is enabled" +
                   " but no pluginModulesToPublish are specified")
  }
  checkModules(layout.productApiModules, "productProperties.productLayout.productApiModules", context)
  checkModules(layout.productImplementationModules, "productProperties.productLayout.productImplementationModules", context)
  checkModules(layout.additionalPlatformJars.values(), "productProperties.productLayout.additionalPlatformJars", context)
  checkModules(layout.moduleExcludes.keySet(), "productProperties.productLayout.moduleExcludes", context)
  checkModules(layout.mainModules, "productProperties.productLayout.mainModules", context)
  checkProjectLibraries(layout.projectLibrariesToUnpackIntoMainJar,
                        "productProperties.productLayout.projectLibrariesToUnpackIntoMainJar", context)
  for (plugin in nonTrivialPlugins) {
    checkBaseLayout(plugin as BaseLayout, "\'${plugin.mainModule}\' plugin", context)
  }
}

private fun checkBaseLayout(layout: BaseLayout, description: String, context: BuildContext) {
  checkModules(layout.getIncludedModuleNames(), "moduleJars in $description", context)
  checkArtifacts(layout.includedArtifacts.keys, "includedArtifacts in $description", context)
  checkModules(layout.resourcePaths.map { it.moduleName }, "resourcePaths in $description", context)
  checkModules(layout.moduleExcludes.keySet(), "moduleExcludes in $description", context)

  checkProjectLibraries(layout.includedProjectLibraries.map { it.libraryName }, "includedProjectLibraries in $description", context)

  for ((moduleName, libraryName) in layout.includedModuleLibraries) {
    checkModules(listOf(moduleName), "includedModuleLibraries in $description", context)
    if (!context.findRequiredModule(moduleName).libraryCollection.libraries.any { getLibraryName(it) == libraryName }) {
      context.messages.error("Cannot find library \'$libraryName\' in \'$moduleName\' (used in $description)")
    }
  }

  checkModules(layout.excludedModuleLibraries.keySet(), "excludedModuleLibraries in $description", context)
  for ((key, value) in layout.excludedModuleLibraries.entrySet()) {
    val libraries = context.findRequiredModule(key).libraryCollection.libraries
    for (libraryName in value) {
      if (!libraries.any { getLibraryName(it) == libraryName }) {
        context.messages.error("Cannot find library \'$libraryName\' in \'$key\' (used in \'excludedModuleLibraries\' in $description)")
      }
    }
  }

  checkProjectLibraries(layout.projectLibrariesToUnpack.values(), "projectLibrariesToUnpack in $description", context)
  checkModules(layout.modulesWithExcludedModuleLibraries, "modulesWithExcludedModuleLibraries in $description", context)
}

private fun checkPluginDuplicates(nonTrivialPlugins: List<PluginLayout>, messages: BuildMessages) {
  val pluginsGroupedByMainModule = nonTrivialPlugins.groupBy { it.mainModule }.values
  for (duplicatedPlugins in pluginsGroupedByMainModule) {
    if (duplicatedPlugins.size > 1) {
      messages.warning("Duplicated plugin description in productLayout.allNonTrivialPlugins: ${duplicatedPlugins.first().mainModule}")
    }
  }
}

private fun checkModules(modules: Collection<String>?, fieldName: String, context: CompilationContext) {
  if (modules != null) {
    val unknownModules = modules.filter { context.findModule(it) == null }
    if (!unknownModules.isEmpty()) {
      context.messages.error("The following modules from $fieldName aren\'t found in the project: $unknownModules")
    }
  }
}

private fun checkArtifacts(names: Collection<String>, fieldName: String, context: CompilationContext) {
  val unknownArtifacts = names - JpsArtifactService.getInstance().getArtifacts(context.project).map { it.name }.toSet()
  if (!unknownArtifacts.isEmpty()) {
    context.messages.error("The following artifacts from $fieldName aren\'t found in the project: $unknownArtifacts")
  }
}

private fun checkPluginModules(pluginModules: List<String>?,
                               fieldName: String,
                               pluginLayoutList: List<PluginLayout>,
                               context: BuildContext) {
  if (pluginModules == null) {
    return
  }

  checkModules(pluginModules, fieldName, context)

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

private fun checkPaths(paths: Collection<String>, propertyName: String, messages: BuildMessages) {
  val nonExistingFiles = paths.filter { Files.notExists(Path.of(it)) }
  if (!nonExistingFiles.isEmpty()) {
    messages.error("$propertyName contains non-existing files: ${nonExistingFiles.joinToString()}")
  }
}

private fun checkPaths2(paths: Collection<Path>, propertyName: String, messages: BuildMessages) {
  val nonExistingFiles = paths.filter { Files.notExists(it) }
  if (!nonExistingFiles.isEmpty()) {
    messages.error("$propertyName contains non-existing files: ${nonExistingFiles.joinToString()}")
  }
}

private fun checkMandatoryField(value: String?, fieldName: String, messages: BuildMessages) {
  if (value == null) {
    messages.error("Mandatory property \'$fieldName\' is not specified")
  }
}

private fun checkMandatoryPath(path: String, fieldName: String, messages: BuildMessages) {
  checkMandatoryField(path, fieldName, messages)
  checkPaths(listOf(path), fieldName, messages)
}

private fun logFreeDiskSpace(phase: String, context: CompilationContext) {
  logFreeDiskSpace(context.messages, context.paths.buildOutputDir, phase)
}

internal fun logFreeDiskSpace(buildMessages: BuildMessages, dir: Path, phase: String) {
  buildMessages.debug(
    "Free disk space $phase: ${Formats.formatFileSize(Files.getFileStore(dir).usableSpace)} (on disk containing $dir)")
}

private fun doBuildUpdaterJar(artifactName: String, context: BuildContext) {
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

private fun buildCrossPlatformZip(distDirs: List<DistributionForOsTaskResult>, context: BuildContext): Path {
  val executableName = context.productProperties.baseFileName

  val productJson = generateMultiPlatformProductJson(
    "bin",
    context.getBuiltinModule(),
    listOf(
      ProductInfoLaunchData(os = OsFamily.WINDOWS.osName,
                            launcherPath = "bin/${executableName}.bat",
                            javaExecutablePath = null,
                            vmOptionsFilePath = "bin/win/${executableName}64.exe.vmoptions"),
      ProductInfoLaunchData(os = OsFamily.LINUX.osName,
                            launcherPath = "bin/${executableName}.sh",
                            javaExecutablePath = null,
                            vmOptionsFilePath = "bin/linux/${executableName}64.vmoptions",
                            startupWmClass = getLinuxFrameClass(context)),
      ProductInfoLaunchData(os = OsFamily.MACOS.osName,
                            launcherPath = "MacOS/$executableName",
                            javaExecutablePath = null,
                            vmOptionsFilePath = "bin/mac/${executableName}.vmoptions")
  ), context)

  val zipFileName = context.productProperties.getCrossPlatformZipFileName(context.applicationInfo, context.buildNumber)
  val targetFile = context.paths.artifactDir.resolve(zipFileName)

  crossPlatformZip(
    macX64DistDir = distDirs.first { it.os == OsFamily.MACOS && it.arch == JvmArchitecture.x64 }.outDir,
    macAarch64DistDir = distDirs.first { it.os == OsFamily.MACOS && it.arch == JvmArchitecture.aarch64 }.outDir,
    linuxX64DistDir = distDirs.first { it.os == OsFamily.LINUX && it.arch == JvmArchitecture.x64 }.outDir,
    winX64DistDir = distDirs.first { it.os == OsFamily.WINDOWS && it.arch == JvmArchitecture.x64 }.outDir,
    targetFile = targetFile,
    executableName = executableName,
    productJson = productJson.encodeToByteArray(),
    macExtraExecutables = context.macDistributionCustomizer!!.extraExecutables,
    linuxExtraExecutables = context.linuxDistributionCustomizer!!.extraExecutables,
    distFiles = context.getDistFiles(),
    extraFiles = mapOf("dependencies.txt" to context.dependenciesProperties.file),
    distAllDir = context.paths.distAllDir,
  )

  checkInArchive(context, targetFile, "")
  context.notifyArtifactBuilt(targetFile)

  checkClassVersion(targetFile, context)
  return targetFile
}

private fun checkClassVersion( targetFile: Path, context: BuildContext) {
  val checkerConfig = context.productProperties.versionCheckerConfig ?: return
  if (!context.options.buildStepsToSkip.contains(BuildOptions.VERIFY_CLASS_FILE_VERSIONS)) {
    ClassVersionChecker.checkVersions(checkerConfig, context.messages, targetFile)
  }
}

fun getOsDistributionBuilder(os: OsFamily, ideaProperties: Path? = null, context: BuildContext): OsSpecificDistributionBuilder {
  return when (os) {
    OsFamily.WINDOWS -> WindowsDistributionBuilder(context = context,
                                                   customizer = context.windowsDistributionCustomizer!!,
                                                   ideaProperties = ideaProperties,
                                                   patchedApplicationInfo = context.applicationInfo.toString())
    OsFamily.LINUX -> LinuxDistributionBuilder(context = context,
                                               customizer = context.linuxDistributionCustomizer!!,
                                               ideaProperties = ideaProperties)
    OsFamily.MACOS -> MacDistributionBuilder(context, context.macDistributionCustomizer, ideaProperties)
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
                     macAarch64DistDir: Path,
                     linuxX64DistDir: Path,
                     winX64DistDir: Path,
                     targetFile: Path,
                     executableName: String,
                     productJson: ByteArray,
                     macExtraExecutables: List<String>,
                     linuxExtraExecutables: List<String>,
                     distFiles: Collection<Map.Entry<Path, String>>,
                     extraFiles: Map<String, Path>,
                     distAllDir: Path) {
  writeNewFile(targetFile) { outFileChannel ->
    NoDuplicateZipArchiveOutputStream(outFileChannel).use { out ->
      out.setUseZip64(Zip64Mode.Never)

      out.entryToDir(winX64DistDir.resolve("bin/idea.properties"), "bin/win")
      out.entryToDir(linuxX64DistDir.resolve("bin/idea.properties"), "bin/linux")
      out.entryToDir(macX64DistDir.resolve("bin/idea.properties"), "bin/mac")

      out.entryToDir(macX64DistDir.resolve("bin/${executableName}.vmoptions"), "bin/mac")
      out.entry("bin/mac/${executableName}64.vmoptions", macX64DistDir.resolve("bin/${executableName}.vmoptions"))

      extraFiles.forEach(BiConsumer { p, f ->
        out.entry(p, f)
      })

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
          val path = file.toString()
          if (path.endsWith(".vmoptions")) {
            out.entryToDir(file, "bin/linux")
          }
          else if (path.endsWith(".sh") || path.endsWith(".py")) {
            out.entry("bin/${file.fileName}", file, unixMode = executableFileUnixMode)
          }
          else {
            val fileName = file.fileName.toString()
            if (fileName.startsWith("fsnotifier")) {
              out.entry("bin/linux/$fileName", file, unixMode = executableFileUnixMode)
            }
          }
        }
      }

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

      val extraExecutablesSet = java.util.Set.copyOf(macExtraExecutables + linuxExtraExecutables)
      val entryCustomizer: EntryCustomizer = { entry, _, relativeFile ->
        if (extraExecutablesSet.contains(relativeFile.toString())) {
          entry.unixMode = executableFileUnixMode
        }
      }

      out.dir(startDir = distAllDir, prefix = "", fileFilter = { _, relativeFile ->
        relativeFile.toString() != "bin/idea.properties"
      }, entryCustomizer = entryCustomizer)

      val zipFiles = mutableMapOf<String, Path>()
      out.dir(startDir = macX64DistDir, prefix = "", fileFilter = { _, relativeFile ->
        val p = relativeFile.toString().replace('\\', '/')
        !p.startsWith("bin/fsnotifier") &&
        !p.startsWith("bin/repair") &&
        !p.startsWith("bin/restarter") &&
        !p.startsWith("bin/printenv") &&
        p != "bin/idea.properties" &&
        !(p.startsWith("bin/") && (p.endsWith(".sh") || p.endsWith(".vmoptions"))) &&
        // do not copy common files, error if they are different
        filterFileIfAlreadyInZip(p, macX64DistDir.resolve(p), zipFiles)
      }, entryCustomizer = entryCustomizer)

      out.dir(startDir = macAarch64DistDir, prefix = "", fileFilter = { _, relativeFile ->
        val p = relativeFile.toString().replace('\\', '/')
        !p.startsWith("bin/fsnotifier") &&
        !p.startsWith("bin/repair") &&
        !p.startsWith("bin/restarter") &&
        !p.startsWith("bin/printenv") &&
        p != "bin/idea.properties" &&
        !(p.startsWith("bin/") && (p.endsWith(".sh") || p.endsWith(".vmoptions"))) &&
        // do not copy common files, error if they are different
        filterFileIfAlreadyInZip(p, macAarch64DistDir.resolve(p), zipFiles)
      }, entryCustomizer = entryCustomizer)

      out.dir(startDir = linuxX64DistDir, prefix = "", fileFilter = { _, relativeFile ->
        val p = relativeFile.toString().replace('\\', '/')
        !p.startsWith("bin/fsnotifier") &&
        !p.startsWith("bin/repair") &&
        !p.startsWith("bin/printenv") &&
        !p.startsWith("help/") &&
        p != "bin/idea.properties" &&
        !(p.startsWith("bin/") && (p.endsWith(".sh") || p.endsWith(".vmoptions") || p.endsWith(".py"))) &&
        // do not copy common files, error if they are different
        filterFileIfAlreadyInZip(p, linuxX64DistDir.resolve(p), zipFiles)
      }, entryCustomizer = entryCustomizer)

      val winExcludes = distFiles.mapTo(HashSet(distFiles.size)) { "${it.value}/${it.key.fileName}" }
      out.dir(startDir = winX64DistDir, prefix = "", fileFilter = { _, relativeFile ->
        val p = relativeFile.toString().replace('\\', '/')
        !p.startsWith("bin/fsnotifier") &&
        !p.startsWith("bin/repair") &&
        !p.startsWith("bin/printenv") &&
        !p.startsWith("help/") &&
        p != "bin/idea.properties" &&
        p != "build.txt" &&
        !(p.startsWith("bin/") && p.endsWith(".exe.vmoptions")) &&
        !(p.startsWith("bin/$executableName") && p.endsWith(".exe")) &&
        !winExcludes.contains(p) &&
        // do not copy common files, error if they are different
        filterFileIfAlreadyInZip(p, winX64DistDir.resolve(p), zipFiles)
      }, entryCustomizer = entryCustomizer)
    }
  }
}