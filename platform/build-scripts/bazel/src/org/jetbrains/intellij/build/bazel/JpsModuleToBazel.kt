// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SameParameterValue", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.bazel

import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.*
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
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
      generator.addModuleToQueue(nameToModule.getValue("intellij.platform.ide.impl"))
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
    for (lib in generator.libs.sortedBy { it.targetName }) {
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
  require(contentRoots.size == 1) {
    "Expected exactly one content root for module ${module.name}, got $contentRoots"
  }
  return ModuleDescriptor(
    contentRoot = contentRoots.first(),
  )
}

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
  private val productionOnly: Boolean = true,
) {
  private val javaExtensionService = JpsJavaExtensionService.getInstance()
  private val projectJavacSettings = javaExtensionService.getCompilerConfiguration(project)

  private val generated = IdentityHashMap<JpsModule, Boolean>()
  private val queue = ArrayDeque<JpsModule>()

  private val moduleDescriptors = IdentityHashMap<JpsModule, ModuleDescriptor>()

  private fun getModuleDescriptor(module: JpsModule): ModuleDescriptor {
    return moduleDescriptors.computeIfAbsent(module) { describeModule(it) }
  }

  val libs = LinkedHashSet<Library>()

  fun addModuleToQueue(module: JpsModule) {
    if (generated.putIfAbsent(module, true) != null) {
      return
    }

    queue.addLast(module)
  }

  fun generate() {
    while (true) {
      generateBazelBuildFiles(module = queue.removeFirstOrNull() ?: break)
    }
  }

  private fun getBazelDependencyLabel(module: JpsModule): String {
    val descriptor = getModuleDescriptor(module)
    val contentRoot = descriptor.contentRoot
    var path = checkAndGetRelativePath(projectDir, contentRoot).invariantSeparatorsPathString
    if (path.startsWith("community/")) {
      path = "@community//${path.removePrefix("community/")}"
    }
    else {
      path = "//$path"
    }
    return path + ":${module.name}"
  }

  private fun generateBazelBuildFiles(module: JpsModule) {
    val moduleDescriptor = getModuleDescriptor(module)
    val contentRoot = moduleDescriptor.contentRoot
    val fileUpdater = BazelFileUpdater(contentRoot.resolve("BUILD.bazel"))
    buildFile(fileUpdater, "build") {
      load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

      target("kt_jvm_library") {
        option("name", module.name)
        visibility(arrayOf("//visibility:public"))
        option("srcs", glob(computeSources(module, contentRoot)))

        val jvmTarget = getLanguageLevel(module)
        var kotlincOptionsLabel = computeKotlincOptions(module = module, jvmTarget = jvmTarget) ?: "//:k$jvmTarget"
        var javacOptionsLabel = computeJavacOptions(module, jvmTarget) ?: "//:j$jvmTarget"

        option("javac_opts", javacOptionsLabel)
        option("kotlinc_opts", kotlincOptionsLabel)

        generateDeps(module)
      }
    }
    fileUpdater.save()
  }

  // exports doesn't work for kotlin-rules - we decided that it is better just write by hand BUILD file if `java_library` is necessary (add-exports works for java rules)
  // the code below is not actual
  private fun BuildFile.computeJavacOptions(module: JpsModule, jvmTarget: String): String? {
    val extraJavacOptions = projectJavacSettings.currentCompilerOptions.ADDITIONAL_OPTIONS_OVERRIDE.get(module.name) ?: return null
    val exports = mutableListOf<String>()
    val regex = Regex("""--add-exports\s+([^=]+)=\S+""")
    val matches = regex.findAll(extraJavacOptions)
    for (match in matches) {
      exports.add(match.groupValues[1])
    }

    if (exports.isEmpty()) {
      return null
    }

    load("@rules_kotlin//kotlin:jvm.bzl", "kt_javac_options")
    val customJavacOptionsName = "custom-javac-options"
    target("kt_javac_options") {
      option("name", customJavacOptionsName)
      option("release", jvmTarget)
      option("x_ep_disable_all_checks", true)
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

  private fun computeSources(module: JpsModule, contentRoot: Path): List<String> {
    return module.sourceRoots.asSequence()
      .filter { !productionOnly || JavaModuleSourceRootTypes.PRODUCTION.contains(it.rootType) }
      .flatMap {
        var prefix = checkAndGetRelativePath(contentRoot, it.path).invariantSeparatorsPathString
        if (prefix.isNotEmpty()) {
          prefix += "/"
        }
        sequenceOf("$prefix**/*.kt", "$prefix**/*.java")
      }
      .toList()
  }

  private fun getLanguageLevel(module: JpsModule): String {
    val languageLevel = javaExtensionService.getLanguageLevel(module)
    return when {
      languageLevel == LanguageLevel.JDK_1_7 || languageLevel == LanguageLevel.JDK_1_8 -> "8"
      languageLevel == LanguageLevel.JDK_11 -> "11"
      languageLevel == LanguageLevel.JDK_17 -> "17"
      languageLevel != null -> error("Unsupported language level: $languageLevel")
      else -> "17"
    }
  }

  private fun Target.generateDeps(module: JpsModule) {
    val deps = ArrayList<String>()
    val exports = mutableListOf<String>()
    val runtimeDeps = mutableListOf<String>()

    for (element in module.dependenciesList.dependencies) {
      val dependencyExtension = javaExtensionService.getDependencyExtension(element) ?: continue
      val scope = dependencyExtension.scope
      if (productionOnly && !isProductionRuntime(withTests = false, scope = scope)) {
        continue
      }

      if (element is JpsModuleDependency) {
        val dependency = element.moduleReference.resolve()!!
        val label = getBazelDependencyLabel(dependency)
        if (scope == JpsJavaDependencyScope.RUNTIME) {
          runtimeDeps.add(label)
        }
        else {
          deps.add(label)
        }
        addModuleToQueue(dependency)
        if (dependencyExtension.isExported) {
          exports.add(label)
        }
      }
      else if (element is JpsLibraryDependency) {
        val untypedLib = element.library!!
        val library = untypedLib.asTyped(JpsRepositoryLibraryType.INSTANCE)
        if (library == null) {
          val targetName = untypedLib.name.lowercase()
          deps.add("@community//lib:$targetName")
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
        if (scope == JpsJavaDependencyScope.RUNTIME) {
          runtimeDeps.add(libLabel)
        }
        else {
          deps.add(libLabel)
        }

        if (dependencyExtension.isExported) {
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

  private fun isProductionRuntime(withTests: Boolean, scope: JpsJavaDependencyScope): Boolean {
    if (withTests && scope.isIncludedIn(JpsJavaClasspathKind.TEST_RUNTIME)) {
      return true
    }
    return scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) || scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_COMPILE)
  }
}

private fun checkAndGetRelativePath(parentDir: Path, childDir: Path): Path {
  require(childDir.startsWith(parentDir)) {
    "$childDir must be a child of parentDir $parentDir"
  }
  return parentDir.relativize(childDir)
}