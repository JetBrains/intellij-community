// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SameParameterValue", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.*
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.module.*
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.jps.model.JpsKotlinFacetModuleExtension
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.io.path.invariantSeparatorsPathString

class JpsModuleToBazel {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      // App module - the module that we want to run. We build Bazel BUILD files for this module and all its dependencies.
      val m2Repo = Path.of(System.getProperty("user.name"), ".m2/repository")
      val projectDir = Path.of(".").toAbsolutePath().normalize()
      val project = JpsSerializationManager.getInstance().loadProject(projectDir.toString(), mapOf("MAVEN_REPOSITORY" to m2Repo.toString()), true)

      val nameToModule = project.model.project.modules.associateTo(HashMap()) { it.name to it }

      val generator = BazelBuildFileGenerator(projectDir, project)

      generator.addModuleToQueue(nameToModule.getValue("intellij.platform.buildScripts"))
      generator.addModuleToQueue(nameToModule.getValue("intellij.platform.buildScripts.bazel"))
      generator.addModuleToQueue(nameToModule.getValue("intellij.platform.images"))
      generator.addModuleToQueue(nameToModule.getValue("intellij.tools.ide.metrics.benchmark"))
      generator.addModuleToQueue(nameToModule.getValue("intellij.xml.impl"))
      generator.generate()

      generateCommunityLibraryBuild(projectDir, generator)

      val bazelFileUpdater = BazelFileUpdater(projectDir.resolve("community/build/libraries/MODULE.bazel"))
      buildFile(bazelFileUpdater, "artifacts") {
        target("") {
          option(
            "artifacts",
            listOf("org.jetbrains.kotlin:kotlin-serialization-compiler-plugin:2.0.10")
            + generator.libs.asSequence().map { it.mavenCoordinates }.distinct().sorted().toList()
          )
        }
      }
      bazelFileUpdater.save()
    }
  }
}

private fun generateCommunityLibraryBuild(projectDir: Path, generator: BazelBuildFileGenerator) {
  val bazelFileUpdater = BazelFileUpdater(projectDir.resolve("community/build/libraries/BUILD.bazel"))
  buildFile(bazelFileUpdater, "maven-libraries") {
    for (lib in generator.libs.groupBy { it.targetName }.flatMap { (_, values) -> listOf(values.maxByOrNull { it.targetName }!!) }) {
      if (lib.targetName == "bifurcan" || lib.targetName == "kotlinx-collections-immutable-jvm") {
        continue
      }

      target("java_library") {
        option("name", lib.targetName)
        option("exports", arrayOf(lib.bazelLabel))
        if (lib.isProvided) {
          @Suppress("SpellCheckingInspection")
          option("neverlink", true)
        }
        visibility(arrayOf("//visibility:public"))
      }
    }
  }
  bazelFileUpdater.save()
}

private data class ModuleDescriptor(
  @JvmField val contentRoot: Path,
)

private fun describeModule(module: JpsModule): ModuleDescriptor {
  val contentRoots = module.contentRootsList.urls.map { Path.of(JpsPathUtil.urlToPath(it)) }
  if (contentRoots.isEmpty()) {
    throw NoContentRoot("Skip ${module.name} because it has no content roots")
  }

  require(contentRoots.size == 1) {
    "Expected exactly one content root for module ${module.name}, got $contentRoots"
  }
  return ModuleDescriptor(
    contentRoot = contentRoots.first(),
  )
}

private class NoContentRoot(message: String) : RuntimeException(message)

private data class Library(
  @JvmField val bazelLabel: String,
  @JvmField val mavenCoordinates: String,
  @JvmField val targetName: String,
  @JvmField val isProvided: Boolean,
  @JvmField val isCommunity: Boolean,
)

private class BazelBuildFileGenerator(
  private val projectDir: Path,
  project: JpsProject,
) {
  private val javaExtensionService = JpsJavaExtensionService.getInstance()
  private val projectJavacSettings = javaExtensionService.getCompilerConfiguration(project)

  private val generated = IdentityHashMap<JpsModule, Boolean>()
  private val queue = ArrayDeque<JpsModule>()

  private val moduleDescriptors = IdentityHashMap<JpsModule, ModuleDescriptor>()

  private fun getModuleDescriptor(module: JpsModule): ModuleDescriptor? {
    try {
      return moduleDescriptors.computeIfAbsent(module) { describeModule(it) }
    }
    catch (_: NoContentRoot) {
      println("Skip ${module.name} because it has no content roots")
      return null
    }
  }

  val libs = LinkedHashSet<Library>()

  fun addModuleToQueue(module: JpsModule) {
    if (generated.putIfAbsent(module, true) == null) {
      queue.addLast(module)
    }
  }

  fun generate() {
    while (true) {
      generateBazelBuildFiles(module = queue.removeFirstOrNull() ?: break)
    }
  }

  private fun getBazelDependencyLabel(module: JpsModule): String? {
    val descriptor = getModuleDescriptor(module) ?: return null
    val contentRoot = descriptor.contentRoot
    var path = checkAndGetRelativePath(projectDir, contentRoot).invariantSeparatorsPathString
    if (path.startsWith("community/")) {
      path = "@community//${path.removePrefix("community/")}"
    }
    else {
      path = "//$path"
    }
    val dirName = contentRoot.fileName.toString()
    val bazelName = jpsModuleNameToBazelBuildName(module)
    return path + (if (dirName == bazelName) "" else ":${jpsModuleNameToBazelBuildName(module)}")
  }

  private fun generateBazelBuildFiles(module: JpsModule) {
    val moduleDescriptor = getModuleDescriptor(module) ?: return
    val contentRoot = moduleDescriptor.contentRoot
    val fileUpdater = BazelFileUpdater(contentRoot.resolve("BUILD.bazel"))
    buildFile(fileUpdater, "build") {
      val sources = computeSources(module = module, contentRoot = contentRoot, type = JavaSourceRootType.SOURCE)
      val resources = module.sourceRoots.filter { it.rootType == JavaResourceRootType.RESOURCE }

      val testSources = computeSources(module = module, contentRoot = contentRoot, type = JavaSourceRootType.TEST_SOURCE)
      //todo testResources
      @Suppress("UnusedVariable", "unused")
      val testResources = module.sourceRoots.filter { it.rootType == JavaResourceRootType.TEST_RESOURCE }

      val resourceDependencies = mutableListOf<String>()

      val isResourceOnly = sources.isEmpty()
      if (resources.isNotEmpty()) {
        generateResources(resources = resources, isResourceOnly = isResourceOnly, module = module, resourceDependencies = resourceDependencies, contentRoot = contentRoot)
      }

      val libraryTargetName = jpsModuleNameToBazelBuildName(module)
      val jvmTarget = getLanguageLevel(module)
      val kotlincOptionsLabel = computeKotlincOptions(module = module, jvmTarget = jvmTarget) ?: "//:k$jvmTarget"
      val javacOptionsLabel = computeJavacOptions(module, jvmTarget) ?: "//:j$jvmTarget"

      if (!isResourceOnly) {
        load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

        target("kt_jvm_library") {
          option("name", libraryTargetName)
          option("module_name", module.name)
          visibility(arrayOf("//visibility:public"))
          option("srcs", glob(sources))
          option("javac_opts", javacOptionsLabel)
          option("kotlinc_opts", kotlincOptionsLabel)

          generateDeps(module, resourceDependencies)
        }
      }

      if (testSources.isNotEmpty()) {
        load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_test")

        target("kt_jvm_test") {
          option("name", libraryTargetName + "_test")
          visibility(arrayOf("//visibility:public"))
          option("srcs", glob(testSources))
          option("javac_opts", javacOptionsLabel)
          option("kotlinc_opts", kotlincOptionsLabel)

          val additionalDeps = if (isResourceOnly) emptyList() else listOf(":$libraryTargetName")
          generateDeps(module = module, resourceDependencies = resourceDependencies, additionalDeps = additionalDeps, isTest = true)
        }
      }
    }
    fileUpdater.save()
  }

  private fun BuildFile.generateResources(
    resources: List<JpsModuleSourceRoot>,
    isResourceOnly: Boolean,
    module: JpsModule,
    resourceDependencies: MutableList<String>,
    contentRoot: Path,
  ) {
    load("@rules_java//java:defs.bzl", "java_library")
    for (resourceRoot in resources) {
      target("java_library") {
        val resourceDependency = if (isResourceOnly) jpsModuleNameToBazelBuildName(module) else resourceRoot.path.fileName.toString()
        option("name", resourceDependency)
        if (isResourceOnly) {
          visibility(arrayOf("//visibility:public"))
        }
        else {
          resourceDependencies.add(resourceDependency)
        }

        val prefix = checkAndGetRelativePath(contentRoot, resourceRoot.path).invariantSeparatorsPathString
        option("resources", glob(listOf("$prefix/**/*")))
      }
    }
  }

  private fun jpsModuleNameToBazelBuildName(module: JpsModule): @NlsSafe String {
    return module.name
      .removePrefix("intellij.platform.")
      .removePrefix("intellij.idea.community.")
      .removePrefix("intellij.")
      .replace('.', '-')
  }

  private fun BuildFile.computeJavacOptions(module: JpsModule, jvmTarget: String): String? {
    val extraJavacOptions = projectJavacSettings.currentCompilerOptions.ADDITIONAL_OPTIONS_OVERRIDE.get(module.name) ?: return null
    val exports = mutableListOf<String>()
    val regex = Regex("""--add-exports\s+([^=]+)=\S+""")
    val matches = regex.findAll(extraJavacOptions)
    for (match in matches) {
      exports.add(match.groupValues[1] + "=ALL-UNNAMED")
    }

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

  private fun BuildFile.computeKotlincOptions(module: JpsModule, jvmTarget: String): String? {
    val kotlinFacetModuleExtension = module.container.getChild(JpsKotlinFacetModuleExtension.KIND) ?: return null
    val optIn = kotlinFacetModuleExtension.settings.mergedCompilerArguments?.optIn ?: return null
    // see create_kotlinc_options
    val effectiveOptIn = optIn.toMutableList()
    effectiveOptIn.remove("com.intellij.openapi.util.IntellijInternalApi")
    if (effectiveOptIn.isEmpty()) {
      return null
    }

    load("@community//:build/compiler-options.bzl", "create_kotlinc_options")

    val kotlincOptionsName = "custom"
    target("create_kotlinc_options") {
      option("name", kotlincOptionsName)
      option("jvm_target", if (jvmTarget == "8") "1.8" else jvmTarget)
      @Suppress("SpellCheckingInspection")
      option("x_optin", effectiveOptIn)
    }
    return ":$kotlincOptionsName"
  }

  private fun computeSources(module: JpsModule, contentRoot: Path, type: JpsModuleSourceRootType<*>): List<String> {
    return module.sourceRoots.asSequence()
      .filter { it.rootType == type }
      .flatMap { it ->
        val dir = it.path
        var prefix = checkAndGetRelativePath(contentRoot, dir).invariantSeparatorsPathString
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

  private fun Target.generateDeps(
    module: JpsModule,
    resourceDependencies: List<String>,
    isTest: Boolean = false,
    additionalDeps: List<String> = emptyList(),
  ) {
    val deps = ArrayList<String>()
    deps.addAll(additionalDeps)
    val exports = mutableListOf<String>()
    val runtimeDeps = mutableListOf<String>()

    resourceDependencies.mapTo(runtimeDeps) { ":$it" }

    for (element in module.dependenciesList.dependencies) {
      val dependencyExtension = javaExtensionService.getDependencyExtension(element) ?: continue
      val scope = dependencyExtension.scope

      if (element is JpsModuleDependency) {
        val dependency = element.moduleReference.resolve()!!
        // todo runtime dependency (getBazelDependencyLabel() is null only because fake "main" modules do not have content roots, and we don't know where to create BUILD file)
        val label = getBazelDependencyLabel(dependency) ?: continue
        addDep(isTest = isTest, scope = scope, deps = deps, libLabel = label, runtimeDeps = runtimeDeps)
        addModuleToQueue(dependency)
        if (dependencyExtension.isExported && !isTest) {
          exports.add(label)
        }
      }
      else if (element is JpsLibraryDependency) {
        val untypedLib = element.library!!
        val library = untypedLib.asTyped(JpsRepositoryLibraryType.INSTANCE)
        if (library == null) {
          val targetName = untypedLib.name.lowercase()
          // todo module-level libs
          if (!targetName.startsWith('#')) {
            deps.add("@community//lib:$targetName")
          }
          continue
        }

        val data = library.properties.data
        val bazelLabel = "@maven//:" + "${data.groupId}_${data.artifactId}".replace('.', '_').replace('-', '_')
        var isProvided = false
        var targetName = data.artifactId
        if (scope == JpsJavaDependencyScope.PROVIDED) {
          isProvided = true
          targetName += ".provided"
        }

        val libLabel = "@libraries//:$targetName"
        addDep(isTest = isTest, scope = scope, deps = deps, libLabel = libLabel, runtimeDeps = runtimeDeps)

        if (dependencyExtension.isExported && !isTest) {
          exports.add(libLabel)
        }

        libs.add(
          Library(
            targetName = targetName,
            bazelLabel = bazelLabel,
            mavenCoordinates = "${data.groupId}:${data.artifactId}:${data.version}",
            isProvided = isProvided,
            // todo isCommunity
            isCommunity = true,
          )
        )

        if (data.artifactId == "kotlinx-serialization-core-jvm") {
          option("plugins", arrayOf("@libraries//:serialization_plugin"))
        }
      }
    }

    if (deps.isNotEmpty()) {
      option("deps", deps)
    }
    if (exports.isNotEmpty()) {
      option("exports", exports)
    }
    if (runtimeDeps.isNotEmpty()) {
      option("runtime_deps", runtimeDeps)
    }
  }
}

private fun addDep(
  isTest: Boolean,
  scope: JpsJavaDependencyScope,
  deps: ArrayList<String>,
  libLabel: String,
  runtimeDeps: MutableList<String>,
) {
  when {
    isTest -> {
      if (scope == JpsJavaDependencyScope.TEST) {
        deps.add(libLabel)
      }
    }
    scope == JpsJavaDependencyScope.RUNTIME -> {
      runtimeDeps.add(libLabel)
    }
    scope == JpsJavaDependencyScope.COMPILE || scope == JpsJavaDependencyScope.PROVIDED -> {
      deps.add(libLabel)
    }
  }
}

private fun checkAndGetRelativePath(parentDir: Path, childDir: Path): Path {
  require(childDir.startsWith(parentDir)) {
    "$childDir must be a child of parentDir $parentDir"
  }
  return parentDir.relativize(childDir)
}