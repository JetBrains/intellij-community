// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.library.JpsTypedLibrary
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.model.module.JpsTestModuleProperties
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import java.util.TreeSet
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
    val isExported = dependencyExtension.isExported

    if (element is JpsModuleDependency) {
      val dependencyModule = element.moduleReference.resolve()!!
      val dependencyModuleDescriptor = context.getKnownModuleDescriptorOrError(dependencyModule)
      val label = context.getBazelDependencyLabel(module = dependencyModuleDescriptor, dependent = module)

      // intellij.platform.configurationStore.tests uses internal symbols from intellij.platform.configurationStore.impl
      val dependencyModuleName = dependencyModule.name
      val effectiveDepContainer = if (isTestFriend(dependentModuleName, dependencyModuleName, testModuleProperties)) associates else deps
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
        isExported = isExported,
      )

      if (dependencyModuleName == "intellij.libraries.compose.foundation.desktop" ||
          dependencyModuleName == "intellij.android.adt.ui.compose" ||
          dependencyModuleName == "intellij.platform.jewel.markdown.ideLafBridgeStyling" ||
          dependencyModuleName == "intellij.ml.llm.libraries.compose.runtime" ||
          dependencyModuleName == "intellij.platform.jewel.foundation") {
        plugins.add("@lib//:compose-plugin")
      }
    }
    else if (element is JpsLibraryDependency) {
      val untypedLib = element.library!!
      val lib = untypedLib.asTyped(JpsRepositoryLibraryType.INSTANCE)
      if (lib == null) {
        val files = untypedLib.getPaths(JpsOrderRootType.COMPILED)
        val firstFile = files.first()
        val targetName = camelToSnakeCase(escapeBazelLabel(firstFile.nameWithoutExtension))
        val isCommunityLib = firstFile.startsWith(context.communityDir)
        val owner = context.getLibOwner(isCommunityLib)

        val libBuildFileDir = firstFile.relativeTo(owner.moduleFile.parent.parent).parent.invariantSeparatorsPathString
        context.addLocalLibrary(
          lib = LocalLibrary(files = files, lib = Library(targetName = targetName, owner = owner)),
          isProvided = isProvided,
        )

        if (!isCommunityLib) {
          require(!module.isCommunity)
        }

        val prefix = when {
          libBuildFileDir == "lib" -> if (isCommunityLib) "@lib//" else "@ultimate_lib//"
          libBuildFileDir.startsWith("lib/") -> libBuildFileDir.replace("lib/", if (isCommunityLib) "@lib//" else "@ultimate_lib//")
          else -> "${if (module.isCommunity || !isCommunityLib) "//" else "@community//"}${libBuildFileDir.removePrefix("community/")}"
        }

        addDep(
          isTest = isTest,
          scope = scope,
          deps = deps,
          dependencyLabel = "$prefix:$targetName${if (isProvided) PROVIDED_SUFFIX else ""}",
          runtimeDeps = runtimeDeps,
          hasSources = hasSources,
          dependentModule = module,
          dependencyModuleDescriptor = null,
          exports = exports,
          provided = provided,
          isExported = isExported,
        )
        continue
      }

      val data = lib.properties.data
      val isModuleLibrary = element.libraryReference.parentReference is JpsModuleReference

      //val storeLibInProject = isModuleLibrary && isExported
      val storeLibInProject = false

      val rawTargetName = if (isModuleLibrary) {
        val moduleRef = element.libraryReference.parentReference as JpsModuleReference
        if (storeLibInProject) {
          val name = requireNotNull(lib.name.takeIf { !it.startsWith("#") && it.isNotEmpty() }) {
            "Module-level library must have a name if exported: ${moduleRef.moduleName} -> $lib"
          }
          if (name == module.targetName) {
            name + "_lib"
          }
          else {
            name
          }
        }
        else {
          val name = lib.name.takeIf { !it.startsWith("#") && it.isNotEmpty() } ?: "${data.artifactId}-${data.version}"
          "${moduleRef.moduleName.removePrefix("intellij.")}-${name}"
        }
      }
      else {
        lib.name
      }
      val targetName = camelToSnakeCase(escapeBazelLabel(name = rawTargetName.removeSuffix("-final").removeSuffix(".Final")))

      var owner = context.getLibOwner(module.isCommunity)
      val communityOwner = context.getLibOwner(module.isCommunity)
      if (isProvided && owner != communityOwner && context.libs.any { it.lib.owner == communityOwner && it.lib.targetName == targetName }) {
        owner = communityOwner
      }

      if (storeLibInProject) {
        owner = owner.copy(
          buildFile = module.bazelBuildFileDir.resolve("BUILD.bazel"),
          moduleFile = owner.moduleFile.parent.parent.resolve("MODULE.bazel"),
          sectionName = "maven libs of ${module.module.name}",
          repoLabel = owner.repoLabel.removeSuffix("_lib"),
          visibility = null,
        )
      }

      // we process community modules first, so, `addOrGet` (library equality ignores `isCommunity` flag)
      owner = context.addMavenLibrary(
        MavenLibrary(
          mavenCoordinates = "${data.groupId}:${data.artifactId}:${data.version}",
          jars = lib.getPaths(JpsOrderRootType.COMPILED).map { getFileMavenFileDescription(lib, it) },
          sourceJars = lib.getPaths(JpsOrderRootType.SOURCES).map { getFileMavenFileDescription(lib, it) },
          javadocJars = lib.getPaths(JpsOrderRootType.DOCUMENTATION).map { getFileMavenFileDescription(lib, it) },
          lib = Library(targetName = targetName, owner = owner),
        ),
        isProvided = isProvided,
      ).lib.owner

      var libLabel: String
      if (storeLibInProject) {
        libLabel = ":${targetName}"
      }
      else {
        libLabel = "${owner.repoLabel}//:$targetName"
      }

      if (isProvided) {
        libLabel += PROVIDED_SUFFIX
      }

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
        isExported = isExported,
      )

      val libName = element.libraryReference.libraryName
      if (libName == "jetbrains-jewel-markdown-laf-bridge-styling" ||
          libName == "jetbrains.kotlin.compose.compiler.plugin" ||
          libName == "jetbrains-compose-ui-test-junit4-desktop") {
        plugins.add("@lib//:compose-plugin")
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

private fun getFileMavenFileDescription(lib: JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>, jar: Path): MavenFileDescription {
  require(jar.isAbsolute) {
    "jar path must be absolute: $jar"
  }

  require(jar == jar.normalize()) {
    "jar path must not contain redundant . and .. segments: $jar"
  }

  val libraryDescriptor = lib.properties.data
  for (verification in libraryDescriptor.artifactsVerification) {
    if (JpsPathUtil.urlToNioPath(verification.url) == jar) {
      return MavenFileDescription(path = jar, sha256checksum = verification.sha256sum)
    }
  }

  return MavenFileDescription(path = jar, sha256checksum = null)
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

        if (dependencyModuleDescriptor != null && !dependencyModuleDescriptor.testSources.isEmpty()) {
          deps.add(getLabelForTest(dependencyLabel))
        }
      }
      JpsJavaDependencyScope.TEST, JpsJavaDependencyScope.PROVIDED -> {
        if (dependencyModuleDescriptor == null) {
          deps.add(dependencyLabel)
        }
        else {
          if (hasOnlyTestResources(dependencyModuleDescriptor)) {
            // module with only test resources
            runtimeDeps.add(addSuffix(dependencyLabel, TEST_RESOURCES_TARGET_SUFFIX))
            if (isExported) {
              throw RuntimeException("Do not export test dependency (module=${dependentModule.module.name}, exported=${dependencyModuleDescriptor.module.name})")
            }
          }
          else {
            val hasTestSource = !dependencyModuleDescriptor.testSources.isEmpty()

            if (isExported && hasTestSource) {
              println("Do not export test dependency (module=${dependentModule.module.name}, exported=${dependencyModuleDescriptor.module.name})")
            }

            if (!dependencyModuleDescriptor.sources.isEmpty() || !hasTestSource) {
              deps.add(dependencyLabel)
            }
            if (hasTestSource) {
              deps.add(getLabelForTest(dependencyLabel))
            }
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

private fun addSuffix(s: String, @Suppress("SameParameterValue") labelSuffix: String): String {
  val lastSlashIndex = s.lastIndexOf('/')
  return (if (s.indexOf(':', lastSlashIndex) == -1) {
    s + ":" + s.substring(lastSlashIndex + 1)
  }
  else {
    s
  }) + labelSuffix
}

internal fun hasOnlyTestResources(moduleDescriptor: ModuleDescriptor): Boolean {
  return !moduleDescriptor.testResources.isEmpty() &&
         moduleDescriptor.sources.isEmpty() &&
         moduleDescriptor.resources.isEmpty() &&
         moduleDescriptor.testSources.isEmpty()
}

internal const val TEST_LIB_NAME_SUFFIX = "_test_lib"

internal const val PRODUCTION_RESOURCES_TARGET_SUFFIX = "_resources"
internal val PRODUCTION_RESOURCES_TARGET_REGEX = Regex("^(?!.+${Regex.escape(TEST_RESOURCES_TARGET_SUFFIX)}).+${Regex.escape(PRODUCTION_RESOURCES_TARGET_SUFFIX)}(_[0-9]+)?$")

internal const val TEST_RESOURCES_TARGET_SUFFIX = "_test_resources"
internal val TEST_RESOURCES_TARGET_REGEX = Regex("^.+${Regex.escape(TEST_RESOURCES_TARGET_SUFFIX)}(_[0-9]+)?$")

private fun getLabelForTest(dependencyLabel: String): String {
  if (dependencyLabel.contains(':')) {
    return "${dependencyLabel}$TEST_LIB_NAME_SUFFIX"
  }
  else {
    return "$dependencyLabel:${dependencyLabel.substringAfterLast('/')}$TEST_LIB_NAME_SUFFIX"
  }
}

private val camelCaseToSnakeCasePattern = Regex("(?<=.)[A-Z]")

internal fun camelToSnakeCase(s: String, replacement: Char = '_'): String {
  return when {
    s.startsWith("JUnit") -> "junit" + s.removePrefix("JUnit")
    s.all { it.isUpperCase() } -> s.lowercase()
    else -> s.replace(" ", "").replace("_RC", "_rc").replace("SNAPSHOT", "snapshot").replace(camelCaseToSnakeCasePattern, "${replacement}$0").lowercase()
  }
}

internal val bazelLabelBadCharsPattern = Regex("[:.+]")

internal fun escapeBazelLabel(name: String): String = bazelLabelBadCharsPattern.replace(name, "-")