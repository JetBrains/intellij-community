// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "CanConvertToMultiDollarString")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.containers.MultiMap
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.cli.common.arguments.Argument
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.jps.model.JpsKotlinFacetModuleExtension
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.TreeMap
import java.util.TreeSet
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

internal class ModuleList(
  @JvmField val community: List<ModuleDescriptor>,
  @JvmField val ultimate: List<ModuleDescriptor>,
  @JvmField val skipped: List<ModuleDescriptor>,
  /** Mapping from module to data of `plugin.xml` file in its resources for plugins that should be built by `ij_plugin` rule */
  @JvmField val moduleToPluginXmlContentData: Map<ModuleDescriptor, PluginXmlContentData>,
) {
  @JvmField val allModules = community + ultimate + skipped
  val skippedModules = skipped.map { it.module.name }

  private val nameToDescriptor = allModules.associateBy { it.module.name }

  fun getModuleDescriptor(name: String): ModuleDescriptor {
    return nameToDescriptor[name] ?: error("Unknown module name: $name")
  }

  fun hasModule(name: String): Boolean = nameToDescriptor.containsKey(name)

  @JvmField val deps = IdentityHashMap<ModuleDescriptor, ModuleDeps>()
  @JvmField val testDeps = IdentityHashMap<ModuleDescriptor, ModuleDeps>()

  /**
   * Names of plugin descriptor modules and content modules included in `ij_plugin` rule
   */
  val modulesIncludedInIjPluginRule: Set<String> by lazy {
    moduleToPluginXmlContentData.entries.flatMapTo(HashSet()) {
      sequenceOf(it.key.module.name) + it.value.contentModuleNames
    }
  }

}

/**
 * The `config_setting` which matches if the community repository is loaded inside the ultimate repository.
 */
private const val INSIDE_ULTIMATE_CONFIG_SETTING = "@community//build:is_inside_ultimate"

private data class ContentModuleLabel(
  @JvmField val label: String,
  @JvmField val isUltimateModuleOfCommunityPlugin: Boolean,
)

/**
 * Returns the value of the `content_modules` attribute for a community plugin with content modules from the ultimate part.
 * An ultimate module is included only if the community repository is loaded inside the ultimate repository.
 * The ultimate modules are put after the other modules.
 */
private fun contentModulesWithUltimateModules(contentModules: List<ContentModuleLabel>): Renderable {
  val (ultimateModules, communityModules) = contentModules.partition { it.isUltimateModuleOfCommunityPlugin }
  val ultimateModulesSelect = formatSelect(linkedMapOf(
    INSIDE_ULTIMATE_CONFIG_SETTING to ultimateModules.map { it.label }.unsorted(),
    "//conditions:default" to emptyList<String>(),
  ))
  return formatConcatenation(listOf(communityModules.map { it.label }.unsorted(), ultimateModulesSelect))
}

internal data class CustomModuleDescription(
  val moduleName: String,
  val bazelPackage: String,
  val bazelTargetName: String,
  val outputDirectory: String,
  val resources: List<String>,
  val sources: List<String>,
  val additionalProductionTargets: List<String> = emptyList(),
  val additionalProductionJars: List<String> = emptyList(),
) {
  val dependencyLabel = if (bazelPackage.substringAfterLast("/") == bazelTargetName) {
    bazelPackage
  }
  else {
    "${bazelPackage}:${bazelTargetName}"
  }
}

internal val DEFAULT_CUSTOM_MODULES: Map<String, CustomModuleDescription> = listOf(
  CustomModuleDescription(moduleName = "intellij.idea.community.build.zip",
                          bazelPackage = "@community//build",
                          bazelTargetName = "zip",
                          outputDirectory = "out/bazel-out/jvm-fastbuild/bin/external/community+/build",
                          resources = listOf(),
                          sources = listOf("@rules_jvm//zip:zip_sources")),
  CustomModuleDescription(moduleName = "intellij.platform.jps.build.dependencyGraph",
                          bazelPackage = "@community//build",
                          bazelTargetName = "dependency-graph",
                          outputDirectory = "out/bazel-out/jvm-fastbuild/bin/external/community+/build",
                          resources = listOf("@rules_jvm//dependency-graph:dependency-graph_resources"),
                          sources = listOf("@rules_jvm//dependency-graph:dependency-graph_sources")),
  CustomModuleDescription(moduleName = "intellij.platform.jps.build.javac.rt",
                          bazelPackage = "@community//build",
                          bazelTargetName = "build-javac-rt",
                          outputDirectory = "out/bazel-out/jvm-fastbuild/bin/external/community+/build",
                          resources = listOf("@rules_jvm//jps-builders-6:build-javac-rt_resources"),
                          sources = listOf("@rules_jvm//jps-builders-6:build-javac-rt_sources")),
).associateBy { it.moduleName }

/**
 * The modules whose jar holds a gzip member of each `.xml` entry of their provided module libraries, see
 * `gzip_resources.bzl`. A provided library stays off the runtime class path, and no packer merges it.
 */
internal val GZIP_RESOURCE_MODULES: Set<String> = setOf("intellij.database.dialects.minicat")

/**
 * The resource jars that join the module jar besides the resource roots of the module. A tree outside the module
 * package joins the jar through a hand-written `resourcegroup` in the package of the tree.
 */
internal val EXTERNAL_RESOURCE_JARS: Map<String, List<String>> = listOf("ja", "ko", "zh").associate { language ->
  "intellij.localization.$language" to listOf(
    "//:localization_${language}_inspections_intentions",
    "//:localization_${language}_properties",
  )
}

internal enum class SnapshotLibraryMode {
  WRITE_TO_REPO,
  REUSE_GENERATED,
}

@Suppress("ReplaceGetOrSet")
internal class BazelBuildFileGenerator(
  val ultimateRoot: Path?,
  val communityRoot: Path,
  private val project: JpsProject,
  private val projectDir: Path,
  val urlCache: UrlCache,
  val customModules: Map<String, CustomModuleDescription>,
  val snapshotLibraryMode: SnapshotLibraryMode = SnapshotLibraryMode.WRITE_TO_REPO,
  private val kotlincDefaults: KotlincProjectDefaults,
) {
  @JvmField
  val javaExtensionService: JpsJavaExtensionService = JpsJavaExtensionService.getInstance()
  private val projectJavacSettings = javaExtensionService.getCompilerConfiguration(project)
  private val projectCompileExcludes = computeProjectCompileExcludes(projectDir = projectDir, compilerExcludes = projectJavacSettings.compilerExcludes)
  private val projectLanguageLevel: LanguageLevel = run {
    val projectExtension = javaExtensionService.getProjectExtension(project)
                           ?: error("Project-level language version is not defined: JpsJavaProjectExtension is missing on project ${project.name}")
    projectExtension.languageLevel
    ?: error("Project-level language version is not defined: JpsJavaProjectExtension.languageLevel is null on project ${project.name}")
  }

  private val moduleToDescriptor = IdentityHashMap<JpsModule, ModuleDescriptor>()

  fun getKnownModuleDescriptorOrError(module: JpsModule): ModuleDescriptor {
    return moduleToDescriptor.get(module) ?: error("No descriptor for module ${module.name}")
  }

  fun getModuleDescriptor(module: JpsModule): ModuleDescriptor {
    moduleToDescriptor.get(module)?.let {
      return it
    }

    val imlDir = JpsModelSerializationDataService.getBaseDirectory(module)!!.toPath()
    val contentRoots = module.contentRootsList.urls.map { Path.of(JpsPathUtil.urlToPath(it)) }

    var bazelBuildDir = imlDir
    while (!contentRoots.all { it.startsWith(bazelBuildDir) }) {
      bazelBuildDir = bazelBuildDir.parent
                      ?: error(
                        "Unable to find parent for all content roots above $imlDir for module ${module.name}.\n" +
                        "content roots: ${contentRoots.joinToString(" ")}"
                      )
    }

    val customModule = customModules[module.name]
    if (customModule != null) {
      bazelBuildDir = when {
        customModule.bazelPackage.startsWith("@community") -> communityRoot.resolve(customModule.bazelPackage.removePrefix("@community//"))
        else -> ultimateRoot?.resolve(customModule.bazelPackage.removePrefix("//")) ?: error("Custom module ${module.name} is not under community directory")
      }
    }

    val isCommunity = imlDir.startsWith(communityRoot)
    if (isCommunity && !bazelBuildDir.startsWith(communityRoot)) {
      throw IllegalStateException("Computed dir for BUILD.bazel for community module ${module.name} is not under community directory")
    }

    val packageExcludes = computePackageRelativeExcludes(
      projectDir = projectDir,
      bazelBuildFileDir = bazelBuildDir,
      projectCompileExcludes = projectCompileExcludes,
    )
    val resourceDescriptors = computeResources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaResourceRootType.RESOURCE, packageExcludes = packageExcludes)

    val imlFile = imlDir.resolve("${module.name}.iml")
    val moduleContent = ModuleDescriptor(
      imlFile = imlFile,
      module = module,
      contentRoots = contentRoots,
      bazelBuildFileDir = bazelBuildDir,
      isCommunity = isCommunity,
      sources = computeSources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaSourceRootType.SOURCE, packageExcludes = packageExcludes),
      resources = resourceDescriptors,
      testSources = computeSources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaSourceRootType.TEST_SOURCE, packageExcludes = packageExcludes),
      testResources = computeResources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaResourceRootType.TEST_RESOURCE, packageExcludes = packageExcludes),
      targetName = jpsModuleNameToBazelBuildName(module = module, baseBuildDir = bazelBuildDir, communityRoot = communityRoot, ultimateRoot = ultimateRoot),
      relativePathFromProjectRoot = if (isCommunity) {
        bazelBuildDir.relativeTo(communityRoot)
      } else {
        check(ultimateRoot != null) {
          "Trying to process ultimate module $imlFile while ultimate root is not present"
        }
        bazelBuildDir.relativeTo(ultimateRoot)
      },
    )
    moduleToDescriptor.put(module, moduleContent)

    for (element in module.dependenciesList.dependencies) {
      if (element is JpsModuleDependency) {
        val ref = element.moduleReference
        val resolved = requireNotNull(ref.resolve()) {
          "Cannot resolve module ${ref.moduleName} (dependency of '${module.name}') in $projectDir/.idea/modules.xml"
        }
        getModuleDescriptor(resolved)
      }
    }

    return moduleContent
  }

  data class LibraryKey(
    @JvmField
    val container: LibraryContainer,
    @JvmField
    val targetName: String
  )

  val mavenLibraries: LinkedHashMap<LibraryKey, MavenLibrary> = LinkedHashMap()
  val localLibraries: LinkedHashMap<LibraryKey, LocalLibrary> = LinkedHashMap()

  val allLibraries: Collection<Library>
    get() = mavenLibraries.values + localLibraries.values

  val communityOnlyLibraries: Collection<Library>
    get() {
      return allLibraries.filter {
        check(it.target.container == communityLibraries || it.target.container == ultimateLibraries) {
          "Library container ${it.target.container} of ${it.target} is not community or ultimate"
        }

        it.target.container == communityLibraries
      }
    }

  private val providedLibraries: ProvidedLibraries = ProvidedLibraries()
  class ProvidedLibraries {
    private val providedLibraries: MultiMap<Library, LibraryContainer> = MultiMap()
    fun getProvidedContexts(library: Library): Collection<LibraryContainer> = providedLibraries[library]
    fun markAsProvided(library: Library, container: LibraryContainer) { providedLibraries.putValue(library, container) }
  }

  private val generated = IdentityHashMap<ModuleDescriptor, Boolean>()

  private val communityLibraries = LibraryContainer(
    repoLabel = "@lib",
    buildFile = communityRoot.resolve("lib/BUILD.bazel"),
    moduleFile = communityRoot.resolve("lib/MODULE.bazel"),
    isCommunity = true
  )

  private val ultimateLibraries = ultimateRoot?.let { ultimate ->
    LibraryContainer(
      repoLabel = "@ultimate_lib",
      buildFile = ultimate.resolve("lib/BUILD.bazel"),
      moduleFile = ultimate.resolve("lib/MODULE.bazel"),
      isCommunity = false
    )
  }

  fun getLibraryContainer(isCommunity: Boolean): LibraryContainer = if (isCommunity) {
    communityLibraries
  }
  else {
    require(ultimateLibraries != null) {
      "requesting ultimate lib owner, but ultimate root is not present"
    }
    ultimateLibraries
  }

  fun generateLibs(
    jarRepositories: List<JarRepository>,
    m2Repo: Path,
  ) {
    val fileToLabelTracker = LinkedHashMap<Path, MutableMap<String, String>>()
    val fileToUpdater = LinkedHashMap<Path, BazelFileUpdater>()
    for ((libraryContainer, list) in mavenLibraries
      .values
      .groupByTo(
      destination = TreeMap(
        compareBy(
          { it.sectionName },
          { it.buildFile.invariantSeparatorsPathString },
        )
      ),
      keySelector = { it.target.container },
    )) {
      val bazelFileUpdater = fileToUpdater.computeIfAbsent(libraryContainer.buildFile) {
        val updater = BazelFileUpdater(it)
        updater.removeSections("maven-libs")
        updater.removeSections("maven libs")
        updater
      }

      val sortedList = list.sortedBy { it.target.targetName }

      val groupedByTargetName = sortedList.groupBy { it.target.targetName }

      val labelTracker = fileToLabelTracker.computeIfAbsent(libraryContainer.moduleFile) { mutableMapOf() }
      buildFile(out = bazelFileUpdater, sectionName = libraryContainer.sectionName) {
        load("@rules_jvm//:jvm.bzl", "jvm_import")

        for (entry in groupedByTargetName) {
          val libs = entry.value
          if (libs.size > 1) {
            throw IllegalStateException("More than one versions: $entry")
          }
          generateMavenLib(
            lib = libs.single(),
            labelTracker = labelTracker,
            isLibraryProvided = { providedLibraries.getProvidedContexts(it).contains(libraryContainer) },
            libVisibility = libraryContainer.visibility,
          )
        }

        // provided libraries which are defined in community
        // but used as provided libraries in the ultimate part
        if (libraryContainer == ultimateLibraries) {
          val communityLibrariesWithProvidedUsagesInUltimate = mavenLibraries
            .values
            .filter {
              it.target.container == communityLibraries &&
              providedLibraries.getProvidedContexts(it).contains(ultimateLibraries)
            }
            .sortedBy { it.target.targetName }

          for (lib in communityLibrariesWithProvidedUsagesInUltimate) {
            generateProvidedMavenLib(
              lib = lib,
              libVisibility = libraryContainer.visibility,
              targetContainer = communityLibraries,
            )
          }
        }
      }

      generateBazelModuleSectionsForLibs(
        list = sortedList,
        owner = libraryContainer,
        jarRepositories = jarRepositories,
        m2Repo = m2Repo,
        urlCache = urlCache,
        moduleFileToLabelTracker = fileToLabelTracker,
        fileToUpdater = fileToUpdater,
      )
    }

    generateLocalLibs(
      libs = localLibraries.values,
      isLibraryProvided = { lib ->
        providedLibraries
          .getProvidedContexts(lib)
          .contains(lib.target.container)
      },
      fileToUpdater = fileToUpdater,
    )

    for (updater in fileToUpdater.values) {
      updater.save()
    }
  }

  fun addMavenLibrary(lib: MavenLibrary, isProvided: Boolean): MavenLibrary {
    if (lib.target.container == ultimateLibraries) {
      val communityLibrary = mavenLibraries[LibraryKey(communityLibraries, lib.target.targetName)]
      if (communityLibrary != null) {
        if (isProvided) {
          providedLibraries.markAsProvided(communityLibrary, ultimateLibraries)
        }
        return communityLibrary
      }
    }

    val internedLib = mavenLibraries.computeIfAbsent(LibraryKey(lib.target.container, lib.target.targetName)) { lib }
    if (isProvided) {
      providedLibraries.markAsProvided(internedLib, internedLib.target.container)
    }
    return internedLib
  }

  fun addLocalLibrary(lib: LocalLibrary, isProvided: Boolean): LocalLibrary {
    val internedLib = localLibraries.computeIfAbsent(LibraryKey(lib.target.container, lib.target.targetName)) { lib }
    if (isProvided) {
      providedLibraries.markAsProvided(internedLib, internedLib.target.container)
    }
    return internedLib
  }

  fun computeModuleList(m2Repo: Path, skipGenerationOfPluginTargets: Boolean): ModuleList {
    val community = ArrayList<ModuleDescriptor>()
    val ultimate = ArrayList<ModuleDescriptor>()
    val skippedModules = ArrayList<ModuleDescriptor>()
    for (module in project.model.project.modules) {
      val descriptor = getModuleDescriptor(module)

      if (module.name == "intellij.platform.buildScripts.bazel") {
        // Skip bazel generator itself since it's a standalone Bazel project
        skippedModules.add(descriptor)
        continue
      }

      if (module.name == "intellij.tools.build.bazel.jvmIncBuilder" || module.name == "intellij.tools.build.bazel.jvmIncBuilderTests") {
        // Skip bazel generator itself since it's a standalone Bazel project
        skippedModules.add(descriptor)
        continue
      }

      if (descriptor.isCommunity) {
        community.add(descriptor)
      }
      else {
        ultimate.add(descriptor)
      }
    }

    community.sortBy { it.module.name }
    ultimate.sortBy { it.module.name }
    skippedModules.sortBy { it.module.name }

    val allModules = community + ultimate
    val result = ModuleList(
      community = community,
      ultimate = ultimate,
      skipped = skippedModules,
      moduleToPluginXmlContentData = if (!skipGenerationOfPluginTargets) computePluginXmlContentData(allModules) else emptyMap(),
    )
    for (module in allModules) {
      if (module.isTestModule) {
        result.testDeps.put(module, generateDeps(m2Repo = m2Repo, module = module, hasSources = module.testSources.isNotEmpty(), isTest = true, context = this))
      }
      else {
        result.deps.put(module, generateDeps(m2Repo = m2Repo, module = module, hasSources = module.sources.isNotEmpty(), isTest = false, context = this))
      }
    }

    return result
  }

  private fun computePluginXmlContentData(modules: List<ModuleDescriptor>): Map<ModuleDescriptor, PluginXmlContentData> {
    val result = IdentityHashMap<ModuleDescriptor, PluginXmlContentData>()
    for (module in modules) {
      val pluginXmlContentData = module.resources.firstNotNullOfOrNull { descriptor -> parsePluginXmlContent(descriptor) }
      if (pluginXmlContentData != null) {
        result[module] = pluginXmlContentData
      }
    }
    return result
  }

  data class ModuleGenerationResult(
    val moduleBuildFiles: Map<Path, BazelFileUpdater>,
    val moduleTargets: List<ModuleTargets>,
    val generatedBuildFileDirs: Set<Path>,
  )

  private fun validateCustomModules(list: ModuleList) {
    for (customModule in customModules.values) {
      check(list.ultimate.any { it.module.name == customModule.moduleName } ||
            list.community.any { it.module.name == customModule.moduleName }) {
        "Unknown module name: ${customModule.moduleName} in `customModules`"
      }
    }
  }

  fun generateModuleTargets(list: ModuleList, manuallyWrittenAttributes: ManuallyWrittenAttributes, isCommunity: Boolean): List<ModuleTargets> {
    validateCustomModules(list)

    val targetsPerModule = mutableListOf<ModuleTargets>()
    for (module in (if (isCommunity) list.community else list.ultimate)) {
      if (generated.putIfAbsent(module, true) == null) {
        val buildTargetsBazel = BuildFile()
        targetsPerModule.add(buildTargetsBazel.generateBuildTargets(module, list, manuallyWrittenAttributes))
      }
    }
    return targetsPerModule
  }

  fun generateModuleBuildFiles(
    list: ModuleList,
    bazelFilesLoader: BazelFilesLoader,
    manuallyWrittenAttributes: ManuallyWrittenAttributes,
    isCommunity: Boolean,
  ): ModuleGenerationResult {
    validateCustomModules(list)
    val targetsPerModule = mutableListOf<ModuleTargets>()
    val fileToUpdater = LinkedHashMap<Path, BazelFileUpdater>()
    // bazel build file -> (bzlFile (for import) -> already imported symbols)
    val existingLoads = mutableMapOf<Path, MutableMap<String, MutableSet<String>>>()

    fun getOrCreateFileUpdater(module: ModuleDescriptor): BazelFileUpdater {
      val fileUpdater = fileToUpdater.computeIfAbsent(module.bazelBuildFileDir) {
        val fileUpdater = BazelFileUpdater(module.bazelBuildFile, bazelFilesLoader.getBuildFileContent(module.bazelBuildFile))
        fileUpdater.removeSections("build")
        fileUpdater.removeSections("iml ")
        fileUpdater.removeSections("test")
        fileUpdater.removeSections("maven libs of ")
        fileUpdater.removeSections("plugin ")
        
        fileUpdater
      }
      return fileUpdater
    }

    for (module in (if (isCommunity) list.community else list.ultimate)) {
      if (generated.putIfAbsent(module, true) == null) {
        val fileUpdater = getOrCreateFileUpdater(module)
        val buildTargetsBazel = BuildFile()
        val moduleBuildTargets = buildTargetsBazel.generateBuildTargets(module, list, manuallyWrittenAttributes)
        val buildSectionName = "build ${module.module.name}"
        val buildSectionSkipped = fileUpdater.isSectionSkipped(buildSectionName)

        val imlTargetsBazel = BuildFile()
        imlTargetsBazel.exportFile(module.imlFile.relativeTo(module.bazelBuildFileDir).invariantSeparatorsPathString)
        exportDescriptorFiles(module = module, buildFile = imlTargetsBazel, alreadyExported = fileUpdater.handWrittenExportedFiles())

        val testTargetsBazel = BuildFile()
        testTargetsBazel.generateTestTargets(module, list)

        targetsPerModule.add(moduleBuildTargets)

        val existingLoadSymbols = existingLoads.computeIfAbsent(module.bazelBuildFileDir) { HashMap() }
        fun collectLoadStatements(loads: List<LoadStatement>) {
          for (load in loads) {
            existingLoadSymbols.computeIfAbsent(load.bzlFile) {
              mutableSetOf()
            }.addAll(load.symbols)
          }
        }

        if (!buildSectionSkipped) {
          collectLoadStatements(buildTargetsBazel.loadStatements)
          fileUpdater.insertAutoGeneratedSection(
            sectionName = buildSectionName,
            autoGeneratedContent = buildTargetsBazel.render(existingLoads = existingLoadSymbols),
          )
          generatePluginSection(module, list, manuallyWrittenAttributes, fileUpdater, ::collectLoadStatements, existingLoadSymbols)
        }

        fileUpdater.insertAutoGeneratedSection(sectionName = "iml ${module.module.name}", autoGeneratedContent = imlTargetsBazel.render())

        // `plugin-model-tool` writes the `dev ${module.module.name}` section, and this run never touches it.

        val testSectionName = "test ${module.module.name}"
        if (!fileUpdater.isSectionSkipped(testSectionName)) {
          collectLoadStatements(testTargetsBazel.loadStatements)
          fileUpdater.insertAutoGeneratedSection(
            sectionName = testSectionName,
            autoGeneratedContent = testTargetsBazel.render(existingLoads = existingLoadSymbols),
          )
        }
        fileUpdater.appendLoadSymbols(existingLoadSymbols)
      }
    }

    val generatedBuildFileDirs = fileToUpdater.keys.toSet()
    for (module in list.skipped.filter { it.isCommunity == isCommunity }) {
      fileToUpdater.computeIfAbsent(module.bazelBuildFileDir) {
        val buildFile = it.resolve("BUILD.bazel")
        check(buildFile.isRegularFile()) {
          "Missing original BUILD.bazel owner for skipped module ${module.module.name}: $buildFile"
        }
        BazelFileUpdater(buildFile)
      }
    }

    return ModuleGenerationResult(
      moduleBuildFiles = fileToUpdater.mapValues { it.value },
      moduleTargets = targetsPerModule,
      generatedBuildFileDirs = generatedBuildFileDirs,
    )
  }

  /**
   * Generates the `plugin` section with the `ij_plugin` target of [module] if the module has a marked `plugin.xml`.
   *
   * In the standalone community repository, a community plugin can register a content module from the ultimate part.
   * The labels of such modules are known only to the generator in the ultimate repository, so the existing section is kept as is.
   */
  private fun generatePluginSection(
    module: ModuleDescriptor,
    list: ModuleList,
    manuallyWrittenAttributes: ManuallyWrittenAttributes,
    fileUpdater: BazelFileUpdater,
    collectLoadStatements: (List<LoadStatement>) -> Unit,
    existingLoadSymbols: Map<String, Set<String>>,
  ) {
    val pluginXmlContentData = list.moduleToPluginXmlContentData[module] ?: return
    val sectionName = "plugin ${module.module.name}"
    if (fileUpdater.isSectionSkipped(sectionName)) {
      return
    }

    val unresolvedContentModules = if (ultimateRoot == null) pluginXmlContentData.contentModuleNames.filterNot { list.hasModule(it) } else emptyList()
    if (unresolvedContentModules.isNotEmpty()) {
      val existingContent = fileUpdater.getOriginalSectionContent(sectionName)
                            ?: error("Content modules $unresolvedContentModules of plugin ${module.module.name} are not in the community project. " +
                                     "Run ./build/jpsModelToBazel.cmd in the ultimate repository to generate the '$sectionName' section in ${module.bazelBuildFile}")
      fileUpdater.insertAutoGeneratedSection(sectionName = sectionName, autoGeneratedContent = existingContent)
      return
    }

    val pluginTargetBazel = BuildFile()
    pluginTargetBazel.generatePluginTarget(module, pluginXmlContentData, list, manuallyWrittenAttributes)
    collectLoadStatements(pluginTargetBazel.loadStatements)
    fileUpdater.insertAutoGeneratedSection(sectionName = sectionName, autoGeneratedContent = pluginTargetBazel.render(existingLoads = existingLoadSymbols))
  }

  fun save(fileToUpdater: Map<Path, BazelFileUpdater>) {
    for (updater in fileToUpdater.values) {
      updater.save()
    }
  }

  fun getBazelDependencyLabel(module: ModuleDescriptor, dependent: ModuleDescriptor, targetNameSuffix: String = ""): String {
    val customModule = customModules[module.module.name]
    if (customModule != null && targetNameSuffix.isEmpty()) {
      return customModule.dependencyLabel
    }

    val dependentIsCommunity = dependent.isCommunity
    val targetName = module.targetName + (if (module.isTestModule) TEST_LIB_NAME_SUFFIX else "") + targetNameSuffix
    if (!dependentIsCommunity && module.isCommunity) {
      require(module.isCommunity)

      val path = checkAndGetRelativePath(communityRoot, module.bazelBuildFileDir).invariantSeparatorsPathString
      return if (path.substringAfterLast('/') == targetName) {
        "@community//$path"
      }
      else {
        "@community//$path:$targetName"
      }
    }

    if (dependentIsCommunity) {
      require(module.isCommunity) {
        "Community module ${dependent.module.name} cannot depend on ultimate module ${module.module.name}"
      }
    }

    val relativeToCommunityPath = if (module.bazelBuildFileDir.startsWith(communityRoot)) {
      checkAndGetRelativePath(communityRoot, module.bazelBuildFileDir).invariantSeparatorsPathString
    } else {
      null
    }

    val relativeToUltimatePath = if (ultimateRoot != null) {
      checkAndGetRelativePath(ultimateRoot, module.bazelBuildFileDir).invariantSeparatorsPathString
    } else {
      null
    }

    val path = when {
      // relativeToCommunityPath == null: `module` is ultimate module
      relativeToCommunityPath == null -> {
        check(relativeToUltimatePath != null) {
          "Trying to process ultimate (non-community) module ${module.module.name} while ultimate root is not present"
        }

        require(!dependentIsCommunity) {
          "Community module ${dependent.module.name} cannot depend on ultimate module ${module.module.name}"
        }

        "//$relativeToUltimatePath"
      }
      dependentIsCommunity -> "//$relativeToCommunityPath"
      else -> "@community//$relativeToCommunityPath"
    }

    val result = path + (if (module.bazelBuildFileDir.fileName.toString() == targetName) "" else ":$targetName")
    return result
  }

  internal data class ModuleTargets(
    val moduleDescriptor: ModuleDescriptor,
    val productionTargets: List<String>,
    val productionJars: List<String>,
    val testTargets: List<String>,
    val testJars: List<String>,
    val pluginDistributionTarget: PluginDistributionTarget?,
  )

  internal data class PluginDistributionTarget(
    @JvmField val target: String,
    @JvmField val distributionDirectory: String,
  )

  private fun BuildFile.generateTestTargets(moduleDescriptor: ModuleDescriptor, moduleList: ModuleList) {
    if (moduleDescriptor.testSources.isEmpty()) {
      return
    }

    load("@community//build:tests-options.bzl", "jps_test")

    val mainModuleLabel = getTestClasspathModule(moduleDescriptor, moduleList)?.let { mainModule ->
      getBazelDependencyLabel(mainModule, moduleDescriptor)
    }

    val testLibTargetName = "${moduleDescriptor.targetName}$TEST_LIB_NAME_SUFFIX"

    target("jps_test") {
      option("name", "${moduleDescriptor.targetName}_test")
      option("runtime_deps", listOfNotNull(":$testLibTargetName", mainModuleLabel))
    }
  }

  private fun BuildFile.generateBuildTargets(moduleDescriptor: ModuleDescriptor, moduleList: ModuleList, manuallyWrittenAttributes: ManuallyWrittenAttributes): ModuleTargets {
    val module = moduleDescriptor.module
    val customModule = customModules[moduleDescriptor.module.name]
    val jvmTarget = getLanguageLevel(module)
    val kotlincOptionsLabel = computeKotlincOptions(buildFile = this, module = moduleDescriptor, jvmTarget = jvmTarget, kotlincDefaults = kotlincDefaults)
                              ?: (if (jvmTarget == kotlincDefaults.jvmTarget) null else "@community//:k$jvmTarget")
    // TODO: require kotlincOptionsLabel == null || sources.isNotEmpty()
    val javacOptionsLabel = computeJavacOptions(moduleDescriptor, jvmTarget)
    // TODO: require javacOptionsLabel == null || sources.isNotEmpty()

    val sources = if (moduleDescriptor.isTestModule) moduleDescriptor.testSources else moduleDescriptor.sources
    val resources = if (moduleDescriptor.isTestModule) moduleDescriptor.testResources else moduleDescriptor.resources
    var deps = if (moduleDescriptor.isTestModule) moduleList.testDeps.get(moduleDescriptor) else moduleList.deps.get(moduleDescriptor)

    var directResources: ResourcesInfo? = null
    val resourceJarTargets = mutableListOf<BazelLabel>()
    if (customModule == null && resources.isNotEmpty()) {
      val result = generateResources(module = moduleDescriptor, forTests = moduleDescriptor.isTestModule)
      directResources = result.resources
      resourceJarTargets.addAll(result.resourceJarsTargets)
    }

    load("@rules_jvm//:jvm.bzl", "jvm_library")

    if (deps != null && deps.provided.isNotEmpty()) {
      val wrappedProvidedLibs = generateProvidedLibs(deps.provided)
      deps = deps.copy(deps = deps.deps + wrappedProvidedLibs.values, exports = deps.exports.map { export -> wrappedProvidedLibs.get(export) ?: export })
    }

    val mustGenerateFleetPluginServicesResources = moduleDescriptor.isFleetModule() && hasRhizomeDbOrPluginDependency(moduleList, moduleDescriptor)
    if (mustGenerateFleetPluginServicesResources) {
      val codegenTargetName = "${moduleDescriptor.targetName}_fleet_plugin_services_resources"
      val ruleName = "fleet_plugin_services_resources"
      load("@community//fleet/build:fleet.bzl", ruleName)
      target(ruleName) {
        option("name", codegenTargetName)
        option("srcs", sourcesToGlob(sources, moduleDescriptor))
        if (deps?.deps?.isNotEmpty() == true) {
          option("deps", deps.deps.sorted())
        }
      }
      resourceJarTargets.add(BazelLabel(label = codegenTargetName, module = null))
    }

    if (module.name in GZIP_RESOURCE_MODULES) {
      val provided = deps?.providedModuleLibraries.orEmpty()
      require(provided.isNotEmpty()) { "Module ${module.name} gzips its provided module libraries, but it has none" }
      val gzipTargetName = "${moduleDescriptor.targetName}_gzip_resources"
      load("@community//platform/build-scripts/bazel-rules:gzip_resources.bzl", "gzip_resources")
      target("gzip_resources") {
        option("name", gzipTargetName)
        option("srcs", provided.unsorted())
      }
      resourceJarTargets.add(BazelLabel(label = gzipTargetName, module = null))
    }

    val useIjPluginModule = shouldUseIjPluginModuleFunction(moduleDescriptor, moduleList, manuallyWrittenAttributes) && !moduleDescriptor.isTestModule
    val moduleTargetType: String
    if (useIjPluginModule) {
      load("@community//platform/build-scripts/bazel-rules:ij_plugin_module.bzl", "ij_plugin_module")
      moduleTargetType = "ij_plugin_module"
    }
    else {
      moduleTargetType = "jvm_library"
    }
    val targetName = moduleDescriptor.targetName + (if (moduleDescriptor.isTestModule) TEST_LIB_NAME_SUFFIX else "")
    val targetLabel = BazelLabel(targetName, moduleDescriptor)
    target(moduleTargetType) {
      option("name", targetName)
      if (moduleDescriptor.isTestModule) {
        option("testonly", true)
      }

      if (customModule == null) {
        option("srcs", sourcesToGlob(sources, moduleDescriptor))
      }
      else if (customModule.sources.isNotEmpty()) {
        option("srcs", customModule.sources)
      }

      @Suppress("CascadeIf")
      if (module.name == "fleet.util.multiplatform" || module.name == "intellij.platform.multiplatformSupport") {
        option("exported_compiler_plugins", listOf("@community//fleet/compiler-plugins/expects:expects-plugin"))
      }
      else if (module.name == "fleet.rpc") {
        option("exported_compiler_plugins", listOf("@community//fleet/compiler-plugins/rpc:rpc-plugin"))
      }
      else if (module.name == "fleet.noria.cells") {
        option("exported_compiler_plugins", listOf("@community//fleet/compiler-plugins/noria:noria-plugin"))
      }
      else if (module.name == "intellij.libraries.compose.runtime.desktop") {
        option("exported_compiler_plugins", listOf("@lib//:compose-plugin"))
      }
      else if (module.name == "fleet.compose.dependencies") {
        option("exported_compiler_plugins", listOf("@lib//:compose-plugin"))
      }

      if (deps != null && deps.associates.isNotEmpty()) {
        option("associates", deps.associates.sorted())
      }

      if (javacOptionsLabel != null && sources.isNotEmpty()) {
        option("javac_opts", javacOptionsLabel)
      }

      if (kotlincOptionsLabel != null && sources.isNotEmpty()) {
        option("kotlinc_opts", kotlincOptionsLabel)
      }

      // the associates carry the module name
      if (deps == null || deps.associates.isEmpty()) {
        option("module_name", module.name)
      }

      if (useIjPluginModule) {
        insertManuallyWrittenAttributes(manuallyWrittenAttributes.getManuallyWrittenAttributes(moduleDescriptor, NON_CLASSPATH_DATA_ATTRIBUTE))
      }

      if (useIjPluginModule && deps != null && deps.packedDeps.isNotEmpty()) {
        option("packed_deps", deps.packedDeps.unsorted())
      }

      if (deps != null && deps.plugins.isNotEmpty()) {
        option("plugins", deps.plugins.sorted())
      }

      if (customModule == null) {
        val resourceJars = resourceJarTargets.map { ":${it.label}" } + EXTERNAL_RESOURCE_JARS.get(module.name).orEmpty()
        if (resourceJars.isNotEmpty()) {
          option("resource_jars", resourceJars)
        }
        if (directResources != null) {
          option("resource_strip_prefix", directResources.stripPrefix)
          option("resources", glob(directResources.fileGlobs, exclude = directResources.excludes, allowEmpty = directResources.allowEmpty))
        }
      }
      else if (customModule.resources.isNotEmpty()) {
        option("resource_jars", customModule.resources)
      }

      visibility(arrayOf("//visibility:public"))

      if (deps != null && deps.exports.isNotEmpty()) {
        option("exports", deps.exports.unsorted())
      }

      if (deps != null && deps.runtimeDeps.isNotEmpty()) {
        option("runtime_deps", deps.runtimeDeps.unsorted())
      }

      if (deps != null && deps.deps.isNotEmpty()) {
        option("deps", deps.deps.unsorted())
      }
    }

    val relativePathFromRoot = moduleDescriptor.relativePathFromProjectRoot.invariantSeparatorsPathString
    val bazelModuleRelativePath = if (moduleDescriptor.isCommunity) {
      if (relativePathFromRoot == "community") {
        ""
      }
      else {
        relativePathFromRoot.removePrefix("community/")
      }
    }
    else {
      relativePathFromRoot
    }

    val packagePrefix = when {
      customModule != null -> customModule.bazelPackage
      moduleDescriptor.isCommunity -> "@community//${bazelModuleRelativePath}"
      else -> "//${bazelModuleRelativePath}"
    }

    val outputDirectory = when {
      customModule != null -> customModule.outputDirectory
      moduleDescriptor.isCommunity -> "out/bazel-out/jvm-fastbuild/bin/external/community+/$bazelModuleRelativePath"
      else -> "out/bazel-out/jvm-fastbuild/bin/$bazelModuleRelativePath"
    }

    fun addPackagePrefix(target: BazelLabel): String =
      if (target.label.startsWith("//") || target.label.startsWith("@")) target.label else "$packagePrefix:${target.label}"

    fun getJarLocation(jarName: BazelLabel) = when {
      // full target name instead of just jar for intellij.dotenv.*
      // like @community//plugins/env-files-support:dotenv-go_resources
      jarName.label.startsWith("@community//") ->
        "out/bazel-out/jvm-fastbuild/bin/external/community+/${jarName.label.substringAfter("@community//").replace(':', '/')}.jar"
      else -> "$outputDirectory/${jarName.label}.jar"
    }

    // the `ij_plugin` target itself is generated by `generatePluginTarget`
    val pluginDistributionTarget = moduleList.moduleToPluginXmlContentData[moduleDescriptor]?.let {
      val label = BazelLabel(getPluginTargetName(moduleDescriptor), moduleDescriptor)
      PluginDistributionTarget(
        target = addPackagePrefix(label),
        distributionDirectory = "$outputDirectory/${generateNameForPluginDirectory(moduleDescriptor.module.name)}",
      )
    }

    val targets = listOf(addPackagePrefix(targetLabel))
    val jars = listOf(getJarLocation(targetLabel))
    return ModuleTargets(
      moduleDescriptor = moduleDescriptor,
      productionTargets = (if (moduleDescriptor.isTestModule) emptyList() else targets) + customModule?.additionalProductionTargets.orEmpty(),
      productionJars = (if (moduleDescriptor.isTestModule) emptyList() else jars) + customModule?.additionalProductionJars.orEmpty(),
      testTargets = if (moduleDescriptor.isTestModule) targets else emptyList(),
      testJars = if (moduleDescriptor.isTestModule) jars else emptyList(),
      pluginDistributionTarget = pluginDistributionTarget,
    )
  }

  /**
   * Generates the `ij_plugin` target of [moduleDescriptor], which has a marked `plugin.xml` with [pluginXmlContentData].
   * All content modules must be in [moduleList].
   */
  private fun BuildFile.generatePluginTarget(
    moduleDescriptor: ModuleDescriptor,
    pluginXmlContentData: PluginXmlContentData,
    moduleList: ModuleList,
    manuallyWrittenAttributes: ManuallyWrittenAttributes,
  ) {
    load("@community//platform/build-scripts/bazel-rules:ij_plugin.bzl", "ij_plugin")
    target("ij_plugin") {
      option("name", getPluginTargetName(moduleDescriptor))
      option("descriptor_module", ":${moduleDescriptor.targetName}${getPluginModuleTargetNameSuffix(moduleDescriptor, moduleList, manuallyWrittenAttributes)}")
      if (pluginXmlContentData.contentModuleNames.isNotEmpty()) {
        val contentModules = pluginXmlContentData.contentModuleNames.map {
          val contentModuleDescriptor = moduleList.getModuleDescriptor(it)
          val contentModuleTargetNameSuffix = getPluginModuleTargetNameSuffix(contentModuleDescriptor, moduleList, manuallyWrittenAttributes)
          ContentModuleLabel(
            label = getContentModuleLabel(contentModuleDescriptor, moduleDescriptor, contentModuleTargetNameSuffix),
            isUltimateModuleOfCommunityPlugin = moduleDescriptor.isCommunity && !contentModuleDescriptor.isCommunity,
          )
        }
        if (contentModules.any { it.isUltimateModuleOfCommunityPlugin }) {
          option("content_modules", contentModulesWithUltimateModules(contentModules))
        }
        else {
          option("content_modules", contentModules.map { it.label }.unsorted())
        }
      }
      // the distribution is consumed from other packages: by the build scripts building this plugin by Bazel,
      // and by the test comparing the result with the build scripts' own output
      visibility(arrayOf("//visibility:public"))
    }
  }

  private fun getPluginTargetName(moduleDescriptor: ModuleDescriptor): String = moduleDescriptor.targetName + "_plugin"

  /**
   * Returns the label of [contentModule] for the `content_modules` attribute of the `ij_plugin` target of [pluginModule].
   * Unlike a usual dependency, a community plugin can register a content module from the ultimate part.
   * Such a module is referenced from the main repository, which is the ultimate one when the label is used.
   */
  private fun getContentModuleLabel(contentModule: ModuleDescriptor, pluginModule: ModuleDescriptor, targetNameSuffix: String): String {
    if (pluginModule.isCommunity && !contentModule.isCommunity) {
      // the label as it is written in the ultimate part
      return "@@" + getBazelDependencyLabel(contentModule, dependent = contentModule, targetNameSuffix = targetNameSuffix)
    }
    return getBazelDependencyLabel(contentModule, pluginModule, targetNameSuffix = targetNameSuffix)
  }

  /**
   * Determines whether `ij_plugin_module` rule should be used for the given module or the default `jvm_library` rule is enough.
   * Currently, `ij_plugin_module` is used if and only if `packed_deps` attribute (that is generated automatically) or `non_classpath_data` (that is written manually) is present.
   */
  private fun shouldUseIjPluginModuleFunction(
    moduleDescriptor: ModuleDescriptor,
    moduleList: ModuleList,
    manuallyWrittenAttributes: ManuallyWrittenAttributes,
  ): Boolean {
    if (manuallyWrittenAttributes.getManuallyWrittenAttributes(moduleDescriptor, NON_CLASSPATH_DATA_ATTRIBUTE).isNotEmpty()) {
      return true
    }
    val deps = moduleList.deps[moduleDescriptor]
    return moduleDescriptor.module.name in moduleList.modulesIncludedInIjPluginRule && deps != null && deps.packedDeps.isNotEmpty()
  }

  private fun ModuleDescriptor.isFleetModule(): Boolean {
    return module.name.startsWith("fleet.")
  }

  private fun hasRhizomeDbOrPluginDependency(
    moduleList: ModuleList,
    moduleDescriptor: ModuleDescriptor,
  ): Boolean {
    val moduleDeps = moduleList.deps.get(moduleDescriptor) ?: return false
    val allDependencies = moduleDeps.deps + moduleDeps.provided
    return allDependencies.any { it.module?.module?.name in setOf("fleet.rhizomedb", "fleet.kernel.plugins") }
  }

  private fun Target.sourcesToGlob(sources: List<SourceDirDescriptor>, module: ModuleDescriptor): Renderable {
    return glob(sources.flatMap { it.glob }, exclude = sources.flatMap { it.excludes })
  }

  private fun BuildFile.generateProvidedLibs(providedLibs: List<BazelLabel>): Map<BazelLabel, BazelLabel> {
    load("@rules_jvm//:jvm.bzl", "jvm_provided_library")

    val labelToName = getUniqueSegmentName(providedLibs.map { it.label })
    return providedLibs.associateWith { label ->
      val name = labelToName.get(label.label) + "_provided"
      target("jvm_provided_library") {
        option("name", name)
        if (label.module?.isTestModule == true) {
          option("testonly", true)
        }
        option("lib", label)
      }
      BazelLabel(":$name", null)
    }
  }

  private data class ResourcesInfo(
    val fileGlobs: List<String>,
    val stripPrefix: String,
    val excludes: List<String>,
    val allowEmpty: Boolean,
  )

  private data class GenerateResourcesResult(
    val resources: ResourcesInfo?,
    val resourceJarsTargets: List<BazelLabel>,
  )

  private fun BuildFile.generateResources(
    module: ModuleDescriptor,
    forTests: Boolean,
  ): GenerateResourcesResult {
    if (module.sources.isEmpty() && module.testSources.isEmpty() && !(module.module.dependenciesList.dependencies.none { element ->
        when (element) {
          is JpsModuleDependency, is JpsLibraryDependency -> {
            val scope = javaExtensionService.getDependencyExtension(element)?.scope
            scope != JpsJavaDependencyScope.TEST && scope != JpsJavaDependencyScope.RUNTIME
          }
          else -> false
        }
      })) {
      LOG.log(Level.FINE, "Expected no module/library non-runtime dependencies for resource-only module for ${module.module.name}")
    }

    val resources = if (forTests) module.testResources else module.resources
    check(resources.isNotEmpty()) {
      "This function should be called only for modules with resources (module=${module.module.name}, forTests=$forTests)"
    }

    if (!module.isCommunity && module.targetName.startsWith("dotenv-") && resources[0].baseDirectory.contains("community")) {
      val productionLabel = "@community//plugins/env-files-support/${module.targetName.removePrefix("dotenv-")}:${module.targetName.removePrefix("dotenv-")}"
      val fixedTargetsList = if (forTests) {
        listOf(BazelLabel("$productionLabel$TEST_RESOURCES_TARGET_SUFFIX", module))
      }
      else {
        listOf(BazelLabel("$productionLabel$PRODUCTION_RESOURCES_TARGET_SUFFIX", module))
      }
      return GenerateResourcesResult(resources = null, resourceJarsTargets = fixedTargetsList)
    }

    val targetNameSuffix = if (forTests) TEST_RESOURCES_TARGET_SUFFIX else PRODUCTION_RESOURCES_TARGET_SUFFIX
    if (resources.isEmpty()) return GenerateResourcesResult(null, emptyList())
    val directResources = resources
      .singleOrNull()
      ?: resources.find { it.root.containsXmlDescriptors() }
      ?: resources.first()
    val resourceJarsTargets = resources
      .minus(directResources)
      .withIndex()
      .map { (i, resource) ->
        val name = "${module.targetName}$targetNameSuffix" + (if (i == 0) "" else "_$i")
        target("resourcegroup") {
          option("name", name)
          option("srcs", glob(resource.files, exclude = resource.excludes, allowEmpty = resource.allowEmpty))
          if (resource.baseDirectory.isNotEmpty()) {
            option("strip_prefix", resource.baseDirectory)
          }
          if (resource.relativeOutputPath.isNotEmpty()) {
            option("add_prefix", resource.relativeOutputPath)
          }
        }

        BazelLabel(name, module)
      }

    if (resourceJarsTargets.isNotEmpty()) load("@rules_jvm//:jvm.bzl", "resourcegroup")
    return GenerateResourcesResult(
      resources = ResourcesInfo(
        fileGlobs = directResources.files,
        stripPrefix = directResources.baseDirectory,
        excludes = directResources.excludes,
        allowEmpty = directResources.allowEmpty,
      ),
      resourceJarsTargets = resourceJarsTargets
    )
  }

  private fun Path.containsXmlDescriptors(): Boolean = exists() && walk().any { it.extension == "xml" && it.readText().contains("<idea-plugin") }

  private fun BuildFile.computeJavacOptions(module: ModuleDescriptor, jvmTarget: String): String? {
    val extraJavacOptions = projectJavacSettings.currentCompilerOptions.ADDITIONAL_OPTIONS_OVERRIDE.get(module.module.name) ?: ""
    val exports = addExportsRegex.findAll(extraJavacOptions).map { it.groupValues[1] + "=ALL-UNNAMED" }.toList()
    val noProc = !projectJavacSettings.getAnnotationProcessingProfile(module.module).isEnabled
    if (exports.isEmpty() && noProc) {
      return null
    }

    load("@rules_jvm//:jvm.bzl", "kt_javac_options")
    val customJavacOptionsName = "custom-javac-options"
    target("kt_javac_options") {
      option("name", customJavacOptionsName)
      // release is not compatible with --add-exports (*** java)
      require(jvmTarget == "25") {
        "failed requirement: jvmTarget == \"25\" for module ${module.module.name}"
      }
      option("add_exports", exports)
      option("no_proc", noProc)
      option("warn", "off")
      option("x_ep_disable_all_checks", true)
    }
    return ":$customJavacOptionsName"
  }

  private fun getLanguageLevel(module: JpsModule): String {
    val languageLevel = javaExtensionService.getLanguageLevel(module) ?: projectLanguageLevel
    return when (languageLevel) {
      LanguageLevel.JDK_1_8 -> "8"
      LanguageLevel.JDK_11 -> "11"
      LanguageLevel.JDK_17 -> "17"
      LanguageLevel.JDK_21 -> "21"
      LanguageLevel.JDK_25 -> "25"
      else -> error("Unsupported language level: $languageLevel for module ${module.name}")
    }
  }

  private fun getPluginModuleTargetNameSuffix(
    module: ModuleDescriptor,
    moduleList: ModuleList,
    manuallyWrittenAttributes: ManuallyWrittenAttributes
  ): String {
    //follows the logic in `ij_plugin_module` function in ij_plugin_module.bzl
    return if (shouldUseIjPluginModuleFunction(module, moduleList, manuallyWrittenAttributes)) "_plugin_module" else ""
  }

  private fun jpsModuleNameToBazelBuildName(module: JpsModule, baseBuildDir: Path, communityRoot: Path, ultimateRoot: Path?): @NlsSafe String {
    val moduleName = module.name
    val customModule = customModules.get(moduleName)
    if (customModule != null) {
      return customModule.bazelTargetName
    }

    val baseDirFilename = if (baseBuildDir == communityRoot || baseBuildDir == ultimateRoot) null else baseBuildDir.fileName.toString()
    if (baseDirFilename != null && baseDirFilename != "resources" &&
        (moduleName.endsWith(".$baseDirFilename") || (camelToSnakeCase(moduleName, '-')).endsWith(".$baseDirFilename"))) {
      return baseDirFilename
    }

    val result = moduleName
      .removePrefix("intellij.platform.")
      .removePrefix("intellij.idea.community.")
      .removePrefix("intellij.")

    val parentDirDirName = when {
      // In a community-only checkout, root-level module names still keep the `main.` segment.
      baseBuildDir == communityRoot || baseBuildDir == ultimateRoot -> null
      baseBuildDir.parent == ultimateRoot -> "idea"
      baseBuildDir.parent == communityRoot -> "community"
      else -> baseBuildDir.parent?.fileName.toString()
    }

    return result
      .let { if (parentDirDirName != null) it.removePrefix("$parentDirDirName.") else it }
      .replace('.', '-')
  }
}

private fun getTestClasspathModule(module: ModuleDescriptor, moduleList: ModuleList): ModuleDescriptor? {
  val moduleName = module.module.name

  val mainModuleName = when {
    moduleName.startsWith("kotlin.jvm-debugger.") -> "intellij.idea.community.main"
    moduleName.startsWith("intellij.kotlin.jvm.debugger.") -> "intellij.idea.community.main"
    else -> null
  }

  return mainModuleName?.let { moduleList.getModuleDescriptor(it) }
}

private fun computeSources(
  module: JpsModule,
  contentRoots: List<Path>,
  bazelBuildDir: Path,
  type: JpsModuleSourceRootType<*>,
  packageExcludes: List<String>,
): List<SourceDirDescriptor> {
  return module.sourceRoots.asSequence()
    .filter { it.rootType == type }
    .flatMap { root ->
      val rootDir = root.path
      var prefix = resolveRelativeToBazelBuildFileDirectory(childDir = rootDir, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, module = module).invariantSeparatorsPathString
      val rootPrefix = prefix
      if (prefix.isNotEmpty()) {
        prefix += "/"
      }

      val excludes = mutableListOf<String>()
      for (excludedUrl in module.excludeRootsList.urls) {
        val excludedDir = Path.of(JpsPathUtil.urlToPath(excludedUrl))
        require(excludedDir.isAbsolute)
        if (excludedDir.startsWith(rootDir)) {
          val relativeExcludedPath = resolveRelativeToBazelBuildFileDirectory(childDir = excludedDir, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, module = module)
            .invariantSeparatorsPathString
          require(relativeExcludedPath.isNotEmpty() && !relativeExcludedPath.endsWith('/'))
          excludes.add("$relativeExcludedPath/**/*")
        }
      }
      excludes.addAll(compileExcludesForRoot(packageExcludes = packageExcludes, rootPrefix = rootPrefix))
      val sourceExcludes = excludes.distinct()

      if (type == JavaSourceRootType.SOURCE || type == JavaSourceRootType.TEST_SOURCE) {
        if (!(root.properties as JavaSourceRootProperties).isForGeneratedSources) {
          sequenceOf(SourceDirDescriptor(glob = listOf("$prefix**/*.kt", "$prefix**/*.java", "$prefix**/*.form"), excludes = sourceExcludes))
        }
        else {
          sequenceOf(SourceDirDescriptor(glob = listOf("$prefix**/*.kt", "$prefix**/*.java"), excludes = sourceExcludes))
        }
      }
      else {
        sequenceOf(SourceDirDescriptor(glob = listOf("$prefix**/*"), excludes = sourceExcludes))
      }
    }
    .toList()
}

private fun computeResources(
  module: JpsModule,
  contentRoots: List<Path>,
  bazelBuildDir: Path,
  type: JavaResourceRootType,
  packageExcludes: List<String>,
): List<ResourceDescriptor> {
  return module.sourceRoots
    .asSequence()
    .filter { it.rootType == type }
    .map { root ->
      val prefix = resolveRelativeToBazelBuildFileDirectory(root.path, contentRoots, bazelBuildDir, module = module).invariantSeparatorsPathString
      val globPrefix = if (prefix.isEmpty()) "" else "$prefix/"
      val excludes = compileExcludesForRoot(packageExcludes = packageExcludes, rootPrefix = prefix)
      val iconRobots = type == JavaResourceRootType.RESOURCE && hasIconRobots(root.path)
      val relativeOutputPath = (root.properties as JavaResourceRootProperties).relativeOutputPath
      ResourceDescriptor(
        baseDirectory = prefix,
        files = listOf("$globPrefix**/*"),
        relativeOutputPath = relativeOutputPath,
        root = root.path,
        excludes = if (iconRobots) excludes + "$globPrefix**/$ICON_ROBOTS_FILE_NAME" else excludes,
        allowEmpty = excludes.isNotEmpty() || iconRobots,
      )
    }
    .toList()
}

/**
 * The icon-class generator reads `icon-robots.txt`. No jar ships it, so the resource glob leaves it out.
 * The converter adds the exclude only to a production resource root that holds such a file.
 * The exclude always comes with `allow_empty`, so untracked build output under the root does not change the generated file.
 */
private const val ICON_ROBOTS_FILE_NAME = "icon-robots.txt"

/** Tells if the resource root holds an `icon-robots.txt` file. */
private fun hasIconRobots(root: Path): Boolean {
  return root.isDirectory() && root.walk().any { it.fileName.toString() == ICON_ROBOTS_FILE_NAME }
}

private fun compileExcludesForRoot(packageExcludes: List<String>, rootPrefix: String): List<String> {
  if (packageExcludes.isEmpty() || rootPrefix.isEmpty()) {
    return packageExcludes
  }

  return packageExcludes.filter { pattern ->
    pattern == "**/*" ||
    pattern == rootPrefix ||
    pattern == "$rootPrefix/**/*" ||
    pattern.startsWith("$rootPrefix/")
  }
}

private fun checkAndGetRelativePath(parentDir: Path, childDir: Path): Path {
  require(childDir.startsWith(parentDir)) {
    "$childDir must be a child of parentDir $parentDir"
  }
  return parentDir.relativize(childDir)
}

private fun resolveRelativeToBazelBuildFileDirectory(childDir: Path, contentRoots: List<Path>, bazelBuildDir: Path, module: JpsModule): Path {
  require(childDir.isAbsolute && contentRoots.all { it.isAbsolute })

  var found: Path? = null
  for (contentRoot in contentRoots) {
    if (childDir.startsWith(contentRoot)) {
      require(found == null) {
        "$childDir must exist only in one location, found $found and $contentRoot"
      }
      found = contentRoot
    }
  }
  require(found != null) {
    "$childDir must be a child of contentRoots ${contentRoots.joinToString()} (module=${module.name})"
  }

  return bazelBuildDir.relativize(childDir)
}

private fun computeKotlincOptions(buildFile: BuildFile, module: ModuleDescriptor, jvmTarget: String, kotlincDefaults: KotlincProjectDefaults): String? {
  val kotlinFacetModuleExtension = module.module.container.getChild(JpsKotlinFacetModuleExtension.KIND) ?: return null
  val mergedCompilerArguments = kotlinFacetModuleExtension.settings.mergedCompilerArguments as? K2JVMCompilerArguments ?: return null
  val options = HashMap<String, Any>()

  val handledArguments = mutableSetOf<String>()
  fun <T> handleArgument(property: KProperty1<out K2JVMCompilerArguments, T>, body: (T) -> Unit) {
    body(property.getter.call(mergedCompilerArguments))
    check(handledArguments.add(property.name))
  }

  //api_version
  handleArgument(K2JVMCompilerArguments::apiVersion) { apiVersion ->
    if (apiVersion != null && apiVersion != kotlincDefaults.apiVersion) {
      options.put("api_version", apiVersion)
    }
  }
  //language_version
  handleArgument(K2JVMCompilerArguments::languageVersion) { languageVersion ->
    if (languageVersion != null && languageVersion != kotlincDefaults.languageVersion) {
      options.put("language_version", languageVersion)
    }
  }
  //optin
  handleArgument(K2JVMCompilerArguments::optIn) {
    // see create_kotlinc_options; treat empty facet opt-in as "use project default"
    val effectiveOptIn = it?.asList() ?: emptyList()
    if (effectiveOptIn.isNotEmpty() && effectiveOptIn != kotlincDefaults.optIn) {
      options.put("opt_in", effectiveOptIn)
    }
  }
  //plugin_options
  handleArgument(K2JVMCompilerArguments::pluginOptions) { pluginOptions ->
    if (pluginOptions?.isNotEmpty() == true) {
      options.put("plugin_options", pluginOptions.map {
        it.replace("${module.bazelBuildFileDir.invariantSeparatorsPathString}/", "${'$'}BASE_DIR$/${module.relativePathFromProjectRoot.invariantSeparatorsPathString}/")
      })
    }
  }
  // progressive
  handleArgument(K2JVMCompilerArguments::progressiveMode) {
    if (!it) {
      options.put("progressive", false)
    }
  }
  // warn: allWarningsAsErrors (-Werror). Default warn = "off" comes from create_kotlinc_options.
  handleArgument(K2JVMCompilerArguments::allWarningsAsErrors) {
    if (it) {
      options.put("warn", "error")
    }
  }
  //x_warning_level: -Xwarning-level=DIAGNOSTIC:severity per-diagnostic overrides (e.g. DEPRECATION:warning).
  handleArgument(K2JVMCompilerArguments::warningLevels) { warningLevels ->
    if (!warningLevels.isNullOrEmpty()) {
      options.put("x_warning_level", warningLevels.asList())
    }
  }
  //x_allow_kotlin_package
  handleArgument(K2JVMCompilerArguments::allowKotlinPackage) {
    if (it) {
      options.put("x_allow_kotlin_package", true)
    }
  }
  //x_allow_result_return_type
  if (mergedCompilerArguments.errors?.unknownExtraFlags?.contains("-Xallow-result-return-type") == true) {
    options.put("x_allow_result_return_type", true)
  }
  //x_allow_unstable_dependencies
  handleArgument(K2JVMCompilerArguments::allowUnstableDependencies) {
    if (it) {
      options.put("x_allow_unstable_dependencies", true)
    }
  }
  //x_compiler_plugin_order: -Xcompiler-plugin-order=<pluginId1>><pluginId2> execution order constraints.
  handleArgument(K2JVMCompilerArguments::pluginOrderConstraints) { pluginOrderConstraints ->
    if (pluginOrderConstraints.isNotEmpty()) {
      options.put("x_compiler_plugin_order", pluginOrderConstraints.asList())
    }
  }
  //x_consistent_data_class_copy_visibility
  handleArgument(K2JVMCompilerArguments::consistentDataClassCopyVisibility) {
    if (it) {
      options.put("x_consistent_data_class_copy_visibility", true)
    }
  }
  //x_context_parameters
  handleArgument(K2JVMCompilerArguments::contextParameters) {
    if (it) {
      options.put("x_context_parameters", true)
    }
  }
  //x_context_receivers
  handleArgument(K2JVMCompilerArguments::contextReceivers) {
    if (it) {
      options.put("x_context_receivers", true)
    }
  }
  //x_explicit_api_mode
  handleArgument(K2JVMCompilerArguments::explicitApi) {
    if (it != "disable") {
      options.put("x_explicit_api_mode", it)
    }
  }
  //x_inline_classes
  handleArgument(K2JVMCompilerArguments::inlineClasses) {
    if (it) {
      options.put("x_inline_classes", true)
    }
  }
  //x_jsr305
  handleArgument(K2JVMCompilerArguments::jsr305) { jsr305 ->
    if (!jsr305.isNullOrEmpty()) {
      options.put("x_jsr305", jsr305.asList())
    }
  }
  //x_jvm_default
  handleArgument(K2JVMCompilerArguments::jvmDefault) { xJvmDefault ->
    if (xJvmDefault != null) {
      if (xJvmDefault != kotlincDefaults.rawJvmDefault) {
        options.put("x_jvm_default", xJvmDefault)
      }
    } else {
      if (mergedCompilerArguments.jvmDefaultStable == null) {
        options.put("x_jvm_default", "all-compatibility")
      }
    }
  }
  //x_lambdas: not project-configurable via kotlinc.xml; default is the kt_kotlinc_options default "indy".
  handleArgument(K2JVMCompilerArguments::lambdas) { lambdas ->
    if (lambdas != null && lambdas != "indy") {
      options.put("x_lambdas", lambdas)
    }
  }
  //x_no_call_assertions
  handleArgument(K2JVMCompilerArguments::noCallAssertions) {
    if (it) {
      options.put("x_no_call_assertions", true)
    }
  }
  //x_no_param_assertions
  handleArgument(K2JVMCompilerArguments::noParamAssertions) {
    if (it) {
      options.put("x_no_param_assertions", true)
    }
  }
  //x_render_internal_diagnostic_names
  handleArgument(K2JVMCompilerArguments::renderInternalDiagnosticNames) {
    if (it) {
      options.put("x_render_internal_diagnostic_names", true)
    }
  }
  //x_report_all_warnings
  handleArgument(K2JVMCompilerArguments::reportAllWarnings) {
    if (it) {
      options.put("x_report_all_warnings", true)
    }
  }
  //x_sam_conversions: not project-configurable via kotlinc.xml; default is the kt_kotlinc_options default "indy".
  handleArgument(K2JVMCompilerArguments::samConversions) { samConversions ->
    if (samConversions != null && samConversions != "indy") {
      options.put("x_sam_conversions", samConversions)
    }
  }
  //x_skip_prerelease_check
  handleArgument(K2JVMCompilerArguments::skipPrereleaseCheck) {
    if (it) {
      options.put("x_skip_prerelease_check", true)
    }
  }
  //x_strict_java_nullability_assertions
  if (mergedCompilerArguments.errors?.unknownExtraFlags?.contains("-Xstrict-java-nullability-assertions") == true) {
    options.put("x_strict_java_nullability_assertions", true)
  }
  //x_wasm_attach_js_exception
  if (mergedCompilerArguments.errors?.unknownExtraFlags?.contains("-Xwasm-attach-js-exception") == true) {
    options.put("x_wasm_attach_js_exception", true)
  }
  //x_wasm_generate_closed_world_multimodule
  if (mergedCompilerArguments.errors?.unknownExtraFlags?.contains("-Xwasm-generate-closed-world-multimodule") == true) {
    options.put("x_wasm_generate_closed_world_multimodule", true)
  }
  //x_wasm_kclass_fqn
  if (mergedCompilerArguments.errors?.unknownExtraFlags?.contains("-Xwasm-kclass-fqn") == true) {
    options.put("x_wasm_kclass_fqn", true)
  }
  //x_when_guards
  handleArgument(K2JVMCompilerArguments::whenGuards) {
    if (it) {
      options.put("x_when_guards", true)
    }
  }
  //x_x_language
  val effectiveXXLanguage = mergedCompilerArguments.internalArguments.map { it.stringRepresentation }.filter { it.startsWith("-XXLanguage:") }
    .map { it.removePrefix("-XXLanguage:") }
  if (effectiveXXLanguage != kotlincDefaults.xxLanguage) {
    options.put("x_x_language", effectiveXXLanguage)
  }

  val allowedInternalXXLanguage = kotlincDefaults.xxLanguage.map { "-XXLanguage:$it" }.toMutableSet()
  // Some modules use -XXLanguage:+InlineClasses to opt into inline classes; this pre-existed kotlinc.xml-driven defaults.
  allowedInternalXXLanguage += "-XXLanguage:+InlineClasses"

  checkNoUnhandledKotlincOptions(
    module.module,
    mergedCompilerArguments,
    // manuallyConfiguredFeatures is the raw form of -XXLanguage:; the parser also folds it into internalArguments,
    // which is what the x_x_language handling above reads.
    handledArguments = handledArguments + setOf("jvmTarget", "pluginClasspaths", "manuallyConfiguredFeatures"),
    allowedInternalArguments = allowedInternalXXLanguage,
    allowedUnknownExtraFlags = setOf("-Xallow-result-return-type", "-Xstrict-java-nullability-assertions", "-Xwasm-attach-js-exception", "-Xwasm-generate-closed-world-multimodule", "-Xwasm-kclass-fqn"),
  )

  if (options.isEmpty()) {
    return null
  }

  buildFile.load((if (module.isCommunity) "" else "@community") + "//build:compiler-options.bzl", "create_kotlinc_options")

  if (jvmTarget != kotlincDefaults.jvmTarget) {
    options.put("jvm_target", jvmTarget)
  }

  val kotlincOptionsName = "custom_" + module.targetName
  buildFile.target("create_kotlinc_options") {
    option("name", kotlincOptionsName)
    for ((name, value) in options.entries.sortedBy { it.key }) {
      option(name, value)
    }
  }
  return ":$kotlincOptionsName"
}

private fun checkNoUnhandledKotlincOptions(module: JpsModule, mergedCompilerArguments: K2JVMCompilerArguments, handledArguments: Set<String>, allowedInternalArguments: Set<String>, allowedUnknownExtraFlags: Set<String>) {
  // check arguments:
  mergedCompilerArguments::class.memberProperties
    .filter { it.javaField!!.getAnnotation(Argument::class.java) != null }
    .filterNot { it.name in handledArguments }
    .forEach {
      val defaultValue = it.getter.call(K2JVMCompilerArguments())
      if (!isSameArgumentValue(it.getter.call(mergedCompilerArguments), defaultValue)) {
        error("module '${module.name}' has compiler argument which is not supported: ${it.name}")
      }
    }

  // check internal arguments:
  mergedCompilerArguments.internalArguments.filterNot { it.stringRepresentation in allowedInternalArguments }.forEach {
    error("module '${module.name}' has compiler internal argument which is not supported: ${it.stringRepresentation}")
  }

  // check errors:
  mergedCompilerArguments.errors?.unknownArgs.orEmpty().forEach {
    error("module '${module.name}' has unknown compiler argument: $it")
  }
  mergedCompilerArguments.errors?.unknownExtraFlags.orEmpty().filterNot { it in allowedUnknownExtraFlags }.forEach {
    error("module '${module.name}' has unknown compiler extra flag: $it")
  }
  mergedCompilerArguments.errors?.argumentsWithoutValue.orEmpty().forEach {
    error("module '${module.name}' has compiler argument without value: $it")
  }
  mergedCompilerArguments.errors?.booleanArgumentsWithIncorrectValue.orEmpty().forEach {
    error("module '${module.name}' has compiler boolean argument with value: $it")
  }
  // Arguments that toggle a language feature are reported separately from the two buckets above.
  mergedCompilerArguments.errors?.booleanLangFeatureArgumentsWithValue.orEmpty().forEach {
    error("module '${module.name}' has compiler boolean language feature argument with value: $it")
  }
  mergedCompilerArguments.errors?.stringLangFeatureArgumentsWithIncorrectValue.orEmpty().forEach { (argument, allowedValues) ->
    error("module '${module.name}' has compiler language feature argument with an unsupported value: $argument (allowed: ${allowedValues.joinToString()})")
  }
  mergedCompilerArguments.errors?.argfileErrors.orEmpty().forEach {
    error("module '${module.name}' has compiler argfile error: $it")
  }
  mergedCompilerArguments.errors?.internalArgumentsParsingProblems.orEmpty().forEach {
    error("module '${module.name}' has compiler internal arguments parsing problem: $it")
  }
}

// Array-valued kotlinc arguments default to a fresh `emptyArray()`, so `==` (reference equality for arrays)
// would report every unset array argument as customized.
private fun isSameArgumentValue(value: Any?, defaultValue: Any?): Boolean {
  return if (value is Array<*> && defaultValue is Array<*>) value.contentEquals(defaultValue) else value == defaultValue
}

private val addExportsRegex = Regex("""--add-exports\s+([^=]+)=\S+""")

private fun getUniqueSegmentName(labels: List<String>): Map<String, String> {
  return labels.associateWith { path ->
    path.splitToSequence('/').map {
      it.substringAfter(':')
        .replace('.', '-')
        .replace("/", "")
        .replace("@", "")
    }.filter { it.isNotEmpty() }.joinToString("_")
  }
}

private val LOG = Logger.getLogger("build-files")

internal fun computeProjectCompileExcludes(projectDir: Path, compilerExcludes: JpsCompilerExcludes): List<String> {
  val normalizedProjectDir = projectDir.toAbsolutePath().normalize()
  val patterns = ArrayList<String>()

  for (file in compilerExcludes.excludedFiles) {
    toProjectRelativePattern(projectDir = normalizedProjectDir, file = file.toPath())?.let { patterns.add(it) }
  }
  for (directory in compilerExcludes.excludedDirectories) {
    toProjectRelativePattern(projectDir = normalizedProjectDir, file = directory.toPath())?.let { patterns.add("$it/*") }
  }
  for (directory in compilerExcludes.recursivelyExcludedDirectories) {
    toProjectRelativePattern(projectDir = normalizedProjectDir, file = directory.toPath())?.let { patterns.add("$it/**/*") }
  }

  return patterns.distinct()
}

private fun toProjectRelativePattern(projectDir: Path, file: Path): String? {
  val path = file.toAbsolutePath().normalize()
  if (!path.startsWith(projectDir)) {
    return null
  }

  val relativePath = projectDir.relativize(path).invariantSeparatorsPathString
  require(relativePath.isNotEmpty()) {
    "Project root cannot be excluded from compilation: $file"
  }
  return relativePath
}

// Filters [projectCompileExcludes] (root-relative glob patterns) down to those that apply to the Bazel
// package located at [bazelBuildFileDir], and converts them to package-relative patterns suitable for
// Bazel's `glob(..., exclude = [...])`.
//
// Patterns that don't fall under [bazelBuildFileDir] are ignored. A pattern that exactly equals the
// package path becomes a recursive '**/*' exclude for that package.
internal fun computePackageRelativeExcludes(
  projectDir: Path,
  bazelBuildFileDir: Path,
  projectCompileExcludes: List<String>,
): List<String> {
  if (projectCompileExcludes.isEmpty()) {
    return emptyList()
  }

  val packagePath = projectDir.relativize(bazelBuildFileDir).invariantSeparatorsPathString
  if (packagePath.startsWith("..")) {
    // Bazel package is outside the project root; project-level excludes don't apply.
    return emptyList()
  }

  val prefix = if (packagePath.isEmpty()) "" else "$packagePath/"
  return projectCompileExcludes.mapNotNull { pattern ->
    when {
      packagePath.isEmpty() -> pattern
      pattern == packagePath -> "**/*"
      pattern.startsWith(prefix) -> pattern.removePrefix(prefix)
      else -> null
    }
  }.sortedBy { compileExcludeSortKey(it) }
}

/** Uses the same logic as `ij_plugin` rule */
private fun generateNameForPluginDirectory(moduleName: String): String {
  return moduleName.removePrefix("intellij.").removeSuffix(".plugin").replace('.', '-')
}

private fun compileExcludeSortKey(pattern: String): String = pattern.lowercase().replace('/', '{')

/**
 * Exports every XML under this module's production resource roots: content module descriptors,
 * `META-INF/plugin.xml`, and the fragments those `xi:include`.
 *
 * The whole tree, not the root and its `META-INF/`. A reference beginning with `/` is taken verbatim
 * (`LoadPathUtil.toLoadPath`), so a descriptor can name any path in the module,
 * and a predicate that assumes two directories leaves those unexported, which is an analysis error
 * the moment the plan names one.
 *
 * Deliberately a superset. An `exports_files` entry costs nothing - only what `build/dev_dist_plan.bzl` and the
 * convention probe name is materialized into the project model tree - and keeping the two rules independent is what
 * makes them unable to disagree: this side only has to be wide enough.
 */
private fun exportDescriptorFiles(module: ModuleDescriptor, buildFile: BuildFile, alreadyExported: Set<String>) {
  val exported = TreeSet<String>()
  for (resource in module.resources) {
    if (!resource.root.isDirectory()) {
      continue
    }

    for (file in resource.root.walk()) {
      if (file.extension != "xml" || !file.isRegularFile()) {
        continue
      }

      val relative = file.relativeTo(module.bazelBuildFileDir).invariantSeparatorsPathString
      // A resource root above the module's own Bazel package. `getModuleDescriptor` walks the package up until every
      // content root is under it, so a `customModules` override is the one way to reach this: it names a package of
      // its own, and `@community//build` is no ancestor of `community/jps/dependency-graph`. `../` is not a label, so
      // such a descriptor stays on the module-output read it uses today.
      //
      // An ultimate module whose resources live in the community tree is not this case, and the entry this writes for
      // it is inert. The walk drags the package up to the ultimate root, `community` is a `.bazelignore` entry of that
      // root, and Bazel therefore leaves the subtree out of the main repository's execroot symlink farm:
      // `//:community/<path>` analyses, and an action that declares it gets a symlink to nothing. The label an action
      // can read is `@community//<package>:<path>`, which only the community package can export - see
      // `containingBazelPackageLabel` in `devDistPluginDescriptorPlan.kt`.
      if (relative.startsWith("../")) {
        continue
      }

      // Exporting a file twice in one package is an analysis error, so a hand-written `exports_files` wins - it
      // is there to give that one file a narrower visibility than `//visibility:public`.
      if (!alreadyExported.contains(relative)) {
        exported.add(relative)
      }
    }
  }

  for (path in exported) {
    buildFile.exportFile(path)
  }
}
