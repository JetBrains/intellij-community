// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "CanConvertToMultiDollarString")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.NlsSafe
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.LanguageLevel
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
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

internal class ModuleList(
  @JvmField val community: List<ModuleDescriptor>,
  @JvmField val ultimate: List<ModuleDescriptor>,
  val skippedModules: List<String>,
) {
  private val nameToDescriptor = community.associateBy { it.module.name } + ultimate.associateBy { it.module.name }

  fun getModuleDescriptor(name: String): ModuleDescriptor {
    return nameToDescriptor[name] ?: error("Unknown module name: $name")
  }

  @JvmField val deps = IdentityHashMap<ModuleDescriptor, ModuleDeps>()
  @JvmField val testDeps = IdentityHashMap<ModuleDescriptor, ModuleDeps>()
}

internal data class CustomModuleDescription(
  val moduleName: String,
  val bazelPackage: String,
  val bazelTargetName: String,
  val outputDirectory: String,
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

internal val customModules: Map<String, CustomModuleDescription> = listOf(
  CustomModuleDescription(moduleName = "intellij.idea.community.build.zip", bazelPackage = "@community//build", bazelTargetName = "zip",
                          outputDirectory = "out/bazel-out/jvm-fastbuild/bin/external/community+/build"),
  CustomModuleDescription(moduleName = "intellij.platform.jps.build.dependencyGraph", bazelPackage = "@community//build", bazelTargetName = "dependency-graph",
                          outputDirectory = "out/bazel-out/jvm-fastbuild/bin/external/community+/build",
                          additionalProductionTargets = listOf("@rules_jvm//dependency-graph:dependency-graph_resources"), additionalProductionJars = listOf("out/bazel-out/jvm-fastbuild/bin/external/rules_jvm+/dependency-graph/dependency-graph_resources.jar")),
  CustomModuleDescription(moduleName = "intellij.platform.jps.build.javac.rt", bazelPackage = "@community//build", bazelTargetName = "build-javac-rt",
                          outputDirectory = "out/bazel-out/jvm-fastbuild/bin/external/community+/build",
                          additionalProductionTargets = listOf("@rules_jvm//jps-builders-6:build-javac-rt_resources"), additionalProductionJars = listOf("out/bazel-out/jvm-fastbuild/bin/external/rules_jvm+/jps-builders-6/build-javac-rt_resources.jar")),
).associateBy { it.moduleName }

@Suppress("ReplaceGetOrSet", "SSBasedInspection")
internal class BazelBuildFileGenerator(
  val ultimateRoot: Path?,
  val communityRoot: Path,
  private val project: JpsProject,
  val urlCache: UrlCache,
) {
  @JvmField
  val javaExtensionService: JpsJavaExtensionService = JpsJavaExtensionService.getInstance()
  private val projectJavacSettings = javaExtensionService.getCompilerConfiguration(project)

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

    val isCommunity = imlDir.startsWith(communityRoot)
    if (isCommunity && !bazelBuildDir.startsWith(communityRoot)) {
      throw IllegalStateException("Computed dir for BUILD.bazel for community module ${module.name} is not under community directory")
    }

    val resourceDescriptors = computeResources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaResourceRootType.RESOURCE)

    val imlFile = imlDir.resolve("${module.name}.iml")
    val moduleContent = ModuleDescriptor(
      imlFile = imlFile,
      module = module,
      contentRoots = contentRoots,
      bazelBuildFileDir = bazelBuildDir,
      isCommunity = isCommunity,
      sources = computeSources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaSourceRootType.SOURCE),
      resources = resourceDescriptors,
      testSources = computeSources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaSourceRootType.TEST_SOURCE),
      testResources = computeResources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaResourceRootType.TEST_RESOURCE),
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
        getModuleDescriptor(requireNotNull(ref.resolve()) { "Cannot resolve module ${ref.moduleName}" })
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

  val mavenLibraries: Object2ObjectOpenHashMap<LibraryKey, MavenLibrary> = Object2ObjectOpenHashMap()
  val localLibraries: Object2ObjectOpenHashMap<LibraryKey, LocalLibrary> = Object2ObjectOpenHashMap()

  private val providedLibraries: ProvidedLibraries = ProvidedLibraries()
  class ProvidedLibraries() {
    private val providedLibraries: MutableSet<Library> = mutableSetOf()
    fun isProvided(library: Library): Boolean = providedLibraries.contains(library)
    fun markAsProvided(library: Library) { providedLibraries.add(library) }
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
    val fileToLabelTracker = LinkedHashMap<Path, MutableSet<String>>()
    val fileToUpdater = LinkedHashMap<Path, BazelFileUpdater>()
    for ((owner, list) in mavenLibraries
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
      val bazelFileUpdater = fileToUpdater.computeIfAbsent(owner.buildFile) {
        val updater = BazelFileUpdater(it)
        updater.removeSections("maven-libs")
        updater.removeSections("maven libs")
        updater
      }

      val sortedList = list.sortedBy { it.target.targetName }

      val groupedByTargetName = sortedList.groupBy { it.target.targetName }

      val labelTracker = fileToLabelTracker.computeIfAbsent(owner.moduleFile) { HashSet() }
      buildFile(out = bazelFileUpdater, sectionName = owner.sectionName) {
        load("@rules_jvm//:jvm.bzl", "jvm_import")

        for (entry in groupedByTargetName) {
          val libs = entry.value
          if (libs.size > 1) {
            throw IllegalStateException("More than one versions: $entry")
          }
          generateMavenLib(lib = libs.single(), labelTracker = labelTracker, isLibraryProvided = providedLibraries::isProvided, libVisibility = owner.visibility)
        }
      }

      generateBazelModuleSectionsForLibs(
        list = sortedList,
        owner = owner,
        jarRepositories = jarRepositories,
        m2Repo = m2Repo,
        urlCache = urlCache,
        moduleFileToLabelTracker = fileToLabelTracker,
        fileToUpdater = fileToUpdater,
      )
    }

    generateLocalLibs(libs = localLibraries.values, isLibraryProvided = providedLibraries::isProvided, fileToUpdater = fileToUpdater)

    for (updater in fileToUpdater.values) {
      updater.save()
    }
  }

  fun addMavenLibrary(lib: MavenLibrary, isProvided: Boolean): MavenLibrary {
    if (lib.target.container == ultimateLibraries) {
      val communityLibrary = mavenLibraries[LibraryKey(communityLibraries, lib.target.targetName)]
      if (communityLibrary != null) {
        if (isProvided) {
          providedLibraries.markAsProvided(communityLibrary)
        }
        return communityLibrary
      }
    }

    val internedLib = mavenLibraries.computeIfAbsent(LibraryKey(lib.target.container, lib.target.targetName)) { lib }
    if (isProvided) {
      providedLibraries.markAsProvided(internedLib)
    }
    return internedLib
  }

  fun addLocalLibrary(lib: LocalLibrary, isProvided: Boolean): LocalLibrary {
    val internedLib = localLibraries.computeIfAbsent(LibraryKey(lib.target.container, lib.target.targetName)) { lib }
    if (isProvided) {
      providedLibraries.markAsProvided(internedLib)
    }
    return internedLib
  }

  fun computeModuleList(): ModuleList {
    val bazelPluginDir = ultimateRoot?.resolve("plugins/bazel")

    val community = ArrayList<ModuleDescriptor>()
    val ultimate = ArrayList<ModuleDescriptor>()
    val skippedModules = ArrayList<String>()
    for (module in project.model.project.modules) {
      if (module.name == "intellij.platform.buildScripts.bazel") {
        // Skip bazel generator itself since it's a standalone Bazel project
        skippedModules.add(module.name)
        continue
      }

      val imlDir = JpsModelSerializationDataService.getBaseDirectory(module)!!.toPath()
      if (bazelPluginDir != null && imlDir.startsWith(bazelPluginDir)) {
        // Skip bazel plugin, they have their own bazel definitions
        skippedModules.add(module.name)
        continue
      }

      val descriptor = getModuleDescriptor(module)
      if (descriptor.isCommunity) {
        community.add(descriptor)
      }
      else {
        ultimate.add(descriptor)
      }
    }

    community.sortBy { it.module.name }
    ultimate.sortBy { it.module.name }
    val result = ModuleList(community = community, ultimate = ultimate, skippedModules = skippedModules)
    for (module in (community + ultimate)) {
      val hasSources = module.sources.isNotEmpty()
      if (hasSources || module.testSources.isEmpty()) {
        result.deps.put(module, generateDeps(module = module, isTest = false, context = this, hasSources = hasSources))
      }

      val hasTestSources = module.testSources.isNotEmpty()
      if (hasTestSources || isTestClasspathModule(module)) {
        result.testDeps.put(module, generateDeps(module = module, isTest = true, context = this, hasSources = hasTestSources))
      }
    }

    return result
  }

  data class ModuleGenerationResult(
    val moduleBuildFiles: Map<Path, BazelFileUpdater>,
    val moduleTargets: List<ModuleTargets>,
  )

  fun generateModuleBuildFiles(list: ModuleList, isCommunity: Boolean): ModuleGenerationResult {
    // assert that customModules are still actual
    for (customModule in customModules.values) {
      check(list.ultimate.any { it.module.name == customModule.moduleName } ||
            list.community.any { it.module.name == customModule.moduleName }) {
        "Unknown module name: ${customModule.moduleName} in `customModules`"
      }
    }
    val targetsPerModule = mutableListOf<ModuleTargets>()
    val fileToUpdater = LinkedHashMap<Path, BazelFileUpdater>()
    // bazel build file -> (bzlFile (for import) -> already imported symbols)
    val existingLoads = mutableMapOf<Path, MutableMap<String, MutableSet<String>>>()
    for (module in (if (isCommunity) list.community else list.ultimate)) {
      if (generated.putIfAbsent(module, true) == null) {
        val fileUpdater = fileToUpdater.computeIfAbsent(module.bazelBuildFileDir) {
          val fileUpdater = BazelFileUpdater(module.bazelBuildFileDir.resolve("BUILD.bazel"))
          fileUpdater.removeSections("build")
          fileUpdater.removeSections("test")
          fileUpdater.removeSections("maven libs of ")
          fileUpdater
        }

        val buildTargetsBazel = BuildFile()
        val moduleBuildTargets = buildTargetsBazel.generateBuildTargets(module, list)

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

        val buildSectionName = "build ${module.module.name}"
        if (!fileUpdater.isSectionSkipped(buildSectionName)) {
          fileUpdater.insertAutoGeneratedSection(sectionName = buildSectionName, autoGeneratedContent = buildTargetsBazel.render(existingLoadSymbols))
          collectLoadStatements(buildTargetsBazel.loadStatements)
        }

        val testSectionName = "test ${module.module.name}"
        if (!fileUpdater.isSectionSkipped(testSectionName)) {
          fileUpdater.insertAutoGeneratedSection(sectionName = testSectionName, autoGeneratedContent = testTargetsBazel.render(existingLoadSymbols))
          collectLoadStatements(buildTargetsBazel.loadStatements)
        }
      }
    }
    return ModuleGenerationResult(
      moduleBuildFiles = fileToUpdater.mapValues { it.value },
      moduleTargets = targetsPerModule,
    )
  }

  fun save(fileToUpdater: Map<Path, BazelFileUpdater>) {
    for (updater in fileToUpdater.values) {
      updater.save()
    }
  }

  fun getBazelDependencyLabel(module: ModuleDescriptor, dependent: ModuleDescriptor): String {
    val customModule = customModules[module.module.name]
    if (customModule != null) {
      return customModule.dependencyLabel
    }

    val dependentIsCommunity = dependent.isCommunity
    if (!dependentIsCommunity && module.isCommunity) {
      require(module.isCommunity)

      val path = checkAndGetRelativePath(communityRoot, module.bazelBuildFileDir).invariantSeparatorsPathString
      return if (path.substringAfterLast('/') == module.targetName) {
        "@community//$path"
      }
      else {
        "@community//$path:${module.targetName}"
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

    val bazelName = module.targetName
    val result = path + (if (module.bazelBuildFileDir.fileName.toString() == bazelName) "" else ":${bazelName}")
    return result
  }

  internal data class ModuleTargets(
    val moduleDescriptor: ModuleDescriptor,
    val productionTargets: List<String>,
    val productionJars: List<String>,
    val testTargets: List<String>,
    val testJars: List<String>,
  )

  private fun BuildFile.generateTestTargets(moduleDescriptor: ModuleDescriptor, moduleList: ModuleList) {
    if (moduleDescriptor.testSources.isEmpty()) {
      return
    }

    load("@community//build:tests-options.bzl", "jps_test")

    val mainModuleLabel = getTestClasspathModule(moduleDescriptor, moduleList)?.let { mainModule ->
      val productionLabel = getBazelDependencyLabel(mainModule, moduleDescriptor)
      "$productionLabel$TEST_LIB_NAME_SUFFIX"
    }

    val testLibTargetName = "${moduleDescriptor.targetName}$TEST_LIB_NAME_SUFFIX"

    target("jps_test") {
      option("name", "${moduleDescriptor.targetName}_test")
      option("runtime_deps", listOfNotNull(":$testLibTargetName", mainModuleLabel))
    }
  }

  private fun BuildFile.generateBuildTargets(moduleDescriptor: ModuleDescriptor, moduleList: ModuleList): ModuleTargets {
    val module = moduleDescriptor.module
    val jvmTarget = getLanguageLevel(module)
    val kotlincOptionsLabel = computeKotlincOptions(buildFile = this, module = moduleDescriptor, jvmTarget = jvmTarget)
                              ?: (if (jvmTarget == "17") null else "@community//:k$jvmTarget")
    val javacOptionsLabel = computeJavacOptions(moduleDescriptor, jvmTarget)

    val resourceTargets = mutableListOf<BazelLabel>()
    val productionCompileTargets = mutableListOf<BazelLabel>()
    val productionCompileJars = mutableListOf<BazelLabel>()
    val testCompileTargets = mutableListOf<BazelLabel>()

    val sources = moduleDescriptor.sources
    if (moduleDescriptor.resources.isNotEmpty()) {
      val result = generateResources(module = moduleDescriptor, forTests = false)
      resourceTargets.addAll(result.resourceTargets)
      productionCompileTargets.addAll(result.resourceTargets)
      productionCompileJars.addAll(result.resourceTargets)
    }
    if (moduleDescriptor.testResources.isNotEmpty()) {
      val result = generateResources(module = moduleDescriptor, forTests = true)
      resourceTargets.addAll(result.resourceTargets)
      testCompileTargets.addAll(result.resourceTargets)
    }

    // if someone depends on such a test module from another production module
    val isUsedAsTestDependency = !moduleDescriptor.testSources.isEmpty() && isReferencedAsTestDep(moduleList, moduleDescriptor)

    if (sources.isNotEmpty()) {
      load("@rules_jvm//:jvm.bzl", "jvm_library")

      target("jvm_library") {
        option("name", moduleDescriptor.targetName)
        productionCompileTargets.add(moduleDescriptor.targetAsLabel)
        productionCompileJars.add(moduleDescriptor.targetAsLabel)

        option("module_name", module.name)
        visibility(arrayOf("//visibility:public"))
        option("srcs", sourcesToGlob(sources, moduleDescriptor))
        if (javacOptionsLabel != null) {
          option("javac_opts", javacOptionsLabel)
        }
        if (kotlincOptionsLabel != null) {
          option("kotlinc_opts", kotlincOptionsLabel)
        }

        @Suppress("CascadeIf")
        if (module.name == "fleet.util.multiplatform" || module.name == "intellij.platform.syntax.multiplatformSupport") {
          option("exported_compiler_plugins", listOf("@lib//:expects-plugin"))
        }
        //else if (module.name == "fleet.rhizomedb") {
          // https://youtrack.jetbrains.com/issue/IJI-2662/RhizomedbCommandLineProcessor-requires-output-dir-but-we-dont-have-it-for-Bazel-compilation
          //option("exported_compiler_plugins", arrayOf("@lib//:rhizomedb-plugin"))
        //}
        else if (module.name == "fleet.rpc") {
          option("exported_compiler_plugins", listOf("@lib//:rpc-plugin"))
        }
        else if (module.name == "fleet.noria.cells") {
          option("exported_compiler_plugins", listOf("@lib//:noria-plugin"))
        }

        var deps = moduleList.deps.get(moduleDescriptor)
        if (deps != null && deps.provided.isNotEmpty()) {
          load("@rules_jvm//:jvm.bzl", "jvm_provided_library")

          val extraDeps = mutableListOf<BazelLabel>()
          val labelToName = getUniqueSegmentName(deps.provided.map { it.label })
          for (label in deps.provided) {
            val name = labelToName.get(label.label) + "_provided"
            extraDeps.add(BazelLabel(":$name", null))
            target("jvm_provided_library") {
              option("name", name)
              option("lib", label)
            }
          }

          deps = deps.copy(deps = deps.deps + extraDeps)
        }

        renderDeps(deps = deps, target = this, resourceDependencies = resourceTargets, forTests = false)
      }
    }
    else {
      load("@rules_jvm//:jvm.bzl", "jvm_library")

      val target = Target("jvm_library").apply {
        option("name", moduleDescriptor.targetName)
        visibility(arrayOf("//visibility:public"))
        option("srcs", sourcesToGlob(sources, moduleDescriptor))

        val deps = moduleList.deps.get(moduleDescriptor)
        renderDeps(
          deps = deps?.copy(plugins = emptyList()), // do not apply plugins to an empty library regardless of dependencies
          target = this,
          resourceDependencies = resourceTargets,
          forTests = false
        )
      }

      val addPhonyTarget =
        // meaning there are some attributes besides name and visibility
        target.optionCount() != 3 ||
        isUsedAsTestDependency ||
        module.name == "kotlin.base.frontend-agnostic" ||
        module.name == "intellij.platform.monolith" ||
        module.name == "intellij.platform.backend" ||
        module.name == "intellij.platform.compose.compilerPlugin"

      if (addPhonyTarget) {
        addTarget(target)

        productionCompileTargets.add(moduleDescriptor.targetAsLabel)
        productionCompileJars.add(moduleDescriptor.targetAsLabel)
      }
    }

    val moduleHasTestSources = moduleDescriptor.testSources.isNotEmpty()

    // Decide whether to render a test target at all
    if (moduleHasTestSources || isTestClasspathModule(moduleDescriptor)) {
      val testLibTargetName = "${moduleDescriptor.targetName}$TEST_LIB_NAME_SUFFIX"
      testCompileTargets.add(BazelLabel(testLibTargetName, moduleDescriptor))

      val testDeps = moduleList.testDeps.get(moduleDescriptor)

      load("@rules_jvm//:jvm.bzl", "jvm_library")
      target("jvm_library") {
        option("name", testLibTargetName)
        if (testDeps == null || testDeps.associates.isEmpty()) { // => in this case no 'associates' attribute will be generated
          option("module_name", module.name)
        }

        visibility(arrayOf("//visibility:public"))

        option("srcs", sourcesToGlob(moduleDescriptor.testSources, moduleDescriptor))

        javacOptionsLabel?.let { option("javac_opts", it) }
        kotlincOptionsLabel?.let { option("kotlinc_opts", it) }

        renderDeps(deps = testDeps, target = this, resourceDependencies = resourceTargets, forTests = true)
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

    val customModule = customModules[moduleDescriptor.module.name]

    val packagePrefix = when {
      customModule != null -> customModule.bazelPackage
      moduleDescriptor.isCommunity -> "@community//${bazelModuleRelativePath}"
      else -> "//${bazelModuleRelativePath}"
    }

    val jarOutputDirectory = when {
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
      else -> "$jarOutputDirectory/${jarName.label}.jar"
    }

    return ModuleTargets(
      moduleDescriptor = moduleDescriptor,
      productionTargets = productionCompileTargets.map { addPackagePrefix(it) } + customModule?.additionalProductionTargets.orEmpty(),
      productionJars = productionCompileJars.map { getJarLocation(it) } + customModule?.additionalProductionJars.orEmpty(),
      testTargets = testCompileTargets.map { addPackagePrefix(it) },
      testJars = testCompileTargets.map { getJarLocation(it) },
    )
  }

  private fun Target.sourcesToGlob(sources: List<SourceDirDescriptor>, module: ModuleDescriptor): Renderable {
    var exclude = sources.asSequence().flatMap { it.excludes }
    if (module.module.name.startsWith("fleet.")) {
      exclude += sequenceOf("**/module-info.java")
    }
    return glob(sources.flatMap { it.glob }, exclude = exclude.toList())
  }

  private data class GenerateResourcesResult(
    val resourceTargets: List<BazelLabel>,
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
      println("Expected no module/library non-runtime dependencies for resource-only module for ${module.module.name}")
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
      return GenerateResourcesResult(resourceTargets = fixedTargetsList)
    }

    load("@rules_jvm//:jvm.bzl", "jvm_resources")

    val targetNameSuffix = if (forTests) TEST_RESOURCES_TARGET_SUFFIX else PRODUCTION_RESOURCES_TARGET_SUFFIX

    val resourceTargets = resources.withIndex().map { (i, resource) ->
      val name = "${module.targetName}$targetNameSuffix" + (if (i == 0) "" else "_$i")

      target("jvm_resources") {
        option("name", name)
        option("files", glob(resource.files, allowEmpty = false))
        if (resource.baseDirectory.isNotEmpty()) {
          option("strip_prefix", resource.baseDirectory)
        }
        if (resource.relativeOutputPath.isNotEmpty()) {
          option("add_prefix", resource.relativeOutputPath)
        }
        if (hasOnlyTestResources(module)) {
          visibility(arrayOf("//visibility:public"))
        }
      }

      BazelLabel(name, module)
    }

    return GenerateResourcesResult(resourceTargets = resourceTargets)
  }

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
      require(jvmTarget == "17")
      option("x_ep_disable_all_checks", true)
      option("warn", "off")
      option("add_exports", exports)
      option("no_proc", noProc)
    }
    return ":$customJavacOptionsName"
  }

  private fun getLanguageLevel(module: JpsModule): String {
    val languageLevel = javaExtensionService.getLanguageLevel(module)
    return when {
      languageLevel == LanguageLevel.JDK_1_7 -> "7"
      languageLevel == LanguageLevel.JDK_1_8 -> "8"
      languageLevel == LanguageLevel.JDK_11 -> "11"
      languageLevel == LanguageLevel.JDK_17 -> "17"
      languageLevel != null -> error("Unsupported language level: $languageLevel")
      else -> "17"
    }
  }
}

// This is a usual convention in the intellij repository for storing classpath for running tests
// ex.: intellij.idea.community.main intellij.rubymine.main
private fun isTestClasspathModule(module: ModuleDescriptor): Boolean {
  return module.module.name.split('.').contains("main")
}

private fun getTestClasspathModule(module: ModuleDescriptor, moduleList: ModuleList): ModuleDescriptor? {
  val moduleName = module.module.name

  val mainModuleName = when {
    moduleName.startsWith("kotlin.jvm-debugger.") -> "intellij.idea.community.main"
    else -> null
  }

  return mainModuleName?.let { moduleList.getModuleDescriptor(it) }
}

private fun computeSources(module: JpsModule, contentRoots: List<Path>, bazelBuildDir: Path, type: JpsModuleSourceRootType<*>): List<SourceDirDescriptor> {
  return module.sourceRoots.asSequence()
    .filter { it.rootType == type }
    .flatMap { root ->
      val rootDir = root.path
      var prefix = resolveRelativeToBazelBuildFileDirectory(childDir = rootDir, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, module = module).invariantSeparatorsPathString
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

      if (type == JavaSourceRootType.SOURCE || type == JavaSourceRootType.TEST_SOURCE) {
        if (!(root.properties as JavaSourceRootProperties).isForGeneratedSources && rootDir.walk().any { it.extension == "form" }) {
          sequenceOf(SourceDirDescriptor(glob = listOf("$prefix**/*.kt", "$prefix**/*.java", "$prefix**/*.form"), excludes = excludes))
        }
        else {
          sequenceOf(SourceDirDescriptor(glob = listOf("$prefix**/*.kt", "$prefix**/*.java"), excludes = excludes))
        }
      }
      else {
        sequenceOf(SourceDirDescriptor(glob = listOf("$prefix**/*"), excludes = excludes))
      }
    }
    .toList()
}

private fun computeResources(module: JpsModule, contentRoots: List<Path>, bazelBuildDir: Path, type: JavaResourceRootType): List<ResourceDescriptor> {
  return module.sourceRoots
    .asSequence()
    .filter { it.rootType == type }
    .map {
      val prefix = resolveRelativeToBazelBuildFileDirectory(it.path, contentRoots, bazelBuildDir, module = module).invariantSeparatorsPathString
      val relativeOutputPath = (it.properties as JavaResourceRootProperties).relativeOutputPath
      ResourceDescriptor(baseDirectory = prefix, files = listOf("${if (prefix.isEmpty()) "" else "$prefix/"}**/*"), relativeOutputPath = relativeOutputPath)
    }
    .toList()
}

private fun isReferencedAsTestDep(
  moduleList: ModuleList,
  referencedModule: ModuleDescriptor,
): Boolean {
  for ((_, deps) in moduleList.testDeps) {
    if (isUsed(deps, referencedModule)) {
      return true
    }
  }
  for ((m, deps) in moduleList.deps) {
    // kotlin.all-tests uses scope RUNTIME to depend on the test module
    if (m.sources.isEmpty() && isUsed(deps, referencedModule)) {
      return true
    }
  }
  return false
}

private fun isUsed(
  deps: ModuleDeps,
  referencedModule: ModuleDescriptor,
): Boolean {
  return deps.depsModuleSet.contains(referencedModule) ||
         deps.runtimeDepsModuleSet.contains(referencedModule)
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
    baseBuildDir == ultimateRoot -> null
    baseBuildDir.parent == ultimateRoot -> "idea"
    else -> baseBuildDir.parent.fileName.toString()
  }

  return result
    .let { if (parentDirDirName != null) it.removePrefix("$parentDirDirName.") else it }
    .replace('.', '-')
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

private fun computeKotlincOptions(buildFile: BuildFile, module: ModuleDescriptor, jvmTarget: String): String? {
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
    if (apiVersion != null && apiVersion != "2.2") {
      options.put("api_version", apiVersion)
    }
  }
  //language_version
  handleArgument(K2JVMCompilerArguments::languageVersion) { languageVersion ->
    if (languageVersion != null && languageVersion != "2.2") {
      options.put("language_version", languageVersion)
    }
  }
  //optin
  handleArgument(K2JVMCompilerArguments::optIn) {
    // see create_kotlinc_options
    var effectiveOptIn = it?.asList() ?: emptyList()
    if (effectiveOptIn.size == 1 && effectiveOptIn[0] == "com.intellij.openapi.util.IntellijInternalApi") {
      effectiveOptIn = emptyList()
    }
    if (effectiveOptIn.isNotEmpty()) {
      options.put("opt_in", effectiveOptIn)
    }
  }
  //plugin_options
  handleArgument(K2JVMCompilerArguments::pluginOptions) { pluginOptions ->
    if (pluginOptions?.isNotEmpty() == true) {
      options.put("plugin_options", pluginOptions.map {
        it.replace("${module.bazelBuildFileDir.invariantSeparatorsPathString}/", "")
      })
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
  //x_jvm_default
  handleArgument(K2JVMCompilerArguments::jvmDefault) { xJvmDefault ->
    if (xJvmDefault != null) {
      if (xJvmDefault != "all") {
        options.put("x_jvm_default", xJvmDefault)
      }
    } else {
      if (mergedCompilerArguments.jvmDefaultStable == null) {
        options.put("x_jvm_default", "all-compatibility")
      }
    }
  }
  //x_lambdas
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
  //x_sam_conversions
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
  //x_when_guards
  handleArgument(K2JVMCompilerArguments::whenGuards) {
    if (it) {
      options.put("x_when_guards", true)
    }
  }
  //x_x_language
  val xXLanguageInlineClass = mergedCompilerArguments.internalArguments.any { it.stringRepresentation == "-XXLanguage:+InlineClasses"}
  if (xXLanguageInlineClass) {
    options.put("x_x_language", "+InlineClasses")
  }

  checkNoUnhandledKotlincOptions(
    module.module,
    mergedCompilerArguments,
    handledArguments = handledArguments + setOf("jvmTarget", "pluginClasspaths"),
    handledInternalArguments = setOf("-XXLanguage:+InlineClasses"),
    handledUnknownExtraFlags = setOf("-Xallow-result-return-type", "-Xstrict-java-nullability-assertions", "-Xwasm-attach-js-exception"),
  )

  if (options.isEmpty()) {
    return null
  }

  buildFile.load((if (module.isCommunity) "" else "@community") + "//build:compiler-options.bzl", "create_kotlinc_options")

  val kotlincOptionsName = "custom_" + module.targetName
  buildFile.target("create_kotlinc_options") {
    option("name", kotlincOptionsName)
    if (jvmTarget != "17") {
      option("jvm_target", jvmTarget)
    }
    for ((name, value) in options.entries.sortedBy { it.key }) {
      option(name, value)
    }
  }
  return ":$kotlincOptionsName"
}

private fun checkNoUnhandledKotlincOptions(module: JpsModule, mergedCompilerArguments: K2JVMCompilerArguments, handledArguments: Set<String>, handledInternalArguments: Set<String>, handledUnknownExtraFlags: Set<String>) {
  // check arguments:
  mergedCompilerArguments::class.memberProperties
    .filter { it.javaField!!.getAnnotation(Argument::class.java) != null }
    .filterNot { it.name in handledArguments }
    .forEach {
      val defaultValue = it.getter.call(K2JVMCompilerArguments())
      if (it.getter.call(mergedCompilerArguments) != defaultValue) {
        error("module '${module.name}' has compiler argument which is not supported: ${it.name}")
      }
    }

  // check internal arguments:
  mergedCompilerArguments.internalArguments.filterNot { it.stringRepresentation in handledInternalArguments }.forEach {
    error("module '${module.name}' has compiler internal argument which is not supported: ${it.stringRepresentation}")
  }

  // check errors:
  mergedCompilerArguments.errors?.unknownArgs.orEmpty().forEach {
    error("module '${module.name}' has unknown compiler argument: $it")
  }
  mergedCompilerArguments.errors?.unknownExtraFlags.orEmpty().filterNot { it in handledUnknownExtraFlags }.forEach {
    error("module '${module.name}' has unknown compiler extra flag: $it")
  }
  mergedCompilerArguments.errors?.argumentWithoutValue?.let {
    error("module '${module.name}' has compiler argument without value: $it")
  }
  mergedCompilerArguments.errors?.booleanArgumentWithValue?.let {
    error("module '${module.name}' has compiler boolean argument with value: $it")
  }
  mergedCompilerArguments.errors?.argfileErrors.orEmpty().forEach {
    error("module '${module.name}' has compiler argfile error: $it")
  }
  mergedCompilerArguments.errors?.internalArgumentsParsingProblems.orEmpty().forEach {
    error("module '${module.name}' has compiler internal arguments parsing problem: $it")
  }
}

private val addExportsRegex = Regex("""--add-exports\s+([^=]+)=\S+""")

private fun renderDeps(
  deps: ModuleDeps?,
  target: Target,
  resourceDependencies: List<BazelLabel>,
  forTests: Boolean,
) {
  if (deps != null) {
    if (deps.associates.isNotEmpty()) {
      target.option("associates", deps.associates)
    }
    if (deps.deps.isNotEmpty()) {
      target.option("deps", deps.deps)
    }
    if (deps.exports.isNotEmpty()) {
      target.option("exports", deps.exports)
    }
  }

  if (resourceDependencies.isNotEmpty() || (deps != null && deps.runtimeDeps.isNotEmpty())) {
    val runtimeDeps = resourceDependencies
                        .filter {
                          check(
                            PRODUCTION_RESOURCES_TARGET_REGEX.matches(it.label) ||
                            TEST_RESOURCES_TARGET_REGEX.matches(it.label)
                          ) {
                            "Unexpected resource dependency target name: $it"
                          }
                          check(
                            !PRODUCTION_RESOURCES_TARGET_REGEX.matches(it.label) ||
                            !TEST_RESOURCES_TARGET_REGEX.matches(it.label)
                          ) {
                            "Resource dependency target name matches both prod and test regex: $it"
                          }
                          return@filter PRODUCTION_RESOURCES_TARGET_REGEX.matches(it.label) ||
                          (forTests && TEST_RESOURCES_TARGET_REGEX.matches(it.label))
                        }.map {
                          if (it.label.startsWith('@') || it.label.startsWith("//")) {
                            it
                          } else {
                            it.copy(label = ":${it.label}")
                          }
                        } + (deps?.runtimeDeps ?: emptyList())
    if (runtimeDeps.isNotEmpty()) {
      target.option("runtime_deps", runtimeDeps)
    }
  }
  if (deps != null && deps.plugins.isNotEmpty()) {
    target.option("plugins", deps.plugins)
  }
}

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
