// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.model.module.JpsTestModuleProperties
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo

internal data class ModuleDeps(
  @JvmField val deps: List<String>,
  @JvmField val provided: List<String>,
  @JvmField val runtimeDeps: List<String>,
  @JvmField val exports: List<String>,
  @JvmField val associates: List<String>,
  @JvmField val plugins: List<String>,
)

internal fun generateDeps(
  module: ModuleDescriptor,
  hasSources: Boolean,
  isTest: Boolean,
  context: BazelBuildFileGenerator,
): ModuleDeps {
  val deps = mutableListOf<String>()
  val associates = mutableListOf<String>()
  val exports = mutableListOf<String>()
  val runtimeDeps = mutableListOf<String>()
  val provided = mutableListOf<String>()
  val plugins = TreeSet<String>()

  if (isTest && module.sources.isNotEmpty()) {
    // associates also is a dependency
    associates.add(":${module.targetName}")
  }

  val testModuleProperties = context.javaExtensionService.getTestModuleProperties(module.module)

  val dependentModuleName = module.module.name
  for (element in module.module.dependenciesList.dependencies) {
    val dependencyExtension = context.javaExtensionService.getDependencyExtension(element) ?: continue
    val scope = dependencyExtension.scope
    val isProvided = scope == JpsJavaDependencyScope.PROVIDED

    if (element is JpsModuleDependency) {
      val dependencyModule = element.moduleReference.resolve()!!
      // todo runtime dependency (getBazelDependencyLabel() is null only because fake "main" modules do not have content roots, and we don't know where to create BUILD file)
      val dependencyModuleDescriptor = context.getKnownModuleDescriptorOrError(dependencyModule)
      val label = context.getBazelDependencyLabel(module = dependencyModuleDescriptor, dependent = module) ?: continue

      // intellij.platform.configurationStore.tests uses internal symbols from intellij.platform.configurationStore.impl
      val dependencyModuleName = dependencyModule.name
      val effectiveDepContainer = if (isTestFriend(dependentModuleName, dependencyModuleName, testModuleProperties)) {
        associates
      }
      else {
        deps
      }
      addDep(
        isTest = isTest,
        scope = scope,
        deps = effectiveDepContainer,
        dependencyLabel = label,
        runtimeDeps = runtimeDeps,
        hasSources = hasSources,
        dependentModule = module,
        dependencyModuleDescriptor = dependencyModuleDescriptor,
        exports = exports,
        provided = provided,
        isExported = dependencyExtension.isExported,
      )

      if (dependencyModuleName == "intellij.libraries.compose.desktop" ||
          dependencyModuleName == "intellij.platform.jewel.markdown.ideLafBridgeStyling" ||
          dependencyModuleName == "intellij.platform.jewel.foundation") {
        plugins.add("@lib//:compose-plugin")
      }
    }
    else if (element is JpsLibraryDependency) {
      val untypedLib = element.library!!
      val library = untypedLib.asTyped(JpsRepositoryLibraryType.INSTANCE)
      if (library == null) {
        val files = untypedLib.getPaths(JpsOrderRootType.COMPILED)
        val firstFile = files.first()
        val targetName = camelToSnakeCase(escapeBazelLabel(firstFile.nameWithoutExtension))
        val isCommunityLibrary = firstFile.startsWith(context.communityDir)

        val libBuildFileDir = firstFile.relativeTo(if (isCommunityLibrary) context.communityDir else projectDir).parent.invariantSeparatorsPathString
        context.addLocalLibrary(
          LocalLibrary(
            files = files,
            lib = Library(targetName = targetName, isCommunity = isCommunityLibrary),
          ),
          isProvided,
        )

        if (!isCommunityLibrary) {
          require(!module.isCommunity)
        }

        val prefix = when {
          libBuildFileDir == "lib" -> if (isCommunityLibrary) "@lib//" else "@ultimate_lib//"
          libBuildFileDir.startsWith("lib/") -> libBuildFileDir.replace("lib/", if (isCommunityLibrary) "@lib//" else "@ultimate_lib//")
          else -> "${if (module.isCommunity || !isCommunityLibrary) "//" else "@community//"}${libBuildFileDir.removePrefix("community/")}"
        }

        deps.add("$prefix:$targetName${if (isProvided) PROVIDED_SUFFIX else ""}")
        continue
      }

      val data = library.properties.data
      val isModuleLibrary = element.libraryReference.parentReference is JpsModuleReference

      var targetName = camelToSnakeCase(
        escapeBazelLabel(
          name = if (isModuleLibrary) {
          val moduleRef = element.libraryReference.parentReference as JpsModuleReference
          val name = library.name.takeIf { !it.startsWith("#") && it.isNotEmpty() } ?: "${data.artifactId}_${data.version}"
          "${moduleRef.moduleName.removePrefix("intellij.")}_${name}"
        }
        else {
          library.name
        }
      ))

      var isLibCommunity = module.isCommunity
      if (isProvided) {
        if (!isLibCommunity && context.libs.any { it.lib.isCommunity && it.lib.targetName == targetName }) {
          isLibCommunity = true
        }
      }

      // we process community modules first, so, `addOrGet` (library equality ignores `isCommunity` flag)
      val isCommunityLibrary = context.addMavenLibrary(
        MavenLibrary(
          mavenCoordinates = "${data.groupId}:${data.artifactId}:${data.version}",
          jars = library.getPaths(JpsOrderRootType.COMPILED),
          sourceJars = library.getPaths(JpsOrderRootType.SOURCES),
          javadocJars = library.getPaths(JpsOrderRootType.DOCUMENTATION),
          lib = Library(
            targetName = targetName,
            isCommunity = isLibCommunity,
          ),
        ),
        isProvided,
      ).lib.isCommunity

      val libLabel = getLibLabel(isCommunityLibrary = isCommunityLibrary, targetName = targetName, isProvided = isProvided)

      addDep(
        isTest = isTest,
        scope = scope,
        deps = deps,
        dependencyLabel = libLabel,
        runtimeDeps = runtimeDeps,
        hasSources = hasSources,
        dependentModule = module,
        dependencyModuleDescriptor = null,
        exports = exports,
        provided = provided,
        isExported = dependencyExtension.isExported,
      )

      val libName = element.libraryReference.libraryName
      if (libName == "jetbrains-jewel-markdown-laf-bridge-styling" ||
          libName == "org.jetbrains.compose.foundation.foundation.desktop" ||
          libName == "jetbrains.kotlin.compose.compiler.plugin") {
        plugins.add("@lib//:compose-plugin")
        // poko fails with "java.lang.NoSuchMethodError: 'org.jetbrains.kotlin.com.intellij.psi.PsiElement org.jetbrains.kotlin.js.resolve.diagnostics.SourceLocationUtilsKt.findPsi(org.jetbrains.kotlin.descriptors.DeclarationDescriptor)'"
        //plugins.add("@lib//:poko-plugin")
      }
    }
  }

  if (exports.isNotEmpty()) {
    require(!exports.contains("@lib//:kotlinx-serialization-core")) {
      "Do not export kotlinx-serialization-core (module=$dependentModuleName})"
    }
    require(!exports.contains("jetbrains-jewel-markdown-laf-bridge-styling")) {
      "Do not export jetbrains-jewel-markdown-laf-bridge-styling (module=$dependentModuleName})"
    }
  }
  return ModuleDeps(deps = deps, associates = associates, runtimeDeps = runtimeDeps, exports = exports, provided = provided, plugins = plugins.toList())
}

private fun getLibLabel(isCommunityLibrary: Boolean, targetName: String, isProvided: Boolean): String {
  var libLabel = "${if (isCommunityLibrary) "@lib" else "@ultimate_lib"}//:$targetName"
  if (isProvided) {
    libLabel += PROVIDED_SUFFIX
  }
  return libLabel
}

private fun isTestFriend(
  dependentModuleName: @NlsSafe String,
  dependencyModuleName: @NlsSafe String,
  testModuleProperties: JpsTestModuleProperties?,
): Boolean {
  if (testModuleProperties != null) {
    return testModuleProperties.productionModuleReference.moduleName == dependencyModuleName
  }
  return dependentModuleName.endsWith(".tests") &&
         dependentModuleName != "intellij.platform.core.nio.fs" &&
         (dependencyModuleName.endsWith(".impl")) &&
         dependentModuleName.removeSuffix(".tests") == dependencyModuleName.removeSuffix(".impl")
}

private fun addDep(
  isTest: Boolean,
  scope: JpsJavaDependencyScope,
  deps: MutableList<String>,
  dependencyLabel: String,
  runtimeDeps: MutableList<String>,
  hasSources: Boolean,
  dependentModule: ModuleDescriptor,
  dependencyModuleDescriptor: ModuleDescriptor?,
  exports: MutableList<String>,
  provided: MutableList<String>,
  isExported: Boolean,
) {
  if (isTest) {
    when (scope) {
      JpsJavaDependencyScope.COMPILE -> {
        deps.add(dependencyLabel)

        if (dependencyModuleDescriptor != null && dependencyModuleDescriptor.testSources.isNotEmpty()) {
          deps.add(getLabelForTest(dependencyLabel))
        }
      }
      JpsJavaDependencyScope.TEST, JpsJavaDependencyScope.PROVIDED -> {
        if (dependencyModuleDescriptor == null) {
          deps.add(dependencyLabel)
        }
        else {
          val hasTestSource = dependencyModuleDescriptor.testSources.isNotEmpty()

          if (isExported && hasTestSource) {
            println("Do not export test dependency (module=${dependentModule.module.name}, exported=${dependencyModuleDescriptor.module.name})")
          }

          if (dependencyModuleDescriptor.sources.isNotEmpty() || !hasTestSource) {
            deps.add(dependencyLabel)
          }
          if (hasTestSource) {
            deps.add(getLabelForTest(dependencyLabel))
          }
        }
      }
      JpsJavaDependencyScope.RUNTIME -> {
        if (dependentModule.sources.isEmpty()) {
          runtimeDeps.add(dependencyLabel)
        }
      }
    }

    return
  }

  if (isExported) {
    exports.add(dependencyLabel)
  }

  when (scope) {
    JpsJavaDependencyScope.RUNTIME -> {
      runtimeDeps.add(dependencyLabel)
    }
    JpsJavaDependencyScope.COMPILE -> {
      if (hasSources) {
        deps.add(dependencyLabel)
      }
      else {
        if (!isExported) {
          println("WARN: dependency scope for $dependencyLabel should be RUNTIME and not COMPILE (module=${dependentModule.module.name})")
        }
        runtimeDeps.add(dependencyLabel)
      }
    }
    JpsJavaDependencyScope.PROVIDED -> {
      // ignore deps if no sources, as `exports` in Bazel means "compile" scope
      if (hasSources) {
        if (dependencyModuleDescriptor == null) {
          // lib supports `provided`
          deps.add(dependencyLabel)
        }
        else {
          provided.add(dependencyLabel)
        }
      }
      else {
        println("WARN: ignoring dependency on $dependencyLabel (module=$dependentModule)")
      }
    }
    JpsJavaDependencyScope.TEST -> {
      // we produce separate Bazel targets for production and test source roots
    }
  }
}

internal const val TEST_LIB_NAME_SUFFIX = "_test_lib"

private fun getLabelForTest(dependencyLabel: String): String {
  if (dependencyLabel.contains(':')) {
    return "${dependencyLabel}$TEST_LIB_NAME_SUFFIX"
  }
  else {
    return "$dependencyLabel:${dependencyLabel.substringAfterLast('/')}$TEST_LIB_NAME_SUFFIX"
  }
}

private val camelCaseToSnakeCasePattern = Regex("(?<=.)[A-Z]")

private fun camelToSnakeCase(s: String): String {
  return when {
    s.startsWith("JUnit") -> "junit" + s.removePrefix("JUnit")
    s.all { it.isUpperCase() } -> s.lowercase()
    else -> s.replace(" ", "").replace("_RC", "_rc").replace("SNAPSHOT", "snapshot").replace(camelCaseToSnakeCasePattern, "_$0").lowercase()
  }
}

private val bazelLabelBadCharsPattern = Regex("[:.+]")

internal fun escapeBazelLabel(name: String): String = bazelLabelBadCharsPattern.replace(name, "_")