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
      val effectiveDepContainer = if (isTestFriend(dependencyModuleName, testModuleProperties)) associates else deps
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
      val jpsLibrary = element.library ?: error("library dependency '$element' from module ${module.module.name} is not resolved")
      val repositoryJpsLibrary = jpsLibrary.asTyped(JpsRepositoryLibraryType.INSTANCE)
      val targetNameSuffix = if (isProvided) PROVIDED_SUFFIX else ""
      val isModuleLibrary = element.libraryReference.parentReference is JpsModuleReference
      if (repositoryJpsLibrary == null) {
        // repositoryJpsLibrary == null
        // non-repository library, meaning library files are under VCS

        val files = jpsLibrary.getPaths(JpsOrderRootType.COMPILED)
        val firstFile = files.first()
        val isCommunityLib = firstFile.startsWith(context.communityRoot)
        val libraryContainer = context.getLibraryContainer(isCommunityLib)

        val communityOrUltimateRoot = libraryContainer.moduleFile.parent.parent
        val libBuildFileDir = firstFile.relativeTo(communityOrUltimateRoot).parent.invariantSeparatorsPathString
        val targetName = camelToSnakeCase(escapeBazelLabel(firstFile.nameWithoutExtension))
        val libraryTarget = LibraryTarget(
          targetName = targetName,
          container = libraryContainer,
          jpsName = jpsLibrary.name,
          isModuleLibrary = isModuleLibrary,
        )
        context.addLocalLibrary(
          lib = LocalLibrary(files = files, target = libraryTarget),
          isProvided = isProvided,
        )

        if (!isCommunityLib) {
          require(!module.isCommunity) {
            "Module ${module.module.name} must not depend on a non-community libraries because it is a community module" +
            "(library=${jpsLibrary.name}, files=$files, bazelTargetName=$targetName)"
          }
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
          dependencyLabel = "$prefix:$targetName$targetNameSuffix",
          runtimeDeps = runtimeDeps,
          hasSources = hasSources,
          dependentModule = module,
          dependencyModuleDescriptor = null,
          exports = exports,
          provided = provided,
          isExported = isExported,
        )
      }
      else {
        // Repository library, meaning library files are under .m2 and not under VCS

        val jpsMavenLibraryDescriptor = repositoryJpsLibrary.properties.data
        val isModuleLibrary = element.libraryReference.parentReference is JpsModuleReference

        val rawTargetName = if (isModuleLibrary) {
          val moduleRef = element.libraryReference.parentReference as JpsModuleReference
          val name = repositoryJpsLibrary.name.takeIf { !it.startsWith("#") && it.isNotEmpty() } ?: "${jpsMavenLibraryDescriptor.artifactId}-${jpsMavenLibraryDescriptor.version}"
          "${moduleRef.moduleName.removePrefix("intellij.")}-${name}"
        }
        else {
          repositoryJpsLibrary.name
        }
        val targetName = camelToSnakeCase(escapeBazelLabel(name = rawTargetName.removeSuffix("-final").removeSuffix(".Final")))

        var libraryContainer = context.getLibraryContainer(module.isCommunity)

        // we process community modules first, so, `addOrGet` (library equality ignores `isCommunity` flag)
        libraryContainer = context.addMavenLibrary(
          MavenLibrary(
            mavenCoordinates = "${jpsMavenLibraryDescriptor.groupId}:${jpsMavenLibraryDescriptor.artifactId}:${jpsMavenLibraryDescriptor.version}",
            jars = repositoryJpsLibrary.getPaths(JpsOrderRootType.COMPILED).map { getFileMavenFileDescription(repositoryJpsLibrary, it) },
            sourceJars = repositoryJpsLibrary.getPaths(JpsOrderRootType.SOURCES).map { getFileMavenFileDescription(repositoryJpsLibrary, it) },
            javadocJars = repositoryJpsLibrary.getPaths(JpsOrderRootType.DOCUMENTATION).map { getFileMavenFileDescription(repositoryJpsLibrary, it) },
            target = LibraryTarget(targetName = targetName, container = libraryContainer, jpsName = jpsLibrary.name, isModuleLibrary = isModuleLibrary),
          ),
          isProvided = isProvided,
        ).target.container

        val libLabel = "${libraryContainer.repoLabel}//:$targetName$targetNameSuffix"

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
            libName == "toolbox:jetbrains.compose.foundation.desktop" ||
            libName == "toolbox:jetbrains.compose.ui.test.junit4.desktop" ||
            libName == "jetbrains-compose-ui-test-junit4-desktop") {
          plugins.add("@lib//:compose-plugin")
        }
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

  fun checkForDuplicates(listMoniker: String, list: List<String>) {
    if (list.distinct() == list) {
      return
    }

    val duplicates = list
      .groupBy { it }
      .filter { it.value.size > 1 }
      .map { it.key }
      .sorted()
    error("Duplicate $listMoniker ${duplicates} for module '${module.module.name}',\ncheck ${module.imlFile}")
  }

  checkForDuplicates("bazel deps", deps)
  checkForDuplicates("bazel associates", associates)
  checkForDuplicates("bazel runtimeDeps", runtimeDeps)
  checkForDuplicates("bazel exports", exports)
  checkForDuplicates("bazel provided", provided)

  return ModuleDeps(deps = deps, associates = associates, runtimeDeps = runtimeDeps, exports = exports, provided = provided, plugins = plugins.toList())
}

private fun getFileMavenFileDescription(lib: JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>, jar: Path): MavenFileDescription {
  require(jar.isAbsolute) {
    "jar path for jps library ${lib.name} must be absolute: $jar"
  }

  require(jar == jar.normalize()) {
    "jar path for jps library ${lib.name} must not contain redundant . and .. segments: $jar"
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
  dependencyModuleName: @NlsSafe String,
  testModuleProperties: JpsTestModuleProperties?,
): Boolean {
  if (testModuleProperties != null) {
    return testModuleProperties.productionModuleReference.moduleName == dependencyModuleName
  }
  return false
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
          if (needsBackwardCompatibleTestDependency(dependencyModuleDescriptor.module.name, dependentModule)) {
            deps.add(getLabelForTest(dependencyLabel))
          }
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

private fun needsBackwardCompatibleTestDependency(
  name: @NlsSafe String,
  dependentModule: ModuleDescriptor,
): Boolean {
  if (name.startsWith("intellij.platform.ide.")) {
    /// Newly extracted modules from platform-impl are not test-framework modules for sure, and no one should depend on their test targets.
    // todo - move ToolWindowManagerTest from platform-lang to platform-impl tests
    return name == "intellij.platform.ide.impl" && dependentModule.module.name == "intellij.platform.lang.tests"
  }
  else {
    // If we depend on module A and A includes test sources, we must add a dependency not only on A’s production library target but also on its test library target.
    // See: https://youtrack.jetbrains.com/issue/IJI-2851/ (auto-add dependency on test target only for existing bad modules and forbid it for everything else).
    return true
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