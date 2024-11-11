// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.collections.ArrayDeque
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.println
import kotlin.io.startsWith
import kotlin.io.walkTopDown

internal class BazelBuildFileGenerator(
  private val projectDir: Path,
  private val project: JpsProject,
  @JvmField val urlCache: UrlCache,
) {
  private val communityDir = projectDir.resolve("community")

  @JvmField val javaExtensionService: JpsJavaExtensionService = JpsJavaExtensionService.getInstance()
  private val projectJavacSettings = javaExtensionService.getCompilerConfiguration(project)

  private val moduleToDescriptor = IdentityHashMap<JpsModule, ModuleDescriptor>()

  fun getKnownModuleDescriptorOrError(module: JpsModule): ModuleDescriptor {
    return moduleToDescriptor.get(module)
           ?: error("No descriptor for module ${module.name}")
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
      baseDirectory = imlDir,
      contentRoots = contentRoots,
      bazelBuildFileDir = bazelBuildDir,
      isCommunity = isCommunity,
      sources = computeSources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaSourceRootType.SOURCE),
      resources = resourceDescriptors + extraResourceTarget,
      testSources = computeSources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaSourceRootType.TEST_SOURCE),
      testResources = computeResources(module = module, contentRoots = contentRoots, bazelBuildDir = bazelBuildDir, type = JavaResourceRootType.TEST_RESOURCE),
    )
    moduleToDescriptor.put(module, moduleContent)

    module.dependenciesList.dependencies.asSequence().filterIsInstance<JpsModuleDependency>().forEach { getModuleDescriptor(it.moduleReference.resolve()!!) }

    return moduleContent
  }

  @Suppress("SSBasedInspection")
  @JvmField val libs: ObjectOpenHashSet<Library> = ObjectOpenHashSet<Library>()

  private val generated = IdentityHashMap<ModuleDescriptor, Boolean>()

  fun generateModuleBuildFiles(isCommunity: Boolean): Map<Path, BazelFileUpdater> {
    val fileToUpdater = HashMap<Path, BazelFileUpdater>()
    val queue = ArrayDeque<ModuleDescriptor>()
    for (module in project.model.project.modules) {
      val descriptor = getModuleDescriptor(module)
      if (isCommunity == descriptor.isCommunity) {
        queue.addLast(descriptor)
      }
    }

    while (true) {
      val module = queue.removeFirstOrNull() ?: break
      if (generated.putIfAbsent(module, true) == null) {
        val fileUpdater = fileToUpdater.computeIfAbsent(module.baseDirectory) {
          val fileUpdater = BazelFileUpdater(module.baseDirectory.resolve("BUILD.bazel"))
          fileUpdater.removeSections("build")
          fileUpdater
        }
        generateBazelBuildFiles(module, fileUpdater)
      }
    }
    return fileToUpdater
  }

  fun save(fileToUpdater: Map<Path, BazelFileUpdater>) {
    for (updater in fileToUpdater.values) {
      updater.save()
    }
  }

  fun getBazelDependencyLabel(descriptor: ModuleDescriptor, dependentIsCommunity: Boolean): String? {
    if (descriptor.module.name == "intellij.idea.community.build.zip") {
      return "@rules_jvm//zip:build-zip"
    }

    var path = checkAndGetRelativePath(projectDir, descriptor.baseDirectory).invariantSeparatorsPathString
    val relativeToCommunityPath = path.removePrefix("community/")
    path = when {
      path == relativeToCommunityPath -> if (path == "community" && dependentIsCommunity) "//" else "//$path"
      dependentIsCommunity -> if (relativeToCommunityPath == "community") "//" else "//$relativeToCommunityPath"
      else -> "@community//$relativeToCommunityPath"
    }

    val bazelName = jpsModuleNameToBazelBuildName(descriptor)
    val result = path + (if (descriptor.baseDirectory.fileName.toString() == bazelName) "" else ":${bazelName}")
    return result
  }

  private fun generateBazelBuildFiles(moduleDescriptor: ModuleDescriptor, fileUpdater: BazelFileUpdater) {
    //todo testResources
    val module = moduleDescriptor.module
    buildFile(out = fileUpdater, sectionName = "build ${module.name}") {
      val libraryTargetName = jpsModuleNameToBazelBuildName(moduleDescriptor)

      val jvmTarget = getLanguageLevel(module)
      val kotlincOptionsLabel = computeKotlincOptions(buildFile = this, module = module, jvmTarget = jvmTarget) ?: "@rules_jvm//:k$jvmTarget"
      val javacOptionsLabel = computeJavacOptions(module, jvmTarget) ?: "@rules_jvm//:j$jvmTarget"

      val resourceDependencies = mutableListOf<String>()
      val sources = moduleDescriptor.sources
      if (moduleDescriptor.resources.isNotEmpty()) {
        generateResources(moduleDescriptor = moduleDescriptor, resourceDependencies = resourceDependencies, libraryTargetName = libraryTargetName)
      }

      if (sources.isNotEmpty()) {
        load("@rules_jvm//:rules.bzl", "jvm_library")

        target("jvm_library") {
          option("name", libraryTargetName)
          option("module_name", module.name)
          visibility(arrayOf("//visibility:public"))
          option("srcs", glob(sources, exclude = listOf("**/module-info.java")))
          option("javac_opts", javacOptionsLabel)
          option("kotlinc_opts", kotlincOptionsLabel)

          generateDeps(
            target = this,
            module = moduleDescriptor,
            resourceDependencies = resourceDependencies,
            hasSources = true,
            context = this@BazelBuildFileGenerator
          )
        }
      }
      else {
        load("@rules_java//java:defs.bzl", "java_library")

        target("java_library", isEmpty = {
          it.optionCount() == 2 &&
          // we have to create an empty production module if someone depends on such a test module from another production module
          moduleDescriptor.testSources.isEmpty() &&
          // see community/plugins/kotlin/base/frontend-agnostic/README.md
          module.name != "kotlin.base.frontend-agnostic" &&
          // also a marker module like frontend-agnostic above
          module.name != "intellij.platform.monolith"
        }) {
          option("name", libraryTargetName)
          visibility(arrayOf("//visibility:public"))

          if (moduleDescriptor.testSources.isEmpty()) {
            generateDeps(
              target = this,
              module = moduleDescriptor,
              resourceDependencies = resourceDependencies,
              hasSources = false,
              context = this@BazelBuildFileGenerator,
            )
          }
        }
      }

      if (moduleDescriptor.testSources.isNotEmpty()) {
        load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_test")

        target("kt_jvm_test") {
          option("name", "${libraryTargetName}_test")
          visibility(arrayOf("//visibility:public"))
          option("srcs", glob(moduleDescriptor.testSources, exclude = listOf("**/module-info.java")))
          option("javac_opts", javacOptionsLabel)
          option("kotlinc_opts", kotlincOptionsLabel)

          if (sources.isNotEmpty()) {
            // associates also is a dependency
            option("associates", arrayOf(":$libraryTargetName"))
          }

          generateDeps(
            target = this,
            module = moduleDescriptor,
            resourceDependencies = resourceDependencies,
            hasSources = true,
            isTest = true,
            context = this@BazelBuildFileGenerator,
          )
        }
      }
    }
  }

  private fun BuildFile.generateResources(
    moduleDescriptor: ModuleDescriptor,
    resourceDependencies: MutableList<String>,
    libraryTargetName: @NlsSafe String,
  ) {
    if (moduleDescriptor.sources.isEmpty() && !(moduleDescriptor.module.dependenciesList.dependencies.none {
        when (it) {  // -- require
          is JpsModuleDependency, is JpsLibraryDependency -> {
            val scope = javaExtensionService.getDependencyExtension(it)?.scope
            scope != JpsJavaDependencyScope.TEST && scope != JpsJavaDependencyScope.RUNTIME
          }
          else -> false
        }
      })) {
      println("Expected no module/library non-runtime dependencies for resource-only module for ${moduleDescriptor.module.name}")
    }

    val resources = moduleDescriptor.resources
    if (resources.isEmpty()) {
      return
    }

    load("@rules_jvm//:jvm.bzl", "jvm_resources")

    for ((i, resource) in resources.withIndex()) {
      target("jvm_resources") {
        val name = "${libraryTargetName}_resources" + (if (i == 0) "" else "_$i")
        option("name", name)
        resourceDependencies.add(name)

        option("files", glob(resource.files, allowEmpty = false))
        if (resource.baseDirectory.isNotEmpty()) {
          option("strip_prefix", resource.baseDirectory)
        }
      }
    }
  }

  private fun jpsModuleNameToBazelBuildName(descriptor: ModuleDescriptor): @NlsSafe String {
    val result = descriptor.module.name
      .removePrefix("intellij.platform.")
      .removePrefix("intellij.idea.community.")
      .removePrefix("intellij.")

    val bazelBuildFileDirectory = descriptor.baseDirectory.parent
    return result
      .removePrefix("${bazelBuildFileDirectory.fileName}.")
      .replace('.', '-')
  }

  private fun BuildFile.computeJavacOptions(module: JpsModule, jvmTarget: String): String? {
    val extraJavacOptions = projectJavacSettings.currentCompilerOptions.ADDITIONAL_OPTIONS_OVERRIDE[module.name] ?: return null
    val exports = addExportsRegex.findAll(extraJavacOptions).map { it.groupValues[1] + "=ALL-UNNAMED" }.toList()
    if (exports.isEmpty()) {
      return null
    }

    load("@rules_kotlin//kotlin:jvm.bzl", "kt_javac_options")
    val customJavacOptionsName = "custom-javac-options"
    target("kt_javac_options") {
      option("name", customJavacOptionsName)
      // release is not compatible with --add-exports (*** java)
      require(jvmTarget == "17")
      option("x_ep_disable_all_checks", true)
      option("warn", "off")
      option("add_exports", exports)
    }
    return ":$customJavacOptionsName"
  }

  private fun computeSources(module: JpsModule, contentRoots: List<Path>, bazelBuildDir: Path, type: JpsModuleSourceRootType<*>): List<String> {
    return module.sourceRoots.asSequence()
      .filter { it.rootType == type }
      .flatMap { it ->
        val dir = it.path
        var prefix = resolveRelativeToBazelBuildFileDirectory(dir, contentRoots, bazelBuildDir).invariantSeparatorsPathString
        if (prefix.isNotEmpty()) {
          prefix += "/"
        }
        if (type == JavaSourceRootType.SOURCE || type == JavaSourceRootType.TEST_SOURCE) {
          sequenceOf("$prefix**/*.kt", "$prefix**/*.java")
        }
        else {
          sequenceOf("$prefix**/*")
        }
      }
      .toList()
  }

  private fun computeResources(module: JpsModule, contentRoots: List<Path>, bazelBuildDir: Path, type: JavaResourceRootType): List<ResourceDescriptor> {
    return module.sourceRoots
      .asSequence()
      .filter { it.rootType == type }
      .map {
        val prefix = resolveRelativeToBazelBuildFileDirectory(it.path, contentRoots, bazelBuildDir).invariantSeparatorsPathString
        ResourceDescriptor(baseDirectory = prefix, files = listOf("${if (prefix.isEmpty()) "" else "$prefix/"}**/*"))
      }
      .toList()
  }

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
        val metaInf = sourceRootDir.resolve("META-INF").toFile()
        val files = sourceRootDir.toFile().walkTopDown().filter {
          it.isFile && it.startsWith(metaInf)
        }.map {
          resolveRelativeToBazelBuildFileDirectory(childDir = it.toPath(), contentRoots = contentRoots, bazelBuildDir = bazelBuildDir).invariantSeparatorsPathString
        }.toList()
        if (files.isEmpty()) {
          return@mapNotNull null
        }

        val existingResourceRoot = module.sourceRoots.firstOrNull { it.rootType == JavaResourceRootType.RESOURCE }
        if (existingResourceRoot != null) {
          //FileUtil.moveDirWithContent(sourceRootDir.resolve("META-INF").toFile(), existingResourceRoot.file.resolve("META-INF"))
          println("WARN: Move META-INF to resource root (module=${module.name})")
        }

        val prefix = resolveRelativeToBazelBuildFileDirectory(sourceRootDir, contentRoots, bazelBuildDir).invariantSeparatorsPathString
        ResourceDescriptor(baseDirectory = prefix, files = files)
      }
  }

  private fun getLanguageLevel(module: JpsModule): String {
    val languageLevel = javaExtensionService.getLanguageLevel(module)
    return when {
      languageLevel == LanguageLevel.JDK_1_7 || languageLevel == LanguageLevel.JDK_1_8 -> "8"
      languageLevel == LanguageLevel.JDK_1_9 || languageLevel == LanguageLevel.JDK_11 -> "11"
      languageLevel == LanguageLevel.JDK_17 -> "17"
      languageLevel != null -> error("Unsupported language level: $languageLevel")
      else -> "17"
    }
  }
}

private fun checkAndGetRelativePath(parentDir: Path, childDir: Path): Path {
  require(childDir.startsWith(parentDir)) {
    "$childDir must be a child of parentDir $parentDir"
  }
  return parentDir.relativize(childDir)
}

private fun resolveRelativeToBazelBuildFileDirectory(childDir: Path, contentRoots: List<Path>, bazelBuildDir: Path): Path {
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
    "$childDir must be a child of contentRoots ${contentRoots.joinToString()}"
  }

  return bazelBuildDir.relativize(childDir)
}

private fun computeKotlincOptions(buildFile: BuildFile, module: JpsModule, jvmTarget: String): String? {
  val kotlinFacetModuleExtension = module.container.getChild(JpsKotlinFacetModuleExtension.Companion.KIND) ?: return null
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
  if (effectiveOptIn.isNotEmpty()) {
    options.put("opt_in", effectiveOptIn)
  }

  if (options.isEmpty()) {
    return null
  }

  buildFile.load("@rules_jvm//:compiler-options.bzl", "create_kotlinc_options")

  val kotlincOptionsName = "custom"
  buildFile.target("create_kotlinc_options") {
    option("name", kotlincOptionsName)
    option("jvm_target", if (jvmTarget == "8") "1.8" else jvmTarget)
    for ((name, value) in options.entries.sortedBy { it.key }) {
      option(name, value)
    }
  }
  return ":$kotlincOptionsName"
}

private val addExportsRegex = Regex("""--add-exports\s+([^=]+)=\S+""")