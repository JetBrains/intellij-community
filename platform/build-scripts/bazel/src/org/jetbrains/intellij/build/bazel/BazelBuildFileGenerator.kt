// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.NlsSafe
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.*
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.jps.model.JpsKotlinFacetModuleExtension
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.invariantSeparatorsPathString

internal class ModuleList(
  @JvmField val community: List<ModuleDescriptor>,
  @JvmField val ultimate: List<ModuleDescriptor>,
) {
  val deps = IdentityHashMap<ModuleDescriptor, ModuleDeps>()
  val testDeps = IdentityHashMap<ModuleDescriptor, ModuleDeps>()
}

@Suppress("ReplaceGetOrSet")
internal class BazelBuildFileGenerator(
  @JvmField val projectDir: Path,
  private val project: JpsProject,
  @JvmField val urlCache: UrlCache,
) {
  @JvmField val communityDir: Path = projectDir.resolve("community")

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
      bazelBuildDir = bazelBuildDir.parent!!
    }
    val isCommunity = imlDir.startsWith(communityDir)
    if (isCommunity && !bazelBuildDir.startsWith(communityDir)) {
      throw IllegalStateException("Computed dir for BUILD.bazel for community module ${module.name} is not under community directory")
    }

    val resourceDescriptors = computeResources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaResourceRootType.RESOURCE)
    val extraResourceTarget = extraResourceTarget(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir)
    val moduleContent = ModuleDescriptor(
      module = module,
      contentRoots = contentRoots,
      bazelBuildFileDir = bazelBuildDir,
      isCommunity = isCommunity,
      sources = computeSources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaSourceRootType.SOURCE),
      resources = resourceDescriptors + extraResourceTarget,
      testSources = computeSources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaSourceRootType.TEST_SOURCE),
      testResources = computeResources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaResourceRootType.TEST_RESOURCE),
      targetName = jpsModuleNameToBazelBuildName(module = module, baseBuildDir = bazelBuildDir, projectDir = projectDir)
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

  @Suppress("SSBasedInspection")
  @JvmField
  val libs: ObjectOpenHashSet<MavenLibrary> = ObjectOpenHashSet<MavenLibrary>()
  private val providedRequested = HashSet<LibOwner>()
  @Suppress("SSBasedInspection")
  @JvmField
  val localLibs: ObjectOpenHashSet<LocalLibrary> = ObjectOpenHashSet<LocalLibrary>()

  private val generated = IdentityHashMap<ModuleDescriptor, Boolean>()

  private val communityLibOwner = LibOwnerDescriptor("@lib", projectDir.resolve("community/lib/BUILD.bazel"), projectDir.resolve("community/lib/MODULE.bazel"))
  private val ultimateLibOwner = LibOwnerDescriptor("@ultimate_lib", projectDir.resolve("lib/BUILD.bazel"), projectDir.resolve("lib/MODULE.bazel"))

  fun getLibOwner(isCommunity: Boolean): LibOwnerDescriptor = if (isCommunity) communityLibOwner else ultimateLibOwner

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
          { it.buildFile.relativize(projectDir).invariantSeparatorsPathString },
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

      val labelTracker = fileToLabelTracker.computeIfAbsent(owner.moduleFile) { HashSet<String>() }
      buildFile(out = bazelFileUpdater, sectionName = owner.sectionName) {
        load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_import")

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
    val community = ArrayList<ModuleDescriptor>()
    val ultimate = ArrayList<ModuleDescriptor>()
    for (module in project.model.project.modules) {
      if (module.name == "fleet.compiler.plugins") {
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
    val result = ModuleList(community = community, ultimate = ultimate)
    for (module in (community + ultimate)) {
      val hasSources = module.sources.isNotEmpty()
      if (hasSources || module.testSources.isEmpty()) {
        result.deps.put(module, generateDeps(module = module, isTest = false, context = this, hasSources = hasSources))
      }
      if (module.testSources.isNotEmpty()) {
        result.testDeps.put(module, generateDeps(module = module, isTest = true, context = this, hasSources = true))
      }
    }

    return result
  }

  fun generateModuleBuildFiles(list: ModuleList, isCommunity: Boolean): Map<Path, BazelFileUpdater> {
    val fileToUpdater = LinkedHashMap<Path, Pair<BazelFileUpdater, BuildFile>>()
    for (module in (if (isCommunity) list.community else list.ultimate)) {
      if (generated.putIfAbsent(module, true) == null) {
        val (fileUpdater, buildFile) = fileToUpdater.computeIfAbsent(module.bazelBuildFileDir) {
          val fileUpdater = BazelFileUpdater(module.bazelBuildFileDir.resolve("BUILD.bazel"))
          fileUpdater.removeSections("build")
          fileUpdater.removeSections("maven libs of ")
          fileUpdater to BuildFile()
        }
        fileUpdater.insertAutoGeneratedSection(sectionName = "build ${module.module.name}", autoGeneratedContent = buildFile.apply { generateBazelBuildFile(module, list) }.render())
        buildFile.reset()
      }
    }
    return fileToUpdater.mapValues { it.value.first }
  }

  fun save(fileToUpdater: Map<Path, BazelFileUpdater>) {
    for (updater in fileToUpdater.values) {
      updater.save()
    }
  }

  fun getBazelDependencyLabel(module: ModuleDescriptor, dependent: ModuleDescriptor): String? {
    if (module.module.name == "intellij.idea.community.build.zip") {
      return "@rules_jvm//zip:build-zip"
    }

    val dependentIsCommunity = dependent.isCommunity
    if (!dependentIsCommunity && module.isCommunity) {
      require(module.isCommunity)

      val path = checkAndGetRelativePath(projectDir, module.bazelBuildFileDir).invariantSeparatorsPathString.removePrefix("community/").takeIf { it != "community" } ?: ""
      if (path.substringAfterLast('/') == module.targetName) {
        return "@community//$path"
      }
      else {
        return "@community//$path:${module.targetName}"
      }
    }

    if (dependentIsCommunity) {
      require(module.isCommunity) {
        "Community module ${dependent.module.name} cannot depend on ultimate module ${module.module.name}"
      }
    }

    var path = checkAndGetRelativePath(projectDir, module.bazelBuildFileDir).invariantSeparatorsPathString
    val relativeToCommunityPath = path.removePrefix("community/")
    path = when {
      path == relativeToCommunityPath -> if (path == "community" && dependentIsCommunity) "//" else "//$path"
      dependentIsCommunity -> if (relativeToCommunityPath == "community") "//" else "//$relativeToCommunityPath"
      else -> "@community//$relativeToCommunityPath"
    }

    val bazelName = module.targetName
    val result = path + (if (module.bazelBuildFileDir.fileName.toString() == bazelName) "" else ":${bazelName}")
    return result
  }

  @Suppress("DuplicatedCode")
  private fun BuildFile.generateBazelBuildFile(moduleDescriptor: ModuleDescriptor, moduleList: ModuleList) {
    //todo testResources
    val module = moduleDescriptor.module
    val jvmTarget = getLanguageLevel(module)
    val kotlincOptionsLabel = computeKotlincOptions(buildFile = this, module = moduleDescriptor, jvmTarget = jvmTarget)
                              ?: (if (jvmTarget == 17) null else "@community//:k$jvmTarget")
    val javacOptionsLabel = computeJavacOptions(module, jvmTarget)

    val resourceDependencies = mutableListOf<String>()
    val sources = moduleDescriptor.sources
    if (moduleDescriptor.resources.isNotEmpty()) {
      generateResources(module = moduleDescriptor, resourceDependencies = resourceDependencies)
    }

    // if someone depends on such a test module from another production module
    val isUsedAsTestDependency = !moduleDescriptor.testSources.isEmpty() && isReferencedAsTestDep(moduleList, moduleDescriptor)

    if (sources.isNotEmpty()) {
      load("@rules_jvm//:jvm.bzl", "jvm_library")

      target("jvm_library") {
        option("name", moduleDescriptor.targetName)
        option("module_name", module.name)
        visibility(arrayOf("//visibility:public"))
        option("srcs", sourcesToGlob(sources, moduleDescriptor))
        if (javacOptionsLabel != null) {
          option("javac_opts", javacOptionsLabel)
        }
        if (kotlincOptionsLabel != null) {
          option("kotlinc_opts", kotlincOptionsLabel)
        }

        var deps = moduleList.deps.get(moduleDescriptor)
        if (deps != null && deps.provided.isNotEmpty()) {
          load("@rules_jvm//:jvm.bzl", "jvm_provided_library")

          val extraDeps = mutableListOf<String>()
          for (label in deps.provided) {
            var n = label.substringAfterLast(':').substringAfterLast('/')
            if (n == "core" || n == "psi") {
              n = label.substringAfter("//").substringBeforeLast(':').split('/').get(1) + "-" + n
            }
            val name = n + "_provided"
            extraDeps.add(":$name")
            target("jvm_provided_library") {
              option("name", name)
              option("lib", label)
            }
          }

          deps = deps.copy(deps = deps.deps + extraDeps)
        }

        renderDeps(deps = deps, target = this, resourceDependencies = resourceDependencies)
      }
    }
    else {
      load("@rules_java//java:defs.bzl", "java_library")

      target("java_library", isEmpty = {
        it.optionCount() == 2 &&
        // we have to create an empty production module if someone depends on such a test module from another production module
        !isUsedAsTestDependency &&
        // see community/plugins/kotlin/base/frontend-agnostic/README.md
        module.name != "kotlin.base.frontend-agnostic" &&
        // also a marker module like frontend-agnostic above
        module.name != "intellij.platform.monolith" &&
        module.name != "intellij.platform.backend"
      }) {
        option("name", moduleDescriptor.targetName)
        visibility(arrayOf("//visibility:public"))

        if (moduleDescriptor.testSources.isEmpty()) {
          renderDeps(deps = moduleList.deps.get(moduleDescriptor), target = this, resourceDependencies = resourceDependencies)
        }
      }
    }

    if (moduleDescriptor.testSources.isNotEmpty()) {
      load("@rules_jvm//:jvm.bzl", "jvm_test")

      if (isUsedAsTestDependency) {
        load("@rules_jvm//:jvm.bzl", "jvm_library")

        val testLibTargetName = "${moduleDescriptor.targetName}$TEST_LIB_NAME_SUFFIX"
        target("jvm_library") {
          option("name", testLibTargetName)
          visibility(arrayOf("//visibility:public"))
          option("srcs", sourcesToGlob(moduleDescriptor.testSources, moduleDescriptor))
          javacOptionsLabel?.let { option("javac_opts", it) }
          kotlincOptionsLabel?.let { option("kotlinc_opts", it) }

          renderDeps(deps = moduleList.testDeps.get(moduleDescriptor), target = this, resourceDependencies = resourceDependencies)
        }

        target("jvm_test") {
          option("name", "${moduleDescriptor.targetName}_test")
          option("runtime_deps", arrayOf(":$testLibTargetName"))
        }
      }
      else {
        target("jvm_test") {
          option("name", "${moduleDescriptor.targetName}_test")
          option("srcs", sourcesToGlob(moduleDescriptor.testSources, moduleDescriptor))
          javacOptionsLabel?.let { option("javac_opts", it) }
          kotlincOptionsLabel?.let { option("kotlinc_opts", it) }

          renderDeps(deps = moduleList.testDeps.get(moduleDescriptor), target = this, resourceDependencies = resourceDependencies)
        }
      }
    }
  }

  private fun Target.sourcesToGlob(sources: List<SourceDirDescriptor>, module: ModuleDescriptor): Renderable {
    var exclude = sources.asSequence().flatMap { it.excludes }
    if (module.module.name.startsWith("fleet.")) {
      exclude += sequenceOf("**/module-info.java")
    }
    return glob(sources.flatMap { it.glob }, exclude = exclude.toList())
  }

  private fun BuildFile.generateResources(
    module: ModuleDescriptor,
    resourceDependencies: MutableList<String>,
  ) {
    if (module.sources.isEmpty() && !(module.module.dependenciesList.dependencies.none {
        when (it) {  // -- require
          is JpsModuleDependency, is JpsLibraryDependency -> {
            val scope = javaExtensionService.getDependencyExtension(it)?.scope
            scope != JpsJavaDependencyScope.TEST && scope != JpsJavaDependencyScope.RUNTIME
          }
          else -> false
        }
      })) {
      println("Expected no module/library non-runtime dependencies for resource-only module for ${module.module.name}")
    }

    val resources = module.resources
    if (resources.isEmpty()) {
      return
    }

    if (!module.isCommunity && module.targetName.startsWith("dotenv-") && resources[0].baseDirectory.contains("community")) {
      resourceDependencies.add("@community//plugins/env-files-support:${module.targetName}_resources")
      return
    }
    if (!module.isCommunity && module.module.name == "intellij.rider.plugins.renderdoc") {
      resourceDependencies.add("//dotnet/Plugins/renderdoc-support:renderdoc_resources")
      return
    }

    load("@rules_jvm//:jvm.bzl", "jvm_resources")

    for ((i, resource) in resources.withIndex()) {
      target("jvm_resources") {
        val name = "${module.targetName}_resources" + (if (i == 0) "" else "_$i")
        option("name", name)
        resourceDependencies.add(name)

        option("files", glob(resource.files, allowEmpty = false))
        if (resource.baseDirectory.isNotEmpty()) {
          option("strip_prefix", resource.baseDirectory)
        }
      }
    }
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
        sequenceOf(SourceDirDescriptor(glob = listOf("$prefix**/*.kt", "$prefix**/*.java"), excludes = excludes))
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
      ResourceDescriptor(baseDirectory = prefix, files = listOf("${if (prefix.isEmpty()) "" else "$prefix/"}**/*"))
    }
    .toList()
}

@OptIn(ExperimentalPathApi::class)
private fun extraResourceTarget(
  module: JpsModule,
  contentRoots: List<Path>,
  bazelBuildDir: Path,
): Sequence<ResourceDescriptor> {
  return module.sourceRoots
    .asSequence()
    .filter { it.rootType == JavaSourceRootType.SOURCE }
    .mapNotNull { sourceRoot ->
      val sourceRootDir = sourceRoot.path
      val metaInf = sourceRootDir.resolve("META-INF")
      if (!Files.exists(metaInf)) {
        return@mapNotNull null
      }

      val isEmptyDir = Files.newDirectoryStream(metaInf).use { stream ->
        val iterator = stream.iterator()
        while (iterator.hasNext()) {
          if (!iterator.next().toString().startsWith('.')) {
            return@use false
          }
        }
        true
      }
      if (isEmptyDir) {
        metaInf.deleteRecursively()
        return@mapNotNull null
      }

      val metaInfRelative = resolveRelativeToBazelBuildFileDirectory(childDir = metaInf, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, module = module)
        .invariantSeparatorsPathString

      val existingResourceRoot = module.sourceRoots.firstOrNull { it.rootType == JavaResourceRootType.RESOURCE }
      if (existingResourceRoot != null) {
        //FileUtil.moveDirWithContent(sourceRootDir.resolve("META-INF").toFile(), existingResourceRoot.file.resolve("META-INF"))
        println("WARN: Move META-INF to resource root (module=${module.name})")
      }

      val prefix = resolveRelativeToBazelBuildFileDirectory(sourceRootDir, contentRoots, bazelBuildDir, module = module).invariantSeparatorsPathString
      ResourceDescriptor(baseDirectory = prefix, files = listOf("$metaInfRelative/**/*"))
    }
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
    // kotlin.all-tests uses scope RUNTIME to depend on test module
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

private fun jpsModuleNameToBazelBuildName(module: JpsModule, baseBuildDir: Path, projectDir: Path): @NlsSafe String {
  val result = module.name
    .removePrefix("intellij.platform.")
    .removePrefix("intellij.idea.community.")
    .removePrefix("intellij.")

  val parentDirDirName = if (baseBuildDir.parent == projectDir) "idea"  else baseBuildDir.parent.fileName
  return result
    .removePrefix("$parentDirDirName.")
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
  val kotlinFacetModuleExtension = module.module.container.getChild(JpsKotlinFacetModuleExtension.Companion.KIND) ?: return null
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
    val runtimeDeps = resourceDependencies.map { if (it.startsWith('@') || it.startsWith("//")) it else ":$it" } + (deps?.runtimeDeps ?: emptyList())
    if (runtimeDeps.isNotEmpty()) {
      target.option("runtime_deps", runtimeDeps)
    }
  }
  if (deps != null && deps.plugins.isNotEmpty()) {
    target.option("plugins", deps.plugins)
  }
}