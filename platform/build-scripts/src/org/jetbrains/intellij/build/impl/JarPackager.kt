// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.dynatrace.hash4j.hashing.HashFunnel
import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import com.jetbrains.util.filetype.FileType
import com.jetbrains.util.filetype.FileTypeDetector.DetectFileType
import io.opentelemetry.api.common.AttributeKey
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.DirSource
import org.jetbrains.intellij.build.InMemoryContentSource
import org.jetbrains.intellij.build.JarPackagerDependencyHelper
import org.jetbrains.intellij.build.LazySource
import org.jetbrains.intellij.build.MAVEN_REPO
import org.jetbrains.intellij.build.NativeFileHandler
import org.jetbrains.intellij.build.SearchableOptionSetDescriptor
import org.jetbrains.intellij.build.SignNativeFileMode
import org.jetbrains.intellij.build.Source
import org.jetbrains.intellij.build.USER_HOME
import org.jetbrains.intellij.build.ZipSource
import org.jetbrains.intellij.build.buildJar
import org.jetbrains.intellij.build.checkForNoDiskSpace
import org.jetbrains.intellij.build.computeHashForModuleOutput
import org.jetbrains.intellij.build.computeModuleSourcesByContent
import org.jetbrains.intellij.build.defaultLibrarySourcesNamesFilter
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.getLibraryRoots
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.inferModuleSources
import org.jetbrains.intellij.build.io.WRITE_OPEN_OPTION
import org.jetbrains.intellij.build.io.writeToFileChannelFully
import org.jetbrains.intellij.build.jarCache.JarCacheManager
import org.jetbrains.intellij.build.jarCache.NonCachingJarCacheManager
import org.jetbrains.intellij.build.jarCache.SourceBuilder
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileSystemException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.attribute.BasicFileAttributes
import java.util.TreeMap
import java.util.TreeSet
import kotlin.io.path.invariantSeparatorsPathString

private fun isJarPreSigned(file: Path, context: BuildContext): Boolean {
  return context.productProperties.presignedNativeLibs.containsKey(getLibNameBySourceFile(file))
}

/**
 * Selects the jars an assembly actually writes, out of everything the layout put in them.
 *
 * A split dev-distribution fragment lays the whole layout out - that part is metadata, and cheap - and then packs only
 * the jars it owns, so that reading and zipping is what the split divides. The layout is the input because ownership is
 * decided from it (see `org.jetbrains.intellij.build.dev.jarOwnership`), never from a list of file names.
 */
@ApiStatus.Internal
fun interface DistributionAssetFilter {
  fun accept(relativeOutputFile: String, includedModules: Collection<ModuleItem>): Boolean
}

/**
 * Project libraries that plugin modules still get implicitly, only because such a dependency was packaged this way before
 * the implicit collection was restricted to library modules (see [computeSourcesForModuleLibs]).
 *
 * Each entry must be converted to a library module (`intellij.libraries.*`) and removed from this list - IJPL-252908.
 * Do not add new entries: a project library must be provided by the platform, by a library module,
 * or declared explicitly in the plugin layout.
 */
private val IMPLICIT_PLUGIN_PROJECT_LIBRARY_ALLOWLIST: Set<String> = java.util.Set.of(
  // declared by the platform layout of the ultimate-family products only, so a plugin of another product needs its own copy
  "LicenseDecoder",
  "LicenseServerAPI",
  "Servlets",
  "agentclientprotocol.acp.jvm",
  "agentclientprotocol.acp.ktor",
  "ai.grazie.rule.engine",
  "ai.grazie.semantic.engine",
  "air.acp.jvm",
  "air.acp.ktor",
  "antlr4-runtime",
  "apache.avro",
  "assertj-swing",
  "com.jetbrains.fus.reporting.ap.validation.all",
  "cucumber-core-1",
  "git-learning-project",
  "github.javafaker",
  "google.protobuf.java.util",
  // used by `intellij.rider.test.cases.rdct`, whose plugin is built by an auto layout
  "intellij-plugin-structure",
  // also used by `intellij.ml.llm.libraries.grazie`, which is a library module by intention, but not by name
  "io.github.oshai.kotlin.logging.jvm",
  "io.modelcontextprotocol.kotlin.sdk",
  "io.qameta.allure.java.commons",
  "jerolba.carpet.record",
  "jetbrains.ai.completion.trigger.model.markdown.cloud",
  "jetbrains.ai.completion.trigger.model.polyglot.cloud",
  "jetbrains.ai.completion.trigger.model.text.cloud",
  "jetbrains.compose.hot.reload.devtools.api",
  "jetbrains.compose.hot.reload.gradle.idea",
  "jetbrains.compose.preview.rpc",
  "jetbrains.intellij.deps.eclipse.jgit",
  "jetbrains.intellij.deps.scheduled.debugger.agent",
  "jetbrains.intellij.deps.searchEverywhere.model.context.ranker.test",
  "jetbrains.kotlin.doctor.kdoctor.api",
  "jetbrains.kotlin.doctor.kdoctor.checks",
  "jetbrains.mlapi.catboost",
  "jetbrains.mlapi.catboost.shadow.need.slf4j",
  "jetbrains.mlapi.core",
  "jetbrains.patronus.config",
  "jetbrains.qodana.cloud.kotlin.client",
  "jetbrains.qodana.configuration",
  "jetbrains.qodana.publisher",
  "jetbrains.qodana.sarif.converter",
  "jetbrains.qodana.web.ui",
  // per-OS/arch native runtimes of the RenderDoc client, all packed into the plugin
  "jetbrains.rd.client.renderdoc.runtime.linux.aarch64",
  "jetbrains.rd.client.renderdoc.runtime.linux.x86_64",
  "jetbrains.rd.client.renderdoc.runtime.macos.aarch64",
  "jetbrains.rd.client.renderdoc.runtime.macos.x86_64",
  "jetbrains.rd.client.renderdoc.runtime.windows.aarch64",
  "jetbrains.rd.client.renderdoc.runtime.windows.x86_64",
  "jgrapht.core",
  "jooq.joox",
  "jps-javac-extension",
  "jruby-parser-0.5.4",
  "json-schema-validator",
  "kaml",
  "kmp-wizard-shared",
  "kotlin-metadata",
  "kotlinc.kotlin-jps-common",
  "kotlinc.kotlin-script-runtime",
  "kotlinc.kotlin-scripting-common",
  "kotlinc.kotlin-scripting-compiler-impl",
  "kotlinc.kotlin-scripting-jvm",
  "kxml2",
  "layoutlib",
  "libthrift",
  "memoryfilesystem",
  "okhttp",
  "openai.java",
  "org.apache.ivy",
  "org.scilab.forge:jlatexmath",
  "package-search-api-client",
  "qodana-sarif",
  "software.amazon.awssdk.glue",
  "space-idea-sdk",
  "spaceport-idea-sdk",
  // see the comment for `flexmark`
  "sqlite",
  "sqlite-native",
  "squareup.okio.jvm",
  // declared by the android plugin layout, so the Rider android plugin needs its own copy
  "studio-platform",
  "workspace-model-codegen",
  "zxing-core",
)

class JarPackager private constructor(
  private val outDir: Path,
  private val context: BuildContext,
  private val platformLayout: PlatformLayout?,
  private val isRootDir: Boolean,
  @JvmField internal val moduleOutputPatcher: ModuleOutputPatcher,
) {
  private val assets = LinkedHashMap<Path, AssetDescriptor>()

  private val copiedFiles = LibraryFileCopyTracker()

  /** project library name to the names of the plugin modules that depend on it, but do not get it packaged - see [checkImplicitProjectLibraries] */
  private val implicitProjectLibraryViolations = TreeMap<String, MutableSet<String>>()

  private val helper = (context as BuildContextImpl).jarPackagerDependencyHelper

  companion object {
    suspend fun pack(includedModules: Collection<ModuleItem>, outputDir: Path, context: BuildContext) {
      val packager = JarPackager(outDir = outputDir, context = context, platformLayout = null, isRootDir = false, moduleOutputPatcher = ModuleOutputPatcher())
      packager.computeModuleSources(includedModules = includedModules, layout = null, searchableOptionSet = null, cachedDescriptorWriterProvider = null)
      buildJars(
        assets = packager.assets.values,
        cache = if (context is BuildContextImpl) context.jarCacheManager else NonCachingJarCacheManager,
        isCodesignEnabled = false,
        useCacheAsTargetFile = context.options.isUnpackedDist,
        dryRun = false,
        layout = null,
        helper = packager.helper,
        context = context
      )
    }

    suspend fun pack(
      includedModules: Collection<ModuleItem>,
      outputDir: Path,
      isRootDir: Boolean,
      isCodesignEnabled: Boolean = true,
      layout: BaseLayout,
      platformLayout: PlatformLayout?,
      moduleOutputPatcher: ModuleOutputPatcher,
      dryRun: Boolean,
      searchableOptionSet: SearchableOptionSetDescriptor? = null,
      descriptorCache: ScopedCachedDescriptorContainer? = null,
      assetFilter: DistributionAssetFilter? = null,
      context: BuildContext,
    ): Collection<DistributionFileEntry> {
      val packager = JarPackager(outDir = outputDir, context = context, platformLayout = platformLayout, isRootDir = isRootDir, moduleOutputPatcher = moduleOutputPatcher)
      packager.computeModuleSources(
        includedModules = includedModules,
        layout = layout,
        searchableOptionSet = searchableOptionSet,
        cachedDescriptorWriterProvider = descriptorCache
      )
      packager.computeModuleCustomLibrarySources(layout, assetFilter)

      packager.computeProjectLibrariesSources(
        outDir = outputDir,
        layout = layout,
        copiedFiles = packager.copiedFiles,
        assetFilter = assetFilter,
      )

      // The whole layout is computed above, but only the owned jars are packed and reported: everything downstream -
      // the built files, the distribution entries, the classpath - must see one consistent subset.
      val assets = if (assetFilter == null) {
        packager.assets.values
      }
      else {
        packager.assets.values.filter { assetFilter.accept(it.relativePath, it.includedModules.keys) }
      }

      val cacheManager = if (dryRun || context !is BuildContextImpl) NonCachingJarCacheManager else context.jarCacheManager
      val buildAssetResult = buildJars(
        assets = assets,
        cache = cacheManager,
        isCodesignEnabled = isCodesignEnabled,
        useCacheAsTargetFile = !dryRun && context.options.isUnpackedDist,
        dryRun = dryRun,
        layout = layout,
        helper = packager.helper,
        context = context,
      )

      return coroutineScope {
        if (buildAssetResult.sourceToNativeFiles.isNotEmpty()) {
          launch(CoroutineName("pack native presigned files")) {
            packNativePresignedFiles(
              nativeFiles = buildAssetResult.sourceToNativeFiles,
              dryRun = dryRun,
              context = context,
              toRelativePath = { libName, fileName -> "lib/${context.productProperties.presignedNativeLibs.getOrDefault(libName, libName)}/$fileName" },
            )
          }
        }

        val list = mutableListOf<DistributionFileEntry>()
        val hasher = Hashing.xxh3_64().hashStream()
        for (item in assets) {
          computeDistributionFileEntries(asset = item, hasher = hasher, list = list, dryRun = dryRun, buildAssetResult = buildAssetResult)
        }
        list
      }
    }
  }

  private suspend fun computeModuleSources(
    includedModules: Collection<ModuleItem>,
    layout: BaseLayout?,
    searchableOptionSet: SearchableOptionSetDescriptor?,
    cachedDescriptorWriterProvider: ScopedCachedDescriptorContainer?,
  ) {
    val addedModules = HashSet<String>()

    val modulesWithCustomPath = HashSet<String>()
    for (item in includedModules) {
      if (layout is PluginLayout && !item.relativeOutputFile.contains('/')) {
        if (item.relativeOutputFile != layout.getMainJarName()) {
          modulesWithCustomPath.add(item.moduleName)
        }
      }
    }

    // First, check the content. This is done prior to everything else since we might configure a custom relativeOutputFile.
    if (layout is PluginLayout) {
      computeModuleSourcesByContent(
        helper = helper,
        context = context,
        pluginLayout = layout,
        addedModules = addedModules,
        jarPackager = this,
        searchableOptionSet = searchableOptionSet,
        modulesWithCustomPath = modulesWithCustomPath,
        pluginCachedDescriptorContainer = cachedDescriptorWriterProvider!!,
      )
    }

    for (item in includedModules) {
      if (layout is PluginLayout && addedModules.contains(item.moduleName) && !item.relativeOutputFile.contains('/')) {
        check(item.relativeOutputFile == layout.getMainJarName()) {
          "Custom output path is not allowed for content modules ($item)"
        }
        continue
      }

      computeSourcesForModule(item, layout, searchableOptionSet)
      addedModules.add(item.moduleName)
    }

    if (layout is PluginLayout && layout.auto) {
      inferModuleSources(
        layout = layout,
        addedModules = addedModules,
        platformLayout = platformLayout!!,
        helper = helper,
        jarPackager = this,
        searchableOptionSet = searchableOptionSet,
        context = context,
      )
    }

    checkImplicitProjectLibraries(layout)
  }

  /**
   * A project library referenced by a plugin module, but neither provided by the platform nor by a library module,
   * would be silently missing from the distribution - fail the build instead, listing everything to be converted.
   */
  private fun checkImplicitProjectLibraries(layout: BaseLayout?) {
    check(implicitProjectLibraryViolations.isEmpty()) {
      "Project libraries used by modules of $layout must be converted to content modules:\n" +
      implicitProjectLibraryViolations.entries.joinToString(separator = "\n") { (libraryName, moduleNames) ->
        "  '$libraryName' used by " + moduleNames.joinToString { "'$it'" }
      }
    }
  }

  /**
   * `true` if [libName] reaches [module] without being packaged for it: the platform provides it (as a library or as a library module),
   * the plugin declares it explicitly, another module of the same group brings it (the same check the collection above does),
   * or a library module for it exists, so `LibraryModuleValidator` is the one to make this module depend on that module.
   */
  private fun isProjectLibraryProvided(libName: String, layout: BaseLayout, module: JpsModule, withTests: Boolean): Boolean {
    return platformLayout == null ||
           platformLayout.hasLibrary(libName, module.name) ||
           layout.hasLibrary(libName) ||
           context.outputProvider.getProjectLibraryToModuleMap().containsKey(libName) ||
           helper.hasLibraryInDependencyChainOfModuleDependencies(
             dependentModule = module,
             libraryName = libName,
             siblings = layout.includedModules,
             withTests = withTests,
           )
  }

  internal suspend fun computeSourcesForModule(item: ModuleItem, layout: BaseLayout?, searchableOptionSet: SearchableOptionSetDescriptor?) {
    val moduleName = item.moduleName
    val patchedContent = moduleOutputPatcher.getPatchedContent(moduleName)

    val module = context.outputProvider.findRequiredModule(moduleName)
    val useTestModuleOutput = helper.isTestPluginModule(moduleName, module)
    val moduleOutputRoots = context.outputProvider.getModuleOutputRoots(module, forTests = useTestModuleOutput)
    val extraExcludes = layout?.moduleExcludes?.get(moduleName) ?: emptyList()
    val filterCacheKey = if (extraExcludes.isEmpty()) emptyList() else extraExcludes.toSortedSet().toList()

    val packToDir = context.options.isUnpackedDist &&
                    !item.relativeOutputFile.contains('/') &&
                    !item.isProductModule() &&
                    (patchedContent.isEmpty() || (patchedContent.size == 1 && patchedContent.containsKey("META-INF/plugin.xml"))) &&
                    extraExcludes.isEmpty() &&
                    moduleOutputRoots.isNotEmpty()

    val outFile = outDir.resolve(item.relativeOutputFile)
    val asset = if (packToDir) {
      assets.computeIfAbsent(moduleOutputRoots.single()) { file ->
        AssetDescriptor(isDir = !file.toString().endsWith(".jar"), file = file, relativePath = "")
      }
    }
    else {
      assets.computeIfAbsent(outFile) { file ->
        AssetDescriptor(isDir = false, file = file, relativePath = item.relativeOutputFile, useCacheAsTargetFile = !item.isProductModule())
      }
    }

    val moduleSources = asset.includedModules.computeIfAbsent(item) { mutableListOf() }

    for ((relativePath, data) in patchedContent) {
      if (layout is PluginLayout && moduleName != layout.mainModule && relativePath == "META-INF/plugin.xml") {
        continue
      }
      moduleSources.add(InMemoryContentSource(relativePath, data))
    }

    val jarAsset = lazy(LazyThreadSafetyMode.NONE) {
      if (packToDir) {
        getJarAsset(targetFile = outFile, relativeOutputFile = item.relativeOutputFile)
      }
      else {
        asset
      }
    }

    if (searchableOptionSet != null) {
      addSearchableOptionSources(layout = layout, moduleName = moduleName, module = module, sources = jarAsset.value.sources, searchableOptionSet = searchableOptionSet)
    }

    val excludes = if (extraExcludes.isEmpty()) {
      commonModuleExcludes
    }
    else {
      val fileSystem = FileSystems.getDefault()
      val result = ArrayList<PathMatcher>(commonModuleExcludes.size + extraExcludes.size)
      result.addAll(commonModuleExcludes)
      extraExcludes.mapTo(result) { fileSystem.getPathMatcher("glob:$it") }
      result
    }

    for (moduleOutDir in moduleOutputRoots) {
      val source = createModuleSource(module = module, outputDir = moduleOutDir, excludes = excludes, filterCacheKey = filterCacheKey)
      if (source != null) {
        moduleSources.add(source)
      }
    }

    if (layout is PluginLayout && layout.mainModule == moduleName) {
      handleCustomAssets(layout, jarAsset)
    }

    if (layout != null && (layout !is PluginLayout || !layout.modulesWithExcludedModuleLibraries.contains(moduleName))) {
      computeSourcesForModuleLibs(item = item, layout = layout, module = module, copiedFiles = copiedFiles, asset = jarAsset, withTests = useTestModuleOutput)
    }
  }

  private suspend fun handleCustomAssets(layout: PluginLayout, jarAsset: Lazy<AssetDescriptor>) {
    for (customAsset in layout.customAssets) {
      if (customAsset.platformSpecific != null) {
        continue
      }

      val relativePath = customAsset.relativePath
      if (relativePath == null) {
        customAsset.getSources(context)?.let { jarAsset.value.sources.addAll(it) }
      }
      else {
        val targetFile = outDir.resolveSibling(relativePath)
        val assetDescriptor = AssetDescriptor(isDir = false, file = targetFile, relativePath = relativePath, useCacheAsTargetFile = false)
        customAsset.getSources(context)?.let { assetDescriptor.sources.addAll(it) }
        val existing = assets.putIfAbsent(targetFile, assetDescriptor)
        require(existing == null) {
          "CustomAsset must be packed into separate target file (existing=$existing, new=$assetDescriptor)"
        }
      }
    }
  }

  private suspend fun addSearchableOptionSources(
    layout: BaseLayout?,
    moduleName: String,
    module: JpsModule,
    sources: MutableList<Source>,
    searchableOptionSet: SearchableOptionSetDescriptor,
  ) {
    if (layout is PluginLayout) {
      if (moduleName == BUILT_IN_HELP_MODULE_NAME) {
        return
      }

      if (moduleName == layout.mainModule) {
        val pluginId = helper.getPluginIdByModule(module)
        sources.addAll(searchableOptionSet.createSourceByPlugin(pluginId))
      }
      else {
        // is it a product module?
        findFileInModuleSources(module, "$moduleName.xml")?.let {
          sources.addAll(searchableOptionSet.createSourceByModule(moduleName))
        }
      }
    }
    else if (moduleName == context.productProperties.applicationInfoModule) {
      sources.addAll(searchableOptionSet.createSourceByPlugin("com.intellij"))
    }
  }

  private fun computeSourcesForModuleLibs(
    item: ModuleItem,
    layout: BaseLayout,
    module: JpsModule,
    copiedFiles: LibraryFileCopyTracker,
    asset: Lazy<AssetDescriptor>,
    withTests: Boolean,
  ) {
    val moduleName = module.name
    // `auto` used to mean "collect every project library of every module of this plugin" - now only a library module does it,
    // everything else must be provided by the platform, by a library module, or declared explicitly in the plugin layout
    val isAutoPlugin = layout is PluginLayout && layout.auto
    val includeProjectLib = if (layout is PluginLayout) isAutoPlugin && moduleName.startsWith(LIB_MODULE_PREFIX) else item.isProductModule()

    val excludedModuleLibraries = if (layout is PluginLayout) layout.excludedModuleLibraries.get(moduleName) ?: emptyList() else emptyList()
    val excludedProjectLibraries = if (layout is PluginLayout) layout.excludedProjectLibraries else emptySet()
    for (element in helper.getLibraryDependencies(module, withTests = withTests)) {
      var projectLibraryData: ProjectLibraryData? = null
      val libRef = element.libraryReference
      val isProjectLibrary = libRef.parentReference !is JpsModuleReference
      if (isProjectLibrary) {
        val libName = libRef.libraryName
        if (excludedProjectLibraries.contains(libName)) {
          continue
        }

        if (includeProjectLib || (isAutoPlugin && IMPLICIT_PLUGIN_PROJECT_LIBRARY_ALLOWLIST.contains(libName))) {
          if (platformLayout!!.hasLibrary(libName, moduleName) || layout.hasLibrary(libName)) {
            continue
          }

          if (helper.hasLibraryInDependencyChainOfModuleDependencies(dependentModule = module, libraryName = libName, siblings = layout.includedModules, withTests = withTests)) {
            continue
          }

          if (layout !is PluginLayout && item.isProductModule()) {
            projectLibraryData = ProjectLibraryData(libraryName = libName, owner = item, reason = null)
          }
          else {
            projectLibraryData = ProjectLibraryData(libraryName = libName, reason = "<- $moduleName", owner = item)
          }
        }
        else if (platformLayout != null && platformLayout.isLibraryAlwaysPackedIntoPlugin(libName)) {
          platformLayout.findProjectLibrary(libName)?.let {
            throw IllegalStateException("Library $libName must not be included into platform layout: $it")
          }

          if (layout.hasLibrary(libName)) {
            continue
          }

          projectLibraryData = ProjectLibraryData(libraryName = libName, reason = "<- $moduleName (always packed into plugin)", owner = item)
        }
        else {
          if (isAutoPlugin &&
              !isProjectLibraryProvided(libName = libName, layout = layout, module = module, withTests = withTests)) {
            implicitProjectLibraryViolations.computeIfAbsent(libName) { TreeSet() }.add(moduleName)
          }
          continue
        }
      }

      val library = requireNotNull(element.library) { "cannot find $libRef" }
      val libraryName = getLibraryFileName(library)
      if ((!isProjectLibrary && excludedModuleLibraries.contains(libraryName)) ||
          layout.includedModuleLibraries.any { it.libraryName == libraryName && !it.extraCopy }) {
        continue
      }

      if (item.reason == ModuleIncludeReasons.PRODUCT_MODULES) {
        packLibFilesIntoModuleJar(
          asset = asset.value,
          item = item,
          files = getLibraryRoots(library, context.outputProvider),
          projectLibraryData = projectLibraryData,
          library = library,
        )
      }
      else {
        fun addLibrary(relativeOutputFile: String, files: List<Path>) {
          val asset = getJarAsset(targetFile = outDir.resolve(relativeOutputFile), relativeOutputFile = relativeOutputFile)
          filesToSourceWithMapping(asset = asset, files = files, library = library, relativeOutputFile = relativeOutputFile, projectLibraryData = projectLibraryData)
        }

        fun addSeparateLibrary(fileName: String, file: Path) {
          val relativeOutputFile = removeVersionFromJar(fileName)
          if (copiedFiles.markLibraryFileForCopy(file = file, targetFile = outDir.resolve(relativeOutputFile))) {
            addLibrary(relativeOutputFile = relativeOutputFile, files = listOf(file))
          }
        }

        val targetFile = outDir.resolve(item.relativeOutputFile)
        val files = copiedFiles.getLibraryFiles(library = library, targetFile = targetFile, outputProvider = context.outputProvider)
        if (layout is PluginLayout && item.relativeOutputFile == layout.getMainJarName()) {
          if (files.size > 1) {
            for (i in (files.size - 1) downTo 0) {
              val file = files[i]
              val fileName = file.fileName.toString()
              if (fileName.endsWith("-rt.jar") || fileName.startsWith("maven-")) {
                files.removeAt(i)
                addSeparateLibrary(fileName = fileName, file = file)
              }
            }
          }

          addLibrary(relativeOutputFile = removeVersionFromJar(fileName = nameToJarFileName(getLibraryFileName(library))), files = files)
        }
        else {
          for (i in (files.size - 1) downTo 0) {
            val file = files[i]
            val fileName = file.fileName.toString()
            if (isSeparateLibraryJar(fileName)) {
              files.removeAt(i)
              addSeparateLibrary(fileName = fileName, file = file)
            }
          }

          packLibFilesIntoModuleJar(asset = asset.value, item = item, files = files, projectLibraryData = projectLibraryData, library = library)
        }
      }
    }
  }

  private fun packLibFilesIntoModuleJar(
    asset: AssetDescriptor,
    item: ModuleItem,
    files: List<Path>,
    projectLibraryData: ProjectLibraryData?,
    library: JpsLibrary,
  ) {
    val libraryName = getLibraryFileName(library)
    val mavenPaths = library.getPaths(JpsOrderRootType.COMPILED).map { toCanonicalReportPath(it, context.paths) }
    for (file in files) {
      val canonicalPath = getCanonicalPath(mavenPaths, file)
      @Suppress("NAME_SHADOWING")
      asset.sources.add(
        ZipSource(
          file = file,
          distributionFileEntryProducer = { size, hash, targetFile ->
            if (projectLibraryData == null) {
              ModuleLibraryFileEntry(
                path = targetFile,
                moduleName = item.moduleName,
                libraryName = libraryName,
                libraryFile = file,
                canonicalLibraryPath = canonicalPath,
                size = size,
                hash = hash,
                relativeOutputFile = item.relativeOutputFile,
                owner = item,
                distributionPath = asset.file,
              )
            }
            else {
              ProjectLibraryEntry(
                path = targetFile,
                data = projectLibraryData,
                libraryFile = file,
                canonicalLibraryPath = canonicalPath,
                hash = hash,
                size = size,
                relativeOutputFile = item.relativeOutputFile,
                distributionPath = asset.file,
              )
            }
          },
          isPreSignedAndExtractedCandidate = isJarPreSigned(file, context),
          filter = ::defaultLibrarySourcesNamesFilter,
          moduleName = null,
        )
      )
    }
  }

  private fun computeModuleCustomLibrarySources(layout: BaseLayout, assetFilter: DistributionAssetFilter?) {
    for (item in layout.includedModuleLibraries) {
      var relativePath = item.relativeOutputPath
      val targetFile: Path
      if (relativePath.endsWith(".jar")) {
        targetFile = outDir.resolve(relativePath)
        if (!relativePath.contains('/')) {
          relativePath = ""
        }
      }
      else {
        val fileName = nameToJarFileName(item.libraryName)
        if (relativePath.isEmpty()) {
          targetFile = outDir.resolve(fileName)
        }
        else {
          targetFile = outDir.resolve(relativePath).resolve(fileName)
          relativePath += "/$fileName"
        }
      }

      if (assetFilter != null && !assetFilter.accept(relativePath, emptyList())) continue
      val library = context.outputProvider.findRequiredModule(item.moduleName).libraryCollection.libraries.find { getLibraryFileName(it) == item.libraryName }
                    ?: throw IllegalArgumentException("Cannot find library ${item.libraryName} in '${item.moduleName}' module")
      val asset = getJarAsset(targetFile, relativePath)
      val files = copiedFiles.getLibraryFiles(library = library, targetFile = targetFile, outputProvider = context.outputProvider)
      filesToSourceWithMapping(asset = asset, files = files, library = library, relativeOutputFile = relativePath, projectLibraryData = null)
    }
  }

  private fun computeProjectLibrariesSources(
    outDir: Path,
    layout: BaseLayout,
    copiedFiles: LibraryFileCopyTracker,
    assetFilter: DistributionAssetFilter?,
  ) {
    if (layout.includedProjectLibraries.isEmpty()) {
      return
    }

    val outputProvider = context.outputProvider
    val projectLibs = layout.includedProjectLibraries.sortedBy { it.libraryName }
    for (libraryData in projectLibs) {
      val library = context.project.libraryCollection.findLibrary(libraryData.libraryName)
                    ?: throw IllegalArgumentException("Cannot find library ${libraryData.libraryName} in the project")
      val libName = library.name
      val outPath = libraryData.outPath
      var libOutputDir = outDir
      if (outPath != null) {
        if (outPath.endsWith(".jar")) {
          val targetFile = outDir.resolve(outPath)
          if (assetFilter != null && !assetFilter.accept(outPath, emptyList())) continue
          val asset = getJarAsset(targetFile, outPath)
          val files = copiedFiles.getLibraryFiles(library = library, targetFile = targetFile, outputProvider = outputProvider)
          filesToSourceWithMapping(asset, files, library, outPath, libraryData)
          continue
        }

        libOutputDir = outDir.resolve(outPath)
      }

      fun addLibrary(targetFile: Path, relativeOutputFile: String, files: List<Path>) {
        val asset = getJarAsset(targetFile, relativeOutputFile)
        filesToSourceWithMapping(asset = asset, files = files, library = library, relativeOutputFile = relativeOutputFile, projectLibraryData = libraryData)
      }

      if (libraryData.packMode == LibraryPackMode.STANDALONE_MERGED) {
        val targetFile = libOutputDir.resolve(nameToJarFileName(libName))
        val relativeOutputFile = outDir.relativize(targetFile).invariantSeparatorsPathString
        if (assetFilter != null && !assetFilter.accept(relativeOutputFile, emptyList())) continue
        addLibrary(
          targetFile = targetFile,
          relativeOutputFile = relativeOutputFile,
          files = copiedFiles.getLibraryFiles(library = library, targetFile = targetFile, outputProvider = outputProvider)
        )
      }
      else {
        if (assetFilter != null && !assetFilter.accept(nameToJarFileName(libName), emptyList())) continue
        for (file in getLibraryRoots(library, outputProvider)) {
          val targetFile = libOutputDir.resolve(file.fileName.toString())
          val relativeOutputFile = outDir.relativize(targetFile).invariantSeparatorsPathString
          addLibrary(targetFile = targetFile, relativeOutputFile = relativeOutputFile, files = listOf(file))
        }
      }
    }
  }

  private fun filesToSourceWithMapping(
    asset: AssetDescriptor,
    files: List<Path>,
    library: JpsLibrary,
    relativeOutputFile: String?,
    projectLibraryData: ProjectLibraryData?,
  ) {
    val libraryName = library.name
    val moduleName = (library.createReference().parentReference as? JpsModuleReference)?.moduleName
    if (moduleName == null && projectLibraryData == null) {
      throw IllegalStateException("Metadata not specified for $libraryName")
    }

    val sources = asset.sources
    val mavenPaths = library.getPaths(JpsOrderRootType.COMPILED).map { toCanonicalReportPath(it, context.paths) }
    for (file in files) {
      val canonicalPath = getCanonicalPath(mavenPaths, file)
      sources.add(
        ZipSource(
          file = file,
          isPreSignedAndExtractedCandidate = isRootDir && isJarPreSigned(file, context),
          optimizeConfigId = libraryName.takeIf { isRootDir && libraryName == "jsvg" },
          distributionFileEntryProducer = { size, hash, targetFile ->
            if (moduleName == null) {
              val data = projectLibraryData ?: throw IllegalStateException("Metadata not specified for $libraryName")
              ProjectLibraryEntry(
                path = targetFile,
                data = data,
                libraryFile = file,
                canonicalLibraryPath = canonicalPath,
                hash = hash,
                size = size,
                relativeOutputFile = relativeOutputFile,
                distributionPath = asset.file,
              )
            }
            else {
              ModuleLibraryFileEntry(
                path = targetFile,
                moduleName = moduleName,
                libraryName = getLibraryFileName(library),
                libraryFile = file,
                canonicalLibraryPath = canonicalPath,
                size = size,
                hash = hash,
                relativeOutputFile = relativeOutputFile,
                owner = ModuleItem(moduleName, relativeOutputFile = targetFile.fileName.toString(), reason = null),
                distributionPath = asset.file,
              )
            }
          },
          filter = ::defaultLibrarySourcesNamesFilter,
          moduleName = null,
        )
      )
    }
  }

  private fun getJarAsset(targetFile: Path, relativeOutputFile: String): AssetDescriptor {
    return assets.computeIfAbsent(targetFile) {
      AssetDescriptor(isDir = false, file = targetFile, relativePath = relativeOutputFile)
    }
  }
}

private fun getCanonicalPath(mavenPaths: List<String>, file: Path): String {
  return mavenPaths.singleOrNull()
         ?: mavenPaths.firstOrNull { it.endsWith("/${file.fileName}") }
         ?: throw IllegalStateException("Cannot find canonical path for $file in $mavenPaths")
}

private fun toCanonicalReportPath(file: Path, buildPaths: BuildPaths): String {
  val projectHome = buildPaths.projectHome
  val mavenHome = MAVEN_REPO
  for (root in listOf(bazelMavenHome, mavenHome, projectHome)) {
    if (file.startsWith(root)) {
      val macro = if (root === projectHome) $$"$PROJECT_DIR$/" else $$"$MAVEN_REPOSITORY$/"
      return macro + root.relativize(file).invariantSeparatorsPathString
    }
  }
  return file.invariantSeparatorsPathString
}

private val bazelMavenHome = USER_HOME.resolve(".m2/repository-do-not-use-maven-repository-with-bazel")

private data class AssetDescriptor(
  @JvmField val isDir: Boolean,
  @JvmField val file: Path,
  @JvmField val relativePath: String,
  @JvmField var effectiveFile: Path = file,
  @JvmField val useCacheAsTargetFile: Boolean = true,
) {
  // must be sorted - we use it as is for Jar Cache
  @JvmField
  val sources: MutableList<Source> = mutableListOf()

  // must be sorted - we use it as is for Jar Cache
  @JvmField
  val includedModules = Reference2ObjectLinkedOpenHashMap<ModuleItem, MutableList<Source>>()
}

internal val commonModuleExcludes: List<PathMatcher> = FileSystems.getDefault().let { fs ->
  listOf(
    fs.getPathMatcher("glob:**/icon-robots.txt"),
    fs.getPathMatcher("glob:icon-robots.txt"),
    fs.getPathMatcher("glob:.unmodified"),
    // compilation cache on TC
    fs.getPathMatcher("glob:.hash"),
    fs.getPathMatcher("glob:classpath.index"),
    fs.getPathMatcher("glob:module-info.class"),
  )
}

internal fun createModuleSourcesNamesFilter(excludes: List<PathMatcher>): (String) -> Boolean {
  return { name ->
    val p = Path.of(name)
    excludes.none { it.matches(p) }
  }
}

private suspend fun buildJars(
  assets: Collection<AssetDescriptor>,
  cache: JarCacheManager,
  isCodesignEnabled: Boolean,
  useCacheAsTargetFile: Boolean,
  dryRun: Boolean,
  layout: BaseLayout?,
  helper: JarPackagerDependencyHelper,
  context: BuildContext,
): BuildAssetResult {
  checkAssetUniqueness(assets)

  if (dryRun) {
    return emptyBuildJarsResult()
  }

  val list = assets.mapConcurrent(workerDispatcher = Dispatchers.IO) { asset ->
    withContext(CoroutineName("build jar for ${asset.relativePath}")) {
      buildAsset(
        asset = asset,
        isCodesignEnabled = isCodesignEnabled,
        context = context,
        cache = cache,
        useCacheAsTargetFile = useCacheAsTargetFile,
        layout = layout,
        helper = helper,
      )
    }
  }

  val sourceToNativeFiles = TreeMap<ZipSource, List<String>>(compareBy { it.file.fileName.toString() })
  val sourceToMetadata = HashMap<Source, SizeAndHash>()

  for (item in list) {
    sourceToNativeFiles.putAll(item.sourceToNativeFiles)
    sourceToMetadata.putAll(item.sourceToMetadata)
  }
  return BuildAssetResult(sourceToNativeFiles = sourceToNativeFiles.ifEmpty { emptyMap() }, sourceToMetadata = sourceToMetadata)
}

private data class SizeAndHash(@JvmField val size: Int, @JvmField val hash: Long)

private data class BuildAssetResult(
  @JvmField val sourceToNativeFiles: Map<ZipSource, List<String>>,
  @JvmField val sourceToMetadata: Map<Source, SizeAndHash>,
)

private fun buildDuplicateSourceErrorMessage(
  file: Path,
  asset: AssetDescriptor,
  source: Source,
  old: SizeAndHash,
  size: Int,
  hash: Long,
  includedModules: Map<ModuleItem, MutableList<Source>>,
): String = buildString {
  appendLine("Source is duplicated:")
  appendLine("  Target JAR: $file")
  appendLine("  Relative path: ${asset.relativePath}")
  appendLine("  Duplicate source: $source")
  if (source is ZipSource) {
    appendLine("  Source file: ${source.file}")
  }
  appendLine("  Already processed: size=${old.size}, hash=${old.hash}")
  appendLine("  New occurrence:    size=$size, hash=$hash")
  if (includedModules.isEmpty()) {
    appendLine("  Sources being packed into this JAR (no modules, direct library merge):")
    appendLine("    Total sources: ${asset.sources.size}")

    // Count how many times the duplicate source appears
    val duplicateCount = asset.sources.count { it == source }
    if (duplicateCount > 1) {
      appendLine("    Duplicate source appears $duplicateCount times in the list below:")
    }

    var duplicateIndex = 0
    for (s in asset.sources) {
      if (s == source) {
        duplicateIndex++
        appendLine("    >>> $s [DUPLICATE #$duplicateIndex]")
      }
      else {
        appendLine("    - $s")
      }
    }
  }
  else {
    appendLine("  Modules being packed into this JAR:")
    for (module in includedModules.keys) {
      appendLine("    - ${module.moduleName} (reason: ${module.reason}, output: ${module.relativeOutputFile})")
    }
  }
}

private suspend fun buildAsset(
  asset: AssetDescriptor,
  isCodesignEnabled: Boolean,
  context: BuildContext,
  cache: JarCacheManager,
  useCacheAsTargetFile: Boolean,
  layout: BaseLayout?,
  helper: JarPackagerDependencyHelper,
): BuildAssetResult {
  val includedModules = asset.includedModules
  if (asset.isDir) {
    val sourceToMetadata = HashMap<Source, SizeAndHash>()
    for (sources in includedModules.values) {
      for (source in sources) {
        when (source) {
          is DirSource -> {
            sourceToMetadata.computeIfAbsent(source) {
              SizeAndHash(size = 0, hash = computeHashForModuleOutput(it as DirSource))
            }
          }
          is InMemoryContentSource -> {
            // ignore
          }
          else -> error("Unexpected source: $source")
        }
      }
    }
    return BuildAssetResult(sourceToNativeFiles = emptyMap(), sourceToMetadata = sourceToMetadata)
  }

  val sources = if (includedModules.isEmpty()) {
    asset.sources
  }
  else if (asset.sources.isEmpty() && includedModules.size == 1 && includedModules.values.first().size == 1) {
    listOf(includedModules.values.first().first())
  }
  else {
    val sources = ObjectLinkedOpenHashSet<Source>(asset.sources.size + includedModules.values.sumOf { it.size })
    sources.addAll(asset.sources)
    for (moduleSources in includedModules.values) {
      for (source in moduleSources) {
        val old = sources.get(source)
        require(old == null) {
          "Source is duplicated: new $source, old: $old"
        }

        sources.add(source)
      }
    }
    sources
  }

  if (sources.isEmpty()) {
    return emptyBuildJarsResult()
  }

  val nativeFileHandler = if (isCodesignEnabled) NativeFileHandlerImpl(context) else null
  val sourceToMetadata = HashMap<Source, SizeAndHash>(sources.size)

  val file = asset.file
  spanBuilder("build jar")
    .setAttribute("jar", file.toString())
    .setAttribute(AttributeKey.stringArrayKey("sources"), sources.map(Source::toString))
    .use { span ->
      asset.effectiveFile = cache.computeIfAbsent(
        sources = sources,
        targetFile = file,
        nativeFiles = nativeFileHandler?.sourceToNativeFiles,
        span = span,
        producer = object : SourceBuilder {
          override val useCacheAsTargetFile: Boolean
            get() = useCacheAsTargetFile && asset.useCacheAsTargetFile && !asset.relativePath.contains('/')

          override fun updateDigest(digest: HashStream64) {
            val isScramblingEnabled = !context.options.buildStepsToSkip.contains(BuildOptions.SCRAMBLING_STEP)
            digest.putInt(if (isScramblingEnabled) 1 else 0)
            if (layout is PluginLayout) {
              digest.putString(layout.mainModule)
              layout.bundlingRestrictions.updateDigest(digest)
              digest.putUnorderedIterable(layout.pathsToScramble, HashFunnel.forString(), Hashing.xxh3_64())
            }
            else {
              digest.putInt(0)
            }
          }

          override suspend fun produce(targetFile: Path) {
            val addDirEntries = includedModules.any { helper.isTestPluginModule(moduleName = it.key.moduleName, module = null) }
            buildJar(targetFile = targetFile, sources = sources, nativeFileHandler = nativeFileHandler, addDirEntries = addDirEntries)
          }

          override fun consumeInfo(source: Source, size: Int, hash: Long) {
            val old = sourceToMetadata.putIfAbsent(source, SizeAndHash(size, hash))
            require(old == null) {
              buildDuplicateSourceErrorMessage(
                file = file,
                asset = asset,
                source = source,
                old = old!!,
                size = size,
                hash = hash,
                includedModules = includedModules,
              )
            }
          }
        },
      )
    }

  return BuildAssetResult(sourceToNativeFiles = nativeFileHandler?.sourceToNativeFiles ?: emptyMap(), sourceToMetadata)
}

private fun emptyBuildJarsResult() = BuildAssetResult(sourceToNativeFiles = emptyMap(), sourceToMetadata = emptyMap())

private fun checkAssetUniqueness(assets: Collection<AssetDescriptor>) {
  val uniqueFiles = HashMap<Path, List<Source>>(assets.size)
  for (asset in assets) {
    val existing = uniqueFiles.putIfAbsent(asset.file, asset.sources)
    check(existing == null) {
      "File ${asset.file} is already associated." +
      "\nPrevious:\n  ${existing!!.joinToString(separator = "\n  ")}" +
      "\nCurrent:\n  ${asset.sources.joinToString(separator = "\n  ")}"
    }
  }
}

private class NativeFileHandlerImpl(private val context: BuildContext) : NativeFileHandler {
  override val sourceToNativeFiles = HashMap<ZipSource, List<String>>()

  @Suppress("SpellCheckingInspection", "RedundantSuppression")
  override fun isNative(name: String): Boolean {
    return isMacLibrary(name) ||
           name.endsWith(".exe") ||
           name.endsWith(".dll") ||
           name.endsWith("pty4j-unix-spawn-helper") ||
           name.endsWith("icudtl.dat")
  }

  override fun isCompatibleWithTargetPlatform(name: String): Boolean {
    return !isNative(name) || NativeFilesMatcher.isCompatibleWithTargetPlatform(name, context.options.targetOs, context.options.targetArch)
  }

  override suspend fun sign(name: String, dataSupplier: () -> ByteBuffer): Path? {
    if (!context.isMacCodeSignEnabled || context.proprietaryBuildTools.signTool.signNativeFileMode != SignNativeFileMode.ENABLED) {
      return null
    }

    // we allow using .so for macOS binraries (binaries/macOS/libasyncProfiler.so), but removing obvious Linux binaries
    // (binaries/linux-aarch64/libasyncProfiler.so) to avoid detecting by binary content
    if (name.endsWith(".dll") || name.endsWith(".exe") || name.contains("/linux/") || name.contains("/linux-") || name.contains("icudtl.dat")) {
      return null
    }

    val data = dataSupplier()
    data.mark()
    val byteBufferChannel = ByteBufferChannel(data)
    if (byteBufferChannel.DetectFileType().first != FileType.MachO) {
      return null
    }

    data.reset()
    if (isSigned(byteBufferChannel, name)) {
      return null
    }

    data.reset()

    val options = macSigningOptions("application/x-mac-app-bin", context)
    val file = Files.createTempFile(context.paths.tempDir, "", "")
    FileChannel.open(file, WRITE_OPEN_OPTION).use { fileChannel ->
      writeToFileChannelFully(fileChannel, data)
    }
    context.proprietaryBuildTools.signTool.signFiles(listOf(file), context, options)
    if (!context.options.isInDevelopmentMode) {
      check(isSigned(file)) { "Missing signature for $file ($name)" }
    }
    return file
  }
}

suspend fun buildJar(targetFile: Path, moduleNames: List<String>, context: CompilationContext, dryRun: Boolean = false, forTests: Boolean = false) {
  if (dryRun) {
    return
  }

  checkForNoDiskSpace(context) {
    buildJar(
      targetFile = targetFile,
      sources = moduleNames.flatMap { moduleName ->
        val module = context.outputProvider.findRequiredModule(moduleName)
        context.outputProvider.getModuleOutputRoots(module, forTests).mapNotNull { output ->
          createModuleSource(module = module, outputDir = output, excludes = commonModuleExcludes)
        }
      },
    )
  }
}

private fun createModuleSource(module: JpsModule, outputDir: Path, excludes: List<PathMatcher>, filterCacheKey: List<String> = emptyList()): Source? {
  val attributes = try {
    Files.readAttributes(outputDir, BasicFileAttributes::class.java)
  }
  catch (_: FileSystemException) {
    null
  }

  return when {
    attributes != null && attributes.isDirectory -> DirSource(dir = outputDir, excludes = excludes, moduleName = module.name, filterCacheKey = filterCacheKey)
    attributes != null -> ZipSource(
      file = outputDir,
      distributionFileEntryProducer = null,
      filter = createModuleSourcesNamesFilter(excludes),
      moduleName = module.name,
      filterCacheKey = filterCacheKey,
    )
    module.sourceRoots.any { !it.rootType.isForTests } -> error("Module ${module.name} output does not exist: $outputDir")
    else -> null
  }
}

private fun computeDistributionFileEntries(
  asset: AssetDescriptor,
  hasher: HashStream64,
  list: MutableList<DistributionFileEntry>,
  dryRun: Boolean,
  buildAssetResult: BuildAssetResult,
) {
  for ((module, sources) in asset.includedModules) {
    var size = 0
    hasher.reset()
    if (!dryRun) {
      for (source in sources) {
        val info = buildAssetResult.sourceToMetadata.get(source) ?: continue
        size += info.size
        hasher.putInt(size)
        hasher.putLong(info.hash)
      }
    }

    hasher.putInt(sources.size)

    val hash = hasher.asLong
    list.add(
      ModuleOutputEntry(
        path = asset.effectiveFile,
        owner = module,
        size = size,
        hash = hash,
        relativeOutputFile = module.relativeOutputFile,
        reason = module.reason,
        distributionPath = asset.file,
      )
    )
  }

  for (source in asset.sources) {
    if (source is ZipSource) {
      source.distributionFileEntryProducer?.consume(size = 0, hash = 0, targetFile = asset.effectiveFile)?.let(list::add)
    }
    else if (source is LazySource) {
      list.add(CustomAssetEntry(path = asset.effectiveFile, hash = 0, distributionPath = asset.file))
    }
  }
}
