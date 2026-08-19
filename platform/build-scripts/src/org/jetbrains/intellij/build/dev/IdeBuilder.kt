// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.HashFunnel
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import io.opentelemetry.api.trace.Tracer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.ScrambleTool
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.classPath.generateClassPathByLayoutReport
import org.jetbrains.intellij.build.classPath.generateCoreClasspathFromPlugins
import org.jetbrains.intellij.build.classPath.generatePluginClassPath
import org.jetbrains.intellij.build.classPath.generatePluginClassPathFromPrebuiltPluginFiles
import org.jetbrains.intellij.build.classPath.writePluginClassPathCount
import org.jetbrains.intellij.build.classPath.writePluginClassPathPrefix
import org.jetbrains.intellij.build.getDevModeOrTestBuildDateInSeconds
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PLUGIN_CLASSPATH
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.collectAllPluginClasspathDirs
import org.jetbrains.intellij.build.impl.collectCoScrambleEntries
import org.jetbrains.intellij.build.impl.copyDistFiles
import org.jetbrains.intellij.build.impl.createDevBuildCompilationContext
import org.jetbrains.intellij.build.impl.createIdeaPropertyFile
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.jetbrains.intellij.build.impl.OsSpecificDistributionBuilder
import org.jetbrains.intellij.build.impl.getOsDistributionBuilder
import org.jetbrains.intellij.build.impl.isDevBuildBazelBacked
import org.jetbrains.intellij.build.impl.layoutPlatformDistribution
import org.jetbrains.intellij.build.impl.moduleRepository.generateRuntimeModuleRepositoryForDevBuild
import org.jetbrains.intellij.build.impl.normalizeCompilationContextForBuild
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.impl.projectStructureMapping.ContentReport
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.jarCache.LocalDiskJarCacheManager
import org.jetbrains.intellij.build.jarCache.NonCachingJarCacheManager
import org.jetbrains.intellij.build.normalizeCompiledClassesOptions
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.readSearchableOptionIndex
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.Long
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.Boolean
import kotlin.Int
import kotlin.OptIn
import kotlin.RuntimeException
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.also
import kotlin.checkNotNull
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.moveTo
import kotlin.io.path.relativeTo
import kotlin.let
import kotlin.text.buildString
import kotlin.text.removePrefix
import kotlin.text.take
import kotlin.text.takeLast
import kotlin.text.toBoolean
import kotlin.time.Duration.Companion.hours

private const val maxWindowsPathLengthForIDERootToBeAbleToRunRiderBackend: Int = 64

sealed interface DevBuildOutput {
  data object Complete : DevBuildOutput

  data class Component(
    @JvmField val fragment: DevBuildFragment,
    @JvmField val manifestFile: Path,
    @JvmField val pluginClasspathPartFile: Path? = null,
    @JvmField val pluginClasspathPrefixFile: Path? = null,
  ) : DevBuildOutput {
    init {
      require(!fragment.isComplete) { "A complete dev distribution must use DevBuildOutput.Complete" }
      require(!fragment.ownsPlugins || pluginClasspathPartFile != null) {
        "The '$fragment' fragment owns plugins, so it requires a plugin-classpath part file"
      }
    }
  }
}

data class BuildRequest(
  @JvmField val platformPrefix: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val projectDir: Path,
  /** For a standalone frontend distribution where `platformPrefix` is "JetBrainsClient", specifies the platform prefix of its base IDE. */
  @JvmField val baseIdePlatformPrefixForFrontend: String? = null,
  @JvmField val devRootDir: Path = System.getProperty("idea.dev.root.dir")?.let { Path.of(it).normalize().toAbsolutePath() } ?: projectDir.resolve("out/dev-run"),
  /**
   * Where composed jars are cached between assemblies, or `null` to compose every jar afresh.
   * A Bazel action passes `null`: its own declared output is the cache, so a local disk cache would only add a second copy
   * of every jar and a directory that parallel assemblies mutate while [org.jetbrains.intellij.build.jarCache.LocalDiskJarCacheManager.cleanup]
   * prunes it.
   */
  @JvmField val jarCacheDir: Path? = devRootDir.resolve("jar-cache"),
  @JvmField val classesOutputDirectory: Path? = null,
  @JvmField val keepHttpClient: Boolean = true,
  @JvmField val platformClassPathConsumer: ((mainClass: String, classPath: Set<Path>, runDir: Path) -> Unit)? = null,
  /**
   * If `true`, the dev build will include a [runtime module repository](psi_element://com.intellij.platform.runtime.repository).
   * It is currently used only to run an instance of JetBrains Client from IDE's installation,
   * and its generation makes the project build a little longer, so it should be enabled only if needed.
   */
  @JvmField val generateRuntimeModuleRepository: Boolean = false,

  @JvmField val isUnpackedDist: Boolean = System.getProperty("idea.dev.build.unpacked").toBoolean(),
  @JvmField val scrambleTool: ScrambleTool? = null,

  @JvmField val writeCoreClasspath: Boolean = true,

  @JvmField val tracer: Tracer? = null,

  @JvmField val os: OsFamily = OsFamily.currentOs,
  @JvmField val arch: JvmArchitecture = JvmArchitecture.currentJvmArch,

  @JvmField val isBootClassPathCorrect: Boolean = false,
  @JvmField val devRunDirPrefix: String = System.getProperty("idea.dev.build.dir.prefix") ?: "",

  /**
   * If set, used verbatim as the run directory, skipping both the name derived from [platformPrefix] and [additionalModules]
   * and the wipe of stale content. Must therefore be absent or empty - nothing clears it, and assembling on top of a previous
   * distribution would produce a directory that is neither the old one nor the new one.
   * A Bazel action writes into a directory whose name `declare_directory` fixes at analysis time, and that directory is handed over empty.
   */
  @JvmField val runDirOverride: Path? = null,

  /**
   * If set, `temp`, `artifacts` and `log` are rooted here instead of in the run directory.
   * A `TreeArtifact` must contain only the assembled distribution - build scratch (`temp` alone is ~200 MB) is not part of the output.
   * `temp` and `artifacts` are cleared at the start of every build, as they were while they lived in the run directory; `log` is kept.
   */
  @JvmField val scratchDir: Path? = null,

  /**
   * Build date stamped into the distribution, in seconds since the epoch; `null` means [getDevModeOrTestBuildDateInSeconds].
   * Wall clock is not an action input, so a Bazel action pins the date to keep its output reproducible.
   */
  // `Long` is `java.lang.Long` in this file
  @JvmField val buildDateInSeconds: kotlin.Long? = null,

  /** Complete distribution, or one fully specified independently cacheable component. */
  @JvmField val output: DevBuildOutput = DevBuildOutput.Complete,
) {
  internal val fragment: DevBuildFragment
    get() = (output as? DevBuildOutput.Component)?.fragment ?: DevBuildFragment.COMPLETE

  internal val componentOutput: DevBuildOutput.Component?
    get() = output as? DevBuildOutput.Component

  override fun toString(): String {
    return buildString {
      append("DevBuildRequest(platformPrefix='$platformPrefix', ")
      if (baseIdePlatformPrefixForFrontend != null) {
        append("baseIdePlatformPrefixForFrontend='$baseIdePlatformPrefixForFrontend', ")
      }
      append("additionalModules=$additionalModules, ")
      if (classesOutputDirectory != null) {
        append("classesOutputDirectory=$classesOutputDirectory, ")
      }
      append("keepHttpClient=$keepHttpClient, ")
      append("generateRuntimeModuleRepository=$generateRuntimeModuleRepository")
    }
  }
}

private fun defaultClassesOutputDirectory(projectDir: Path): Path {
  return System.getenv("CLASSES_DIR")?.let { Path.of(it).normalize().toAbsolutePath().parent } ?: projectDir.resolve("out/classes")
}

internal fun resolveProjectClassesOutputDirectory(request: BuildRequest, buildOptionsTemplate: BuildOptions): Path {
  return request.classesOutputDirectory ?: buildOptionsTemplate.classOutDir?.let { Path.of(it) } ?: defaultClassesOutputDirectory(request.projectDir)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun buildProductFromProject(
  request: BuildRequest,
  productConfiguration: ProductConfiguration,
  buildOptionsTemplate: BuildOptions,
): Path {
  return buildProduct(request = request) { buildDir ->
    createBuildContextFromProject(
      productConfiguration = productConfiguration,
      request = request,
      buildDir = buildDir,
      buildOptionsTemplate = buildOptionsTemplate,
      scope = this,
    )
  }
}

internal suspend fun buildProduct(request: BuildRequest, createBuildContext: suspend CoroutineScope.(buildDir: Path) -> BuildContext): Path {
  check(request.fragment.isComplete || request.scrambleTool == null) {
    "Split dev distribution assembly does not support scrambling"
  }
  val buildDir = request.runDirOverride?.let { prepareOverriddenRunDir(it) } ?: prepareDevRunDir(request)
  request.scratchDir?.let { prepareScratchDir(it) }

  val runDir = buildDir
  var contextToClose: BuildContext? = null
  try {
    coroutineScope {
      val context = createBuildContext(buildDir)
      contextToClose = context
      // Must precede layout: dev mode uses cache payload paths directly on the classpath,
      // so a concurrent cleanup can delete a payload after layout captures its path,
      // crashing the JVM at the first class lookup into that jar.
      withContext(Dispatchers.IO + CoroutineName("cleanup jar cache")) {
        context.cleanupJarCache()
      }
      configureTargetPlatform(context.options, request)

      val moduleOutputPatcher = ModuleOutputPatcher()

      val platformLayout = if (request.fragment.ownsPlatformJars || request.fragment.ownsPlugins || request.fragment.platformResources) {
        async(CoroutineName("create platform layout")) {
          spanBuilder("create platform layout").use {
            createPlatformLayout(context)
          }
        }
      }
      else null

      val searchableOptionSetDeferred = if (request.fragment.ownsPlatformJars || request.fragment.ownsPlugins) {
        async(CoroutineName("read searchable options")) {
          getSearchableOptionSet(context)
        }
      }
      else null

      val platformResourcesJob = if (request.fragment.platformResources) {
        launch(Dispatchers.IO + CoroutineName("layout platform resources")) {
          // Product metadata, like `bin/product-info.json` below - and so owned by this fragment alone. It used to be
          // written while laying out the platform jars, which every platform fragment does, and each of them then
          // claimed the same file.
          Files.writeString(runDir.resolve("build.txt"), context.fullBuildNumber)

          // PathManager.getBinPath() is used as a working dir for maven
          val binDir = Files.createDirectories(runDir.resolve("bin"))
          val oldFiles = Files.newDirectoryStream(binDir).use { it.toCollection(HashSet()) }

          val libcImpl = LibcImpl.current(request.os)

          val osDistributionBuilder = getOsDistributionBuilder(os = request.os, libcImpl = libcImpl, context = context)
          if (osDistributionBuilder != null) {
            oldFiles.remove(osDistributionBuilder.writeVmOptions(binDir))
            // the file cannot be placed right into the distribution as it throws off home dir detection in `PathManager#getHomeDirFor`
            val productInfoDir = context.paths.tempDir.resolve("product-info").createDirectories()
            val productInfoFile = osDistributionBuilder.writeProductInfoFile(productInfoDir, request.arch)
            oldFiles.remove(productInfoFile.moveTo(binDir.resolve(PRODUCT_INFO_FILE_NAME), overwrite = true))
            NioFiles.deleteRecursively(productInfoDir)
            oldFiles.removeAll(layOutNativeBinFiles(osDistributionBuilder, binDir, runDir, request.arch))
          }

          val ideaPropertyFile = binDir.resolve(PathManager.PROPERTIES_FILE_NAME)
          Files.writeString(ideaPropertyFile, createIdeaPropertyFile(context))
          oldFiles.remove(ideaPropertyFile)

          for (oldFile in oldFiles) {
            NioFiles.deleteRecursively(oldFile)
          }
        }
      }
      else null

      val platformLayoutResultDeferred: Deferred<PlatformLayoutResult> = if (!request.fragment.ownsPlatformJars) {
        CompletableDeferred(PlatformLayoutResult(distributionEntries = emptyList(), coreClassPath = emptySet()))
      }
      else async(CoroutineName("platform distribution entries")) {
        val platformLayoutAwaited = checkNotNull(platformLayout).await()
        spanBuilder("layout platform").use {
          layoutPlatform(
            runDir = runDir,
            platformLayout = platformLayoutAwaited,
            searchableOptionSet = checkNotNull(searchableOptionSetDeferred).await(),
            context = context,
            moduleOutputPatcher = moduleOutputPatcher,
            request = request,
          )
        }
      }

      val pluginLayouts = if (request.fragment.ownsPlugins) devModePluginCandidates(request, context) else emptyList()
      val layoutsOfPluginsToScramble = collectLayoutsOfPluginsToScramble(pluginLayouts)
      val pluginBuildStrategy = if (request.fragment.ownsPlugins) {
        selectDevModePluginBuildStrategy(request = request, context = context, pluginLayouts = pluginLayouts)
      }
      else DevModePluginBuildStrategy.NORMAL
      val pluginsBuildResultsDeferred = if (pluginBuildStrategy == DevModePluginBuildStrategy.LAYOUT_BEFORE_PLATFORM_SCRAMBLE) {
        // Lay out ALL plugins early (no scrambling). The platform ZKM run below scrambles
        // co-scramble plugin jars in the same call and needs every plugin's lib/modules on its
        // scramble classpath; per-plugin scramble runs after platform scramble.
        async(CoroutineName("lay out plugins")) {
          layoutAllPluginsForDevMode(
            request = request,
            pluginLayouts = pluginLayouts,
            context = context,
            runDir = runDir,
            platformLayout = checkNotNull(platformLayout),
            searchableOptionSet = checkNotNull(searchableOptionSetDeferred).await(),
          )
        }
      }
      else {
        null
      }

      val platformScrambleResultDeferred: Deferred<PlatformLayoutResult> = if (!request.fragment.ownsPlatformJars) {
        platformLayoutResultDeferred
      }
      else async(CoroutineName("scramble platform")) {
        val platformLayoutResult = platformLayoutResultDeferred.await()
        if (context.productProperties.scrambleMainJar) {
          request.scrambleTool?.let { scrambleTool ->
            spanBuilder("scramble platform").use {
              val buildResults = pluginsBuildResultsDeferred?.await().orEmpty()
              val coScrambleEntries = if (buildResults.isEmpty()) emptyList() else collectCoScrambleEntries(buildResults, layoutsOfPluginsToScramble = layoutsOfPluginsToScramble)
              scrambleTool.scramble(
                platformLayout = checkNotNull(platformLayout).await(),
                platformContent = platformLayoutResult.distributionEntries,
                coScrambleEntries = coScrambleEntries,
                // Skip the per-plugin lib/ walk when nothing opts in — pure platform scramble doesn't
                // need cross-plugin classpath.
                classpathDirs = if (coScrambleEntries.isEmpty()) emptyList() else collectAllPluginClasspathDirs(buildResults),
                context = context,
              )
            }
          }
        }
        platformLayoutResult
      }

      val pluginDistributionEntriesDeferred: Deferred<PluginsLayoutResult> = if (!request.fragment.ownsPlugins) {
        CompletableDeferred(PluginsLayoutResult(pluginEntries = emptyList(), additionalPlugins = null))
      }
      else async(CoroutineName("scramble plugins")) {
        if (pluginBuildStrategy == DevModePluginBuildStrategy.LAYOUT_BEFORE_PLATFORM_SCRAMBLE) {
          scrambleAlreadyLaidOutPluginsForDevMode(
            request = request,
            descriptors = checkNotNull(pluginsBuildResultsDeferred).await(),
            context = context,
            runDir = runDir,
            platformLayout = checkNotNull(platformLayout),
            layoutsOfPluginsToScramble = layoutsOfPluginsToScramble,
            platformEntriesProvider = { platformScrambleResultDeferred.await().distributionEntries },
          )
        }
        else {
          buildPluginsForDevMode(
            request = request,
            pluginLayouts = pluginLayouts,
            context = context,
            runDir = runDir,
            platformLayout = checkNotNull(platformLayout),
            searchableOptionSet = checkNotNull(searchableOptionSetDeferred).await(),
          ) { platformScrambleResultDeferred.await().distributionEntries }
        }
      }

      val coreClassPathDeferred = async(CoroutineName("compute core classpath")) {
        val platformClasspath = platformLayoutResultDeferred.await().coreClassPath
        val coreClasspathFromPlugins = if (request.fragment.ownsPlugins) {
          generateCoreClasspathFromPlugins(
            platformLayout = checkNotNull(platformLayout).await(),
            pluginBuildResults = pluginDistributionEntriesDeferred.await().pluginEntries,
            context = context,
          )
        }
        else emptyList()
        platformClasspath + coreClasspathFromPlugins
      }

      // Write and publish the one classpath computation shared with the component manifest below.
      launch(CoroutineName("publish core classpath")) {
        val classPath = coreClassPathDeferred.await()
        if (request.writeCoreClasspath && request.fragment.isComplete) {
          val classPathString = formatCoreClasspath(classPath = classPath, runDir = runDir)
          launch(Dispatchers.IO) {
            Files.writeString(runDir.resolve("core-classpath.txt"), classPathString)
          }
        }

        request.platformClassPathConsumer?.invoke(context.ideMainClassName, classPath, runDir)
      }

      val postProcessJob = launch(CoroutineName("post-process distribution")) {
        // ensure platform dist files added to the list
        val platformFileEntries = platformScrambleResultDeferred.await().distributionEntries
        // ensure plugin dist files added to the list
        val pluginDistributionEntries = pluginDistributionEntriesDeferred.await()
        val platformLayoutAwaited = platformLayout?.await()

        val pluginClasspathJob = if (request.fragment.ownsPlugins) launch {
          val (pluginEntries, additionalEntries) = pluginDistributionEntries
          val requiredPlatformLayout = checkNotNull(platformLayoutAwaited)
          val cachedDescriptorContainer = requiredPlatformLayout.descriptorCacheContainer
          spanBuilder("generate plugin classpath").use(Dispatchers.IO) {
            val mainData = generatePluginClassPath(
              pluginEntries = pluginEntries,
              descriptorFileProvider = cachedDescriptorContainer,
              platformLayout = requiredPlatformLayout,
              layoutsOfPluginsToScramble = layoutsOfPluginsToScramble,
              context = context,
            )
            val additionalData = additionalEntries?.let { generatePluginClassPathFromPrebuiltPluginFiles(it) }

            val byteOut = ByteArrayOutputStream()
            val out = DataOutputStream(byteOut)
            if (request.fragment.isComplete) {
              writePluginClassPathPrefix(
                out = out,
                isJarOnly = !request.isUnpackedDist,
                platformLayout = requiredPlatformLayout,
                descriptorCacheContainer = cachedDescriptorContainer,
                context = context
              )
              writePluginClassPathCount(out = out, pluginCount = pluginEntries.size + (additionalEntries?.size ?: 0))
            }
            out.write(mainData)
            additionalData?.let { out.write(it) }
            out.close()
            val target = if (request.fragment.isComplete) {
              runDir.resolve(PLUGIN_CLASSPATH)
            }
            else {
              checkNotNull(request.componentOutput).pluginClasspathPartFile!!
            }
            target.parent?.createDirectories()
            Files.write(target, byteOut.toByteArray())
          }
        }
        else null

        request.componentOutput?.pluginClasspathPrefixFile?.let { prefixFile ->
          launch(CoroutineName("write plugin classpath prefix")) {
            val requiredPlatformLayout = checkNotNull(platformLayoutAwaited) {
              "The '${request.fragment}' fragment must lay out the platform to describe the product"
            }
            val byteOut = ByteArrayOutputStream()
            DataOutputStream(byteOut).use { out ->
              writePluginClassPathPrefix(
                out = out,
                isJarOnly = !request.isUnpackedDist,
                platformLayout = requiredPlatformLayout,
                descriptorCacheContainer = requiredPlatformLayout.descriptorCacheContainer,
                context = context,
              )
            }
            withContext(Dispatchers.IO) {
              prefixFile.parent?.createDirectories()
              Files.write(prefixFile, byteOut.toByteArray())
            }
          }
        }

        if (context.generateRuntimeModuleRepository && request.fragment.isComplete) {
          launch(CoroutineName("generate runtime repository")) {
            val contentReport = ContentReport(
              platform = platformFileEntries,
              bundledPlugins = pluginDistributionEntries.pluginEntries,
              nonBundledPlugins = emptyList()
            )
            checkNotNull(pluginClasspathJob).join() //this is necessary to have full data in DescriptorCacheContainer

            spanBuilder("generate runtime repository").use(Dispatchers.IO) {
              generateRuntimeModuleRepositoryForDevBuild(
                contentReport = contentReport,
                targetDirectory = runDir,
                context = context,
                platformLayout = checkNotNull(platformLayoutAwaited),
              )
            }
          }
        }

        if (request.fragment.platformResources) {
          checkNotNull(platformResourcesJob).join()
          withContext(Dispatchers.IO) {
            context.productProperties.copyAdditionalOsSpecificFiles(
              runDir = runDir,
              os = request.os,
              arch = request.arch,
              context = context
            )
          }
        }

        // A platform layout registers IJent as DistFiles. Platform and plugin fragments need that layout for descriptor
        // resolution, but the bytes have one owner: platform_resources, which creates the layout specifically to run
        // platform specs and copy those files. Other DistFiles remain with the fragment that produced them.
        withContext(Dispatchers.IO) {
          copyDistFiles(
            newDir = runDir,
            os = request.os,
            arch = request.arch,
            libcImpl = LibcImpl.current(request.os),
            context = context,
            include = { shouldCopyDevBuildDistFile(fragment = request.fragment, relativePath = it.relativePath) },
          )
        }
      }

      launch(CoroutineName("compute IDE fingerprint")) {
        // The component manifest inventories the finished tree, including DistFiles and semantic archive links. It
        // must therefore run after every post-processing child has completed, not merely after jars were laid out.
        postProcessJob.join()
        if (request.fragment.isComplete) {
          computeIdeFingerprint(
            platformDistributionEntriesDeferred = platformLayoutResultDeferred,
            pluginDistributionEntriesDeferred = pluginDistributionEntriesDeferred,
            runDir = runDir,
            projectDir = request.projectDir,
          )
        }
        else {
          val pluginsResult = pluginDistributionEntriesDeferred.await()
          withContext(Dispatchers.IO) {
            writeDevBuildComponentManifest(
              file = checkNotNull(request.componentOutput).manifestFile,
              kind = request.fragment.name,
              platformPrefix = request.platformPrefix,
              os = request.os,
              arch = request.arch,
              additionalModules = if (request.fragment.ownsPlugins) request.additionalModules else emptyList(),
              mainClass = context.ideMainClassName,
              coreClassPath = coreClassPathDeferred.await(),
              pluginCount = pluginsResult.pluginEntries.size + (pluginsResult.additionalPlugins?.size ?: 0),
              componentRoot = runDir,
            )
          }
        }
      }
    }
  }
  finally {
    // close debug logging to prevent locking of the output directory on Windows
    contextToClose?.messages?.close()
  }
  return runDir
}

@VisibleForTesting
internal fun shouldCopyDevBuildDistFile(fragment: DevBuildFragment, relativePath: String): Boolean {
  return fragment.platformResources || !relativePath.startsWith("lib/ijent/")
}

@VisibleForTesting
internal fun configureTargetPlatform(options: BuildOptions, request: BuildRequest) {
  options.targetOs = persistentListOf(request.os)
  options.targetArch = request.arch
}

/**
 * Clears the throwaway parts of a caller-supplied scratch directory ([BuildRequest.scratchDir]).
 *
 * While `temp` and `artifacts` lived in the run directory, [prepareDevRunDir] wiped them on every build. Rooted outside it they have
 * no such owner, and code that extracts a file into `temp` fails on the second build with `FileAlreadyExistsException`.
 * `log` is kept, exactly as [prepareDevRunDir] keeps it.
 */
internal suspend fun prepareScratchDir(scratchDir: Path) {
  withContext(Dispatchers.IO) {
    NioFiles.deleteRecursively(scratchDir.resolve("temp"))
    NioFiles.deleteRecursively(scratchDir.resolve("artifacts"))
  }
}

/**
 * Prepares a caller-owned run directory ([BuildRequest.runDirOverride]).
 *
 * Unlike [prepareDevRunDir] this never deletes anything: a caller that names the directory itself owns its lifecycle, and a
 * silent wipe of a mistyped path would be worse than a failure. It only insists that the directory starts out empty.
 */
internal suspend fun prepareOverriddenRunDir(runDir: Path): Path {
  return withContext(Dispatchers.IO) {
    val staleEntries = try {
      Files.newDirectoryStream(runDir).use { stream -> stream.take(5).map { it.fileName.toString() } }
    }
    catch (_: NoSuchFileException) {
      emptyList()
    }

    check(staleEntries.isEmpty()) {
      "a run directory override must be empty, but $runDir already contains ${staleEntries.joinToString()};" +
      " delete it first (the standalone assembler has --clean-output for that)"
    }
    Files.createDirectories(runDir)
  }
}

/** Derives the run directory name from the request and clears stale content from it. */
private suspend fun prepareDevRunDir(request: BuildRequest): Path {
  val rootDir = withContext(Dispatchers.IO) {
    val rootDir = request.devRootDir
    // if symlinked to RAM disk, use a real path for performance reasons and avoid any issues in ant/other code
    if (Files.exists(rootDir)) {
      // toRealPath must be called only on an existing file
      rootDir.toRealPath()
    }
    else {
      rootDir
    }
  }

  val classifier = computeAdditionalModulesFingerprint(request.additionalModules)
  val productDirNameWithoutClassifier = when (request.platformPrefix) {
    "Idea" -> "idea-community"
    "JetBrainsClient" -> "${request.baseIdePlatformPrefixForFrontend ?: ""}${request.platformPrefix}"
    else -> request.platformPrefix
  }
  val productDirSuffix = when {
    System.getProperty("intellij.build.minimal").toBoolean() -> "-ij-void"
    request.scrambleTool != null -> "-scrambled"
    else -> ""
  }
  // Keep the product-identifying head (dev-run prefix + product name + suffix) intact, and spend the remaining
  // path-length budget on the classifier, truncating it from the front so its discriminative xxh3 hash tail survives.
  // Truncating the whole string with `takeLast` used to drop the product prefix from the front whenever
  // `prefix + classifier` exceeded the limit, so different products could collide into the same `out/dev-run` directory.
  val productDirNameHead = request.devRunDirPrefix + productDirNameWithoutClassifier + productDirSuffix
  val classifierBudget = (maxWindowsPathLengthForIDERootToBeAbleToRunRiderBackend - productDirNameHead.length).coerceAtLeast(0)
  val productDirName = productDirNameHead + classifier.takeLast(classifierBudget)

  return withContext(Dispatchers.IO.limitedParallelism(4)) {
    val buildDir = rootDir.resolve(productDirName)
    // on start, delete everything to avoid stale data
    val files = try {
      Files.newDirectoryStream(buildDir).toList()
    }
    catch (_: NoSuchFileException) {
      Files.createDirectories(buildDir)
      return@withContext buildDir
    }

    for (child in files) {
      val fileName = child.fileName.toString()
      if (fileName != "log" && fileName != "bin") {
        launch {
          NioFiles.deleteRecursively(child)
        }
      }
    }
    buildDir
  }
}

/**
 * Copies the distribution's `bin` natives and marks executable the ones a production build would.
 *
 * A production distribution gets those permissions when it is archived - `updateExecutablePermissions` over
 * [OsSpecificDistributionBuilder.generateExecutableFilesPatterns]. A dev distribution is never archived, and its
 * sources do not always carry the mode: inside a Bazel action the checkout is a tree that
 * [materializeProjectModelTree] laid out, and it copies without POSIX attributes, so a `755` file in git arrives
 * here as `rw-`. The same patterns therefore decide here, applied to the copied files alone - walking the whole
 * distribution would rewrite the permissions of every jar in it, per assembly.
 *
 * Returns the copied files, which the caller must keep out of the sweep that deletes whatever else is in `bin`.
 */
private suspend fun layOutNativeBinFiles(
  osDistributionBuilder: OsSpecificDistributionBuilder,
  binDir: Path,
  runDir: Path,
  arch: JvmArchitecture,
): List<Path> {
  val copied = osDistributionBuilder.copyNativeBinFiles(binDir, arch)
  val executableMatchers = osDistributionBuilder.generateExecutableFilesMatchers(includeRuntime = false, arch = arch).keys
  for (file in copied) {
    val relativePath = runDir.relativize(file)
    if (executableMatchers.any { it.matches(relativePath) }) {
      NioFiles.setExecutable(file)
    }
  }
  return copied
}

// paths are written relative to the IDE home dir to keep the built IDE relocatable;
// entries outside of the home dir (e.g., jar cache payload) stay absolute - a `..`-prefixed path would break relocation
internal fun formatCoreClasspath(classPath: Collection<Path>, runDir: Path): String {
  return classPath.joinToString(separator = "\n") {
    if (it.startsWith(runDir)) it.relativeTo(runDir).invariantSeparatorsPathString else it.invariantSeparatorsPathString
  }
}

private suspend fun computeIdeFingerprint(
  platformDistributionEntriesDeferred: Deferred<PlatformLayoutResult>,
  pluginDistributionEntriesDeferred: Deferred<PluginsLayoutResult>,
  runDir: Path,
  projectDir: Path,
) {
  val entries = platformDistributionEntriesDeferred.await().distributionEntries.asSequence() +
                pluginDistributionEntriesDeferred.await().pluginEntries.asSequence().flatMap { it.distribution.asSequence() }
  writeIdeFingerprint(entries = entries, runDir = runDir, projectDir = projectDir)
}

private suspend fun getSearchableOptionSet(context: CompilationContext): SearchableOptionSetDescriptor? {
  return withContext(Dispatchers.IO) {
    try {
      readSearchableOptionIndex(context.paths.searchableOptionDir)
    }
    catch (_: NoSuchFileException) {
      null
    }
  }
}

private suspend fun createBuildContextFromProject(
  productConfiguration: ProductConfiguration,
  request: BuildRequest,
  buildDir: Path,
  buildOptionsTemplate: BuildOptions,
  scope: CoroutineScope,
): BuildContext {
  val options = createProjectDevBuildOptions(request = request, buildDir = buildDir, buildOptionsTemplate = buildOptionsTemplate)

  val buildPaths = createDevBuildPaths(
    projectDir = request.projectDir,
    buildDir = buildDir,
    logDir = options.logDir!!,
    scratchDir = request.scratchDir ?: buildDir,
  )
  val compilationContext = normalizeCompilationContextForBuild(
    context = createDevBuildCompilationContext(
      projectHome = request.projectDir,
      buildOutputRootEvaluator = { _ -> buildDir },
      options = options,
      customBuildPaths = buildPaths,
    ),
    scope = scope,
    isBazelBacked = isDevBuildBazelBacked(),
  )
  val productProperties = createProductProperties(
    productConfiguration = productConfiguration,
    outputProvider = compilationContext.outputProvider,
    projectDir = request.projectDir,
    platformPrefix = request.platformPrefix,
  )
  return createDevBuildContext(
    compilationContext = compilationContext,
    productProperties = productProperties,
    request = request,
  )
}

@VisibleForTesting
internal fun createProjectDevBuildOptions(request: BuildRequest, buildDir: Path, buildOptionsTemplate: BuildOptions): BuildOptions {
  val classesOutputDirectory = resolveProjectClassesOutputDirectory(request, buildOptionsTemplate)
  val options = buildOptionsTemplate.copy(
    classOutDir = classesOutputDirectory.toString(),
  ).normalizeCompiledClassesOptions(
    defaultClassesOutputDirectory = classesOutputDirectory,
  ).copyWithDevBuildOverrides(
    request = request,
    buildDir = buildDir,
    defaultBuildDateInSeconds = getDevModeOrTestBuildDateInSeconds(),
  )
  configureDevModeBuildOptions(options = options, request = request, buildOptionsTemplate = buildOptionsTemplate)
  return options
}

/**
 * The build option overrides that every dev assembly applies, whichever context it is built from - a project model
 * ([createProjectDevBuildOptions]) or an enclosing build
 * ([org.jetbrains.intellij.build.productRunner.createDevModeProductRunner]).
 *
 * One owner, because the two used to carry their own copy of this list and one of them silently dropped
 * [BuildRequest.buildDateInSeconds]: [BuildOptions.buildDateInSeconds] is a `val`, so the mutating
 * [configureDevModeBuildOptions] cannot set it and every caller has to.
 * [defaultBuildDateInSeconds] is what a request without an override gets - the dev-mode date for a standalone assembly,
 * the enclosing build's own date for one nested in a real build.
 */
internal fun BuildOptions.copyWithDevBuildOverrides(
  request: BuildRequest,
  buildDir: Path,
  defaultBuildDateInSeconds: kotlin.Long,
): BuildOptions {
  return copy(
    jarCacheDir = request.jarCacheDir,
    buildDateInSeconds = request.buildDateInSeconds ?: defaultBuildDateInSeconds,
    isDevDistribution = true,
    printFreeSpace = false,
    validateImplicitPlatformModule = false,
    skipDependencySetup = true,
    skipCheckOutputOfPluginModules = true,
    validateModuleStructure = false,
    cleanOutDir = false,
    outRootDir = buildDir,
    compilationLogEnabled = false,
    logDir = (request.scratchDir ?: buildDir).resolve("log"),
    isUnpackedDist = request.isUnpackedDist,
  )
}

internal fun configureDevModeBuildOptions(options: BuildOptions, request: BuildRequest, buildOptionsTemplate: BuildOptions) {
  options.setTargetOsAndArchToCurrent()
  options.buildStepsToSkip += listOf(
    BuildOptions.PREBUILD_SHARED_INDEXES,
    BuildOptions.FUS_METADATA_BUNDLE_STEP,
    BuildOptions.PROVIDED_MODULES_LIST_STEP,
  )

  if (request.isUnpackedDist && options.enableEmbeddedFrontend) {
    options.enableEmbeddedFrontend = false
  }

  options.generateRuntimeModuleRepository = options.generateRuntimeModuleRepository && request.generateRuntimeModuleRepository
  options.buildNumber = buildOptionsTemplate.buildNumber
  options.isInDevelopmentMode = buildOptionsTemplate.isInDevelopmentMode
  options.isTestBuild = buildOptionsTemplate.isTestBuild
  // A dev assembly can contain uncommitted changes, so HEAD does not identify its contents.
  // Avoid coupling assembly to the mutable checkout solely for production provenance metadata.
  options.storeGitRevision = false
  // Only the fragment that packs the core jars or writes the `plugin-classpath.txt` prefix has anywhere to put the
  // inlined content-module descriptors; for the rest, resolving them only makes every content module's jar an input.
  options.embedProductContentModuleDescriptors = request.fragment.ownsProductDescriptorJars || request.componentOutput?.pluginClasspathPrefixFile != null
}

/** [scratchDir] holds throwaway build data (`temp`, `artifacts`); it is separate from [buildDir] when the latter must contain only the distribution. */
internal fun createDevBuildPaths(projectDir: Path, buildDir: Path, logDir: Path, scratchDir: Path = buildDir): BuildPaths {
  val tempDir = scratchDir.resolve("temp")
  Files.createDirectories(tempDir)

  return BuildPaths(
    communityHomeDirRoot = BuildPaths.COMMUNITY_ROOT,
    buildOutputDir = buildDir,
    logDir = logDir,
    projectHome = projectDir,
    tempDir = tempDir,
    artifactDir = scratchDir.resolve("artifacts"),
    searchableOptionDir = projectDir.resolve("out/dev-data/searchable-options"),
  ).also {
    it.distAllDir = buildDir
  }
}

internal fun createDevBuildContext(
  compilationContext: CompilationContext,
  productProperties: ProductProperties,
  request: BuildRequest,
): BuildContextImpl {
  return BuildContextImpl(
    compilationContext = compilationContext,
    productProperties = productProperties,
    windowsDistributionCustomizer = object : WindowsDistributionCustomizer() {},
    linuxDistributionCustomizer = LinuxDistributionCustomizer(),
    macDistributionCustomizer = MacDistributionCustomizer(),
    proprietaryBuildTools = if (request.scrambleTool == null) {
      ProprietaryBuildTools.DUMMY
    }
    else {
      ProprietaryBuildTools(
        signTool = ProprietaryBuildTools.DUMMY_SIGN_TOOL,
        scrambleTool = request.scrambleTool,
        featureUsageStatisticsProperties = null,
        artifactsServer = null,
        licenseServerHost = null,
      )
    },
    jarCacheManager = request.jarCacheDir?.let {
      LocalDiskJarCacheManager(
        cacheDir = it,
        classesOutputDirectory = compilationContext.classesOutputDirectory,
        maxAccessTimeAge = compilationContext.options.jarCacheMaxAccessAge,
        cleanupInterval = 1.hours,
      )
    } ?: NonCachingJarCacheManager,
  )
}

internal suspend fun createProductProperties(
  productConfiguration: ProductConfiguration,
  outputProvider: ModuleOutputProvider,
  projectDir: Path,
  platformPrefix: String,
): ProductProperties {
  val classPathFiles = getBuildModules(productConfiguration)
    .flatMap { outputProvider.getModuleOutputRoots(outputProvider.findRequiredModule(it)) }
    .toList()

  @Suppress("SimpleRedundantLet")
  (ProductConfiguration::class.java.classLoader as? PathClassLoader)?.let {
    it.getClassPath().addFiles(classPathFiles)
  }

  val classLoader = spanBuilder("create product properties classloader").use {
    PathClassLoader(UrlClassLoader.build().files(classPathFiles).parent(BuildRequest::class.java.classLoader))
  }

  val className = if (System.getProperty("intellij.build.minimal").toBoolean()) {
    "org.jetbrains.intellij.build.IjLightProperties"
  }
  else {
    productConfiguration.className
  }
  return spanBuilder("create product properties").setAttribute("className", className).use {
    doCreateProductProperties(classLoader = classLoader, className = className, classPathFiles = classPathFiles, projectDir = projectDir, platformPrefix = platformPrefix)
  }
}

private val lookup = MethodHandles.lookup()

private fun doCreateProductProperties(
  classLoader: PathClassLoader,
  className: String,
  classPathFiles: List<Path>,
  projectDir: Path,
  platformPrefix: String,
): ProductProperties {
  val productPropertiesClass = try {
    classLoader.loadClass(className)
  }
  catch (e: ClassNotFoundException) {
    val classPathString = classPathFiles.joinToString(separator = "\n") { file ->
      "$file (" + (if (Files.isDirectory(file)) "dir" else if (Files.exists(file)) "exists" else "doesn't exist") + ")"
    }
    val projectPropertiesPath = getProductPropertiesPath(projectDir)
    throw RuntimeException("cannot create product properties, className=$className, projectPropertiesPath=$projectPropertiesPath, classPath=$classPathString, ", e)
  }

  return try {
    lookup.findConstructor(productPropertiesClass, MethodType.methodType(Void.TYPE)).invoke()
  }
  catch (_: NoSuchMethodException) {
    lookup
      .findConstructor(productPropertiesClass, MethodType.methodType(Void.TYPE, Path::class.java))
      .invoke(if (platformPrefix == "Idea" || platformPrefix == "PyCharmCore") getCommunityHomePath(projectDir) else projectDir)
  } as ProductProperties
}

private fun getBuildModules(productConfiguration: ProductConfiguration): Sequence<String> = sequenceOf("intellij.idea.community.build") + productConfiguration.modules.asSequence()

private data class PlatformLayoutResult(
  @JvmField val distributionEntries: List<DistributionFileEntry>,
  @JvmField val coreClassPath: Set<Path>,
)

private suspend fun layoutPlatform(
  runDir: Path,
  platformLayout: PlatformLayout,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  context: BuildContext,
  moduleOutputPatcher: ModuleOutputPatcher,
  request: BuildRequest,
): PlatformLayoutResult {
  val selector = checkNotNull(request.fragment.platform)
  // One answer for both filters below, taken from the whole layout rather than from whatever each of them can see.
  val ownership = PlatformJarOwnership.of(platformLayout.includedModules)
  check(platformLayout.resourcePaths.isEmpty() || request.fragment.isComplete) {
    // Copied by every fragment that lays the platform out, into its own tree, and the paths are not jars the asset
    // filter can partition. No product does this today; the first one that does has to say which fragment owns them.
    "The platform layout of '${request.platformPrefix}' declares resource paths" +
    " (${platformLayout.resourcePaths.joinToString { it.relativeOutputPath }}), which a split assembly cannot place:" +
    " every platform fragment would copy them. Give them an owner before splitting this product."
  }
  val includedModules = selector.selectModules(platformLayout.includedModules, ownership)
  // The fragment decided before the layout existed that it would not need the inlined content-module descriptors,
  // because the application-info module's jar holds no content module and so belongs to the core. Confirm it against
  // the layout that was actually produced: a product that lands that module in a content-module jar would otherwise
  // ship a product descriptor with nothing inlined into it, which fails far away at runtime.
  val applicationInfoModule = context.productProperties.applicationInfoModule
  check(context.options.embedProductContentModuleDescriptors || includedModules.none { it.moduleName == applicationInfoModule }) {
    "Fragment '${request.fragment}' packs the application-info module '$applicationInfoModule'," +
    " whose jar carries the product descriptor, but it did not inline the content-module descriptors into it." +
    " DevBuildFragment.ownsProductDescriptorJars has to account for how '${request.platformPrefix}' lays that module out."
  }
  // cannot be in parallel
  val entries = layoutPlatformDistribution(
    moduleOutputPatcher = moduleOutputPatcher,
    targetDir = runDir,
    platform = platformLayout,
    searchableOptionSet = searchableOptionSet,
    copyFiles = true,
    // Narrows what is resolved, so that a module this fragment does not pack cannot invalidate it.
    includedModules = includedModules,
    // Narrows what is written, over the jars packing produced - including the ones the layout never named, which the
    // core fragment owns: a library pinned into its own jar, or a project library.
    assetFilter = { relativeOutputFile, _ -> selector.accepts(ownership, relativeOutputFile) },
    context = context,
  )
  val coreClassPath = coroutineScope {
    // todo - we cannot for now skip nio-fs.jar, probably `-Xbootclasspath/a` is not correctly set for dev-mode-based tests
    generateClassPathByLayoutReport(
      libDir = runDir.resolve("lib"),
      entries = entries,
      skipNioFs = if (request.isBootClassPathCorrect) isMultiRoutingFileSystemEnabledForProduct(context.productProperties.platformPrefix) else false,
    )
  }

  return PlatformLayoutResult(entries, coreClassPath)
}

private fun computeAdditionalModulesFingerprint(additionalModules: List<String>): String {
  if (additionalModules.isEmpty()) {
    return ""
  }
  else {
    val hash = Hashing.xxh3_64().hashStream()
    hash.putUnorderedIterable(additionalModules, HashFunnel.forString(), Hashing.xxh3_64())
    return "-" + additionalModules.joinToString(separator = "-") { it.removePrefix("intellij.").take(4) } + "-" +
           Long.toUnsignedString(hash.asLong, Character.MAX_RADIX)
  }
}

private fun getCommunityHomePath(homePath: Path): Path {
  return if (Files.isDirectory(homePath.resolve("community"))) homePath.resolve("community") else homePath
}
