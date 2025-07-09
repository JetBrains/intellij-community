// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "CanConvertToMultiDollarString")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.NlsSafe
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
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
import org.jetbrains.kotlin.jps.model.JpsKotlinFacetModuleExtension
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.TreeMap
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

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
) {
  val dependencyLabel = if (bazelPackage.substringAfterLast("/") == bazelTargetName) {
    bazelPackage
  }
  else {
    "${bazelPackage}:${bazelTargetName}"
  }
}

internal val customModules: Map<String, CustomModuleDescription> = listOf(
  CustomModuleDescription(moduleName = "intellij.idea.community.build.zip", bazelPackage = "@rules_jvm//zip", bazelTargetName = "zip",
                          outputDirectory = "out/bazel-out/jvm-fastbuild/bin/external/rules_jvm+/zip"),
  CustomModuleDescription(moduleName = "intellij.platform.jps.build.dependencyGraph", bazelPackage = "@rules_jvm//dependency-graph", bazelTargetName = "dependency-graph",
                          outputDirectory = "out/bazel-out/jvm-fastbuild/bin/external/rules_jvm+/dependency-graph"),
  CustomModuleDescription(moduleName = "intellij.platform.jps.build.javac.rt", bazelPackage = "@rules_jvm//jps-builders-6", bazelTargetName = "build-javac-rt",
                          outputDirectory = "out/bazel-out/jvm-fastbuild/bin/external/rules_jvm+/jps-builders-6"),
).associateBy { it.moduleName }

@Suppress("ReplaceGetOrSet")
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

  val libs: ObjectOpenHashSet<MavenLibrary> = ObjectOpenHashSet<MavenLibrary>()
  private val providedRequested = HashSet<LibOwner>()
  val localLibs: ObjectOpenHashSet<LocalLibrary> = ObjectOpenHashSet<LocalLibrary>()

  private val generated = IdentityHashMap<ModuleDescriptor, Boolean>()

  private val communityLibOwner = LibOwnerDescriptor(
    repoLabel = "@lib",
    buildFile = communityRoot.resolve("lib/BUILD.bazel"),
    moduleFile = communityRoot.resolve("lib/MODULE.bazel"),
    isCommunity = true
  )

  private val ultimateLibOwner = ultimateRoot?.let { ultimate ->
    LibOwnerDescriptor(
      repoLabel = "@ultimate_lib",
      buildFile = ultimate.resolve("lib/BUILD.bazel"),
      moduleFile = ultimate.resolve("lib/MODULE.bazel"),
      isCommunity = false
    )
  }

  fun getLibOwner(isCommunity: Boolean): LibOwnerDescriptor = if (isCommunity) {
    communityLibOwner
  }
  else {
    require(ultimateLibOwner != null) {
      "requesting ultimate lib owner, but ultimate root is not present"
    }
    ultimateLibOwner
  }

  fun generateLibs(
    jarRepositories: List<JarRepository>,
    m2Repo: Path,
  ) {
    val fileToLabelTracker = LinkedHashMap<Path, MutableSet<String>>()
    val fileToUpdater = LinkedHashMap<Path, BazelFileUpdater>()
    for ((owner, list) in libs.groupByTo(
      destination = TreeMap(
        compareBy(
          { it.sectionName },
          { it.buildFile.invariantSeparatorsPathString },
        )
      ),
      keySelector = { it.lib.owner },
    )) {
      val bazelFileUpdater = fileToUpdater.computeIfAbsent(owner.buildFile) {
        val updater = BazelFileUpdater(it)
        updater.removeSections("maven-libs")
        updater.removeSections("maven libs")
        updater
      }

      val sortedList = list.sortedBy { it.lib.targetName }

      val groupedByTargetName = sortedList.groupBy { it.lib.targetName }

      val labelTracker = fileToLabelTracker.computeIfAbsent(owner.moduleFile) { HashSet() }
      buildFile(out = bazelFileUpdater, sectionName = owner.sectionName) {
        load("@rules_jvm//:jvm.bzl", "jvm_import")

        for (entry in groupedByTargetName) {
          val libs = entry.value
          if (libs.size > 1) {
            throw IllegalStateException("More than one versions: $entry")
          }
          generateMavenLib(lib = libs.single(), labelTracker = labelTracker, providedRequested = providedRequested, libVisibility = owner.visibility)
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

    generateLocalLibs(libs = localLibs, providedRequested = providedRequested, fileToUpdater = fileToUpdater)

    for (updater in fileToUpdater.values) {
      updater.save()
    }
  }

  fun addMavenLibrary(lib: MavenLibrary, isProvided: Boolean): MavenLibrary {
    if (lib.lib.owner == ultimateLibOwner) {
      libs.firstOrNull { it.lib.owner == communityLibOwner && it.lib.targetName == lib.lib.targetName }?.let {
        if (isProvided) {
          providedRequested.add(it)
        }
        return it
      }
    }

    val internedLib = libs.addOrGet(lib)
    if (isProvided) {
      providedRequested.add(internedLib)
    }
    return internedLib
  }

  fun addLocalLibrary(lib: LocalLibrary, isProvided: Boolean): LocalLibrary {
    val internedLib = localLibs.addOrGet(lib)
    if (isProvided) {
      providedRequested.add(internedLib)
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
    val fileToUpdater = LinkedHashMap<Path, Pair<BazelFileUpdater, BuildFile>>()
    for (module in (if (isCommunity) list.community else list.ultimate)) {
      if (generated.putIfAbsent(module, true) == null) {
        val (fileUpdater, buildFile) = fileToUpdater.computeIfAbsent(module.bazelBuildFileDir) {
          val fileUpdater = BazelFileUpdater(module.bazelBuildFileDir.resolve("BUILD.bazel"))
          fileUpdater.removeSections("build")
          fileUpdater.removeSections("maven libs of ")
          fileUpdater to BuildFile()
        }

        val moduleTargets = buildFile.generateBazelBuildFile(module, list)
        targetsPerModule.add(moduleTargets)

        val autoGeneratedContent = buildFile.render()
        fileUpdater.insertAutoGeneratedSection(sectionName = "build ${module.module.name}", autoGeneratedContent = autoGeneratedContent)
        buildFile.reset()
      }
    }
    return ModuleGenerationResult(
      moduleBuildFiles = fileToUpdater.mapValues { it.value.first },
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

  private fun BuildFile.generateBazelBuildFile(moduleDescriptor: ModuleDescriptor, moduleList: ModuleList): ModuleTargets {
    //todo testResources
    val module = moduleDescriptor.module
    val jvmTarget = getLanguageLevel(module)
    val kotlincOptionsLabel = computeKotlincOptions(buildFile = this, module = moduleDescriptor, jvmTarget = jvmTarget)
                              ?: (if (jvmTarget == 17) null else "@community//:k$jvmTarget")
    val javacOptionsLabel = computeJavacOptions(module, jvmTarget)

    val resourceTargets = mutableListOf<String>()
    val productionCompileTargets = mutableListOf<String>()
    val productionCompileJars = mutableListOf<String>()
    val testCompileTargets = mutableListOf<String>()

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
        productionCompileTargets.add(moduleDescriptor.targetName)
        productionCompileJars.add(moduleDescriptor.targetName)

        option("module_name", module.name)
        visibility(arrayOf("//visibility:public"))
        option("srcs", sourcesToGlob(sources, moduleDescriptor))
        if (javacOptionsLabel != null) {
          option("javac_opts", javacOptionsLabel)
        }
        if (kotlincOptionsLabel != null) {
          option("kotlinc_opts", kotlincOptionsLabel)
        }

        if (module.name == "fleet.util.multiplatform" || module.name == "intellij.platform.syntax.multiplatformSupport") {
          option("exported_compiler_plugins", arrayOf("@lib//:expects-plugin"))
        }
        //else if (module.name == "fleet.rhizomedb") {
          // https://youtrack.jetbrains.com/issue/IJI-2662/RhizomedbCommandLineProcessor-requires-output-dir-but-we-dont-have-it-for-Bazel-compilation
          //option("exported_compiler_plugins", arrayOf("@lib//:rhizomedb-plugin"))
        //}
        else if (module.name == "fleet.rpc") {
          option("exported_compiler_plugins", arrayOf("@lib//:rpc-plugin"))
        }
        else if (module.name == "fleet.noria.cells" ||
                 module.name == "fleet.noria.html" ||
                 module.name == "fleet.noria.ui" ||
                 module.name == "fleet.noria.ui.examples" ||
                 module.name == "fleet.noria.windowManagement.api" ||
                 module.name == "fleet.noria.windowManagement.extensions" ||
                 module.name == "fleet.noria.windowManagement.impl" ||
                 module.name == "fleet.noria.windowManagement.implDesktopMacOS") {
          option("exported_compiler_plugins", arrayOf("@lib//:noria-plugin"))
        }

        var deps = moduleList.deps.get(moduleDescriptor)
        if (deps != null && deps.provided.isNotEmpty()) {
          load("@rules_jvm//:jvm.bzl", "jvm_provided_library")

          val extraDeps = mutableListOf<String>()
          val labelToName = getUniqueSegmentName(deps.provided)
          for (label in deps.provided) {
            val name = labelToName.get(label) + "_provided"
            extraDeps.add(":$name")
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

        productionCompileTargets.add(moduleDescriptor.targetName)
        productionCompileJars.add(moduleDescriptor.targetName)
      }
    }

    val moduleHasTestSources = moduleDescriptor.testSources.isNotEmpty()

    // Decide whether to render a test target at all
    if (moduleHasTestSources || isTestClasspathModule(moduleDescriptor)) {
      val testLibTargetName = "${moduleDescriptor.targetName}$TEST_LIB_NAME_SUFFIX"
      testCompileTargets.add(testLibTargetName)

      val testDeps = moduleList.testDeps.get(moduleDescriptor)

      load("@rules_jvm//:jvm.bzl", "jvm_library")
      target("jvm_library") {
        option("name", testLibTargetName)

        visibility(arrayOf("//visibility:public"))

        option("srcs", sourcesToGlob(moduleDescriptor.testSources, moduleDescriptor))

        javacOptionsLabel?.let { option("javac_opts", it) }
        kotlincOptionsLabel?.let { option("kotlinc_opts", it) }

        renderDeps(deps = testDeps, target = this, resourceDependencies = resourceTargets, forTests = true)
      }

      if (moduleHasTestSources) {
        load("@rules_jvm//:jvm.bzl", "jvm_test")
        load("@community//build:tests-options.bzl", "jps_test")

        val mainModuleLabel = getTestClasspathModule(moduleDescriptor, moduleList)?.let { mainModule ->
          val productionLabel = getBazelDependencyLabel(mainModule, moduleDescriptor)
          "$productionLabel$TEST_LIB_NAME_SUFFIX"
        }

        target("jps_test") {
          option("name", "${moduleDescriptor.targetName}_test")
          option("runtime_deps", listOfNotNull(":$testLibTargetName", mainModuleLabel))
        }
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

    fun addPackagePrefix(target: String): String =
      if (target.startsWith("//") || target.startsWith("@")) target else "$packagePrefix:$target"

    fun getJarLocation(jarName: String) = when {
      // full target name instead of just jar for intellij.dotenv.*
      // like @community//plugins/env-files-support:dotenv-go_resources
      jarName.startsWith("@community//") ->
        "out/bazel-out/jvm-fastbuild/bin/external/community+/${jarName.substringAfter("@community//").replace(':', '/')}.jar"
      else -> "$jarOutputDirectory/$jarName.jar"
    }

    return ModuleTargets(
      moduleDescriptor = moduleDescriptor,
      productionTargets = productionCompileTargets.map { addPackagePrefix(it) },
      productionJars = productionCompileJars.map { getJarLocation(it) },
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
    val resourceTargets: List<String>,
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
      val fixedTargetsList = if (forTests) {
        // skip for now
        emptyList()
      }
      else {
        listOf("@community//plugins/env-files-support:${module.targetName}_resources")
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

      name
    }

    return GenerateResourcesResult(resourceTargets = resourceTargets)
  }

  private fun BuildFile.computeJavacOptions(module: JpsModule, jvmTarget: Int): String? {
    val extraJavacOptions = projectJavacSettings.currentCompilerOptions.ADDITIONAL_OPTIONS_OVERRIDE.get(module.name) ?: return null
    val exports = addExportsRegex.findAll(extraJavacOptions).map { it.groupValues[1] + "=ALL-UNNAMED" }.toList()
    if (exports.isEmpty()) {
      return null
    }

    load("@rules_kotlin//kotlin:jvm.bzl", "kt_javac_options")
    val customJavacOptionsName = "custom-javac-options"
    target("kt_javac_options") {
      option("name", customJavacOptionsName)
      // release is not compatible with --add-exports (*** java)
      require(jvmTarget == 17)
      option("x_ep_disable_all_checks", true)
      option("warn", "off")
      option("add_exports", exports)
    }
    return ":$customJavacOptionsName"
  }

  private fun getLanguageLevel(module: JpsModule): Int {
    val languageLevel = javaExtensionService.getLanguageLevel(module)
    return when {
      languageLevel == LanguageLevel.JDK_1_7 || languageLevel == LanguageLevel.JDK_1_8 -> 8
      languageLevel == LanguageLevel.JDK_1_9 || languageLevel == LanguageLevel.JDK_11 -> 11
      languageLevel == LanguageLevel.JDK_17 -> 17
      languageLevel != null -> error("Unsupported language level: $languageLevel")
      else -> 17
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
        val rootProperties = root.properties
        if ((rootProperties !is JavaSourceRootProperties || !rootProperties.isForGeneratedSources) && moduleWithForm.contains (module.name)) {
          // rootDir.walk().any { it.extension == "form" }
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

// todo: use context.getBazelDependencyLabel
private fun isUsed(
  deps: ModuleDeps,
  referencedModule: ModuleDescriptor,
): Boolean {
  return deps.deps.any { it.substringAfterLast(':').substringAfterLast('/').contains(referencedModule.targetName) } ||
         deps.runtimeDeps.any { it.substringAfterLast(':').substringAfterLast('/').contains(referencedModule.targetName) }
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

private fun computeKotlincOptions(buildFile: BuildFile, module: ModuleDescriptor, jvmTarget: Int): String? {
  val kotlinFacetModuleExtension = module.module.container.getChild(JpsKotlinFacetModuleExtension.KIND) ?: return null
  val mergedCompilerArguments = kotlinFacetModuleExtension.settings.mergedCompilerArguments ?: return null
  // see create_kotlinc_options
  val effectiveOptIn = mergedCompilerArguments.optIn?.filter { it != "com.intellij.openapi.util.IntellijInternalApi" } ?: emptyList()

  val options = HashMap<String, Any>()
  if (mergedCompilerArguments.allowKotlinPackage) {
    options.put("allow_kotlin_package", true)
  }
  if (mergedCompilerArguments.contextReceivers) {
    options.put("context_receivers", true)
  }
  if (mergedCompilerArguments.contextParameters) {
    options.put("context_parameters", true)
  }
  if (mergedCompilerArguments.whenGuards) {
    options.put("when_guards", true)
  }
  if (effectiveOptIn.isNotEmpty()) {
    options.put("opt_in", effectiveOptIn)
  }

  if (options.isEmpty()) {
    return null
  }

  buildFile.load((if (module.isCommunity) "" else "@community") + "//build:compiler-options.bzl", "create_kotlinc_options")

  var kotlincOptionsName = "custom"
  if (!buildFile.nameGuard.add(kotlincOptionsName)) {
    kotlincOptionsName = "custom_" + module.targetName
  }
  buildFile.target("create_kotlinc_options") {
    option("name", kotlincOptionsName)
    if (jvmTarget != 17) {
      option("jvm_target", jvmTarget)
    }
    for ((name, value) in options.entries.sortedBy { it.key }) {
      option(name, value)
    }
  }
  return ":$kotlincOptionsName"
}

private val addExportsRegex = Regex("""--add-exports\s+([^=]+)=\S+""")

private fun renderDeps(
  deps: ModuleDeps?,
  target: Target,
  resourceDependencies: List<String>,
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
                            PRODUCTION_RESOURCES_TARGET_REGEX.matches(it) ||
                            TEST_RESOURCES_TARGET_REGEX.matches(it)
                          ) {
                            "Unexpected resource dependency target name: $it"
                          }
                          check(
                            !PRODUCTION_RESOURCES_TARGET_REGEX.matches(it) ||
                            !TEST_RESOURCES_TARGET_REGEX.matches(it)
                          ) {
                            "Resource dependency target name matches both prod and test regex: $it"
                          }
                          return@filter PRODUCTION_RESOURCES_TARGET_REGEX.matches(it) ||
                          (forTests && TEST_RESOURCES_TARGET_REGEX.matches(it))
                        }.map { if (it.startsWith('@') || it.startsWith("//")) it else ":$it" } +
                      (deps?.runtimeDeps ?: emptyList())
    if (runtimeDeps.isNotEmpty()) {
      target.option("runtime_deps", runtimeDeps)
    }
  }
  if (deps != null && deps.plugins.isNotEmpty()) {
    target.option("plugins", deps.plugins)
  }
}

private fun getUniqueSegmentName(labels: List<String>): Map<String, String> {
  // first try with just last segments
  val lastSegments = labels.associateWith { path ->
    path.splitToSequence('/').last().substringAfter(':').replace('.', '-')
  }

  // find which names have collisions
  val nameCount = lastSegments.values.groupingBy { it }.eachCount()
  if (nameCount.none { it.value > 1 }) {
    return lastSegments
  }

  // for paths with colliding names, try using more segments
  val result = LinkedHashMap<String, String>()
  var segmentDepth = 2

  while (true) {
    result.clear()
    for (label in labels) {
      val segments = label.splitToSequence('/')
        .map { it.substringAfter(':').replace('.', '-') }
        .filter { it.isNotEmpty() }
        .toList()
      if (segments.isEmpty()) {
        continue
      }

      val lastSegment = segments.last()

      // if this last segment has collisions, use more segments
      if ((nameCount[lastSegment] ?: 0) > 1) {
        val relevantSegments = segments.takeLast(minOf(segmentDepth, segments.size))
        result.put(label, relevantSegments.joinToString("-"))
      }
      else {
        // no collision - use just the last segment
        result.put(label, lastSegment.replace('.', '-'))
      }
    }

    // check if we resolved all collisions
    val newNameCount = result.values.groupingBy { it }.eachCount()
    if (newNameCount.none { it.value > 1 }) {
      return result
    }

    segmentDepth++
    // safety check to prevent infinite loop
    if (segmentDepth > 5) {
      throw IllegalStateException("Unable to resolve unique names after trying 5 segment levels")
    }
  }
}
