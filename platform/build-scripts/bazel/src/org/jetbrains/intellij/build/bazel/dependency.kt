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
import org.jetbrains.kotlin.jps.model.JpsKotlinFacetModuleExtension
import java.nio.file.Path
import java.util.TreeSet
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readBytes
import kotlin.io.path.relativeToOrNull

internal data class BazelLabel(
  val label: String,
  // module if label is produced from that module, null in case of a library
  val module: ModuleDescriptor?,
) : Renderable {
  override fun render(): String = "\"$label\""
}

internal data class ModuleDeps(
  @JvmField val deps: List<BazelLabel>,
  @JvmField val provided: List<BazelLabel>,
  @JvmField val runtimeDeps: List<BazelLabel>,
  @JvmField val exports: List<BazelLabel>,
  @JvmField val associates: List<BazelLabel>,
  @JvmField val plugins: List<String>,
) {
  val depsModuleSet = deps.mapNotNull { it.module }.toSet()
  val runtimeDepsModuleSet = runtimeDeps.mapNotNull { it.module }.toSet()
}

internal fun generateDeps(
  module: ModuleDescriptor,
  hasSources: Boolean,
  isTest: Boolean,
  context: BazelBuildFileGenerator,
): ModuleDeps {
  val deps = mutableListOf<BazelLabel>()
  val associates = mutableListOf<BazelLabel>()
  val exports = mutableListOf<BazelLabel>()
  val runtimeDeps = mutableListOf<BazelLabel>()
  val provided = mutableListOf<BazelLabel>()

  if (isTest && module.sources.isNotEmpty()) {
    // associates also is a dependency
    associates.add(BazelLabel(":${module.targetName}", module))
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
      val label = BazelLabel(
        label = context.getBazelDependencyLabel(module = dependencyModuleDescriptor, dependent = module),
        module = dependencyModuleDescriptor,
      )

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
    }
    else if (element is JpsLibraryDependency) {
      val jpsLibrary = element.library ?: error("library dependency '$element' from module ${module.module.name} is not resolved")
      val files: List<Path> = jpsLibrary.getPaths(JpsOrderRootType.COMPILED)
      val repositoryJpsLibrary = jpsLibrary.asTyped(JpsRepositoryLibraryType.INSTANCE)
      val isSnapshotOutsideOfTree = files.any { it.name.endsWith("-SNAPSHOT.jar") } &&
                                    files.all { !it.startsWith(context.communityRoot) &&
                                                context.ultimateRoot?.let { ultimateRoot -> !it.startsWith(ultimateRoot) } ?: true }
      val isSnapshotVersion = isSnapshotOutsideOfTree ||
                              repositoryJpsLibrary?.properties?.data?.version?.endsWith("-SNAPSHOT") == true
      val targetNameSuffix = if (isProvided) PROVIDED_SUFFIX else ""
      val isModuleLibrary = element.libraryReference.parentReference is JpsModuleReference
      when {
        // Library from .m2 or from any other place with a snapshot version
        isSnapshotVersion -> {
          val firstFile = files.first()
          val libraryContainer = context.getLibraryContainer(module.isCommunity)
          val libSnapshotsDir = libraryContainer.buildFile.parent.resolve("snapshots").createDirectories()
          val targetName = camelToSnakeCase(escapeBazelLabel(firstFile.nameWithoutExtension))

          val localFilesWithChecksum = files.map { file ->
            val checksum = file.inputStream().sha256().take(20)
            val localFile = libSnapshotsDir.resolve("${file.nameWithoutExtension}-${checksum}.${file.extension}")
            if (!localFile.exists() || !localFile.readBytes().contentEquals(file.readBytes())) {
              file.copyTo(localFile, overwrite = true)
            }
            localFile
          }

          val libraryTarget = LibraryTarget(
            targetName = targetName,
            container = libraryContainer,
            jpsName = jpsLibrary.name,
            isModuleLibrary = false,
          )
          context.addLocalLibrary(
            lib = LocalLibrary(files = localFilesWithChecksum, target = libraryTarget),
            isProvided = isProvided,
          )

          val prefix = if (module.isCommunity) "@lib//snapshots" else "@ultimate_lib//snapshots"

          addDep(
            isTest = isTest,
            scope = scope,
            deps = deps,
            dependencyLabel = BazelLabel("$prefix:$targetName$targetNameSuffix", null),
            runtimeDeps = runtimeDeps,
            hasSources = hasSources,
            dependentModule = module,
            dependencyModuleDescriptor = null,
            exports = exports,
            provided = provided,
            isExported = isExported,
          )
        }

        // repositoryJpsLibrary == null
        // non-repository library, meaning library files are under VCS
        // or from -SNAPSHOT versions already resolved to .m2/repo
        repositoryJpsLibrary == null -> {
          val firstFile = files.first()
          val isCommunityLib = firstFile.startsWith(context.communityRoot)
          val libraryContainer = context.getLibraryContainer(isCommunityLib)

          val communityOrUltimateRoot = libraryContainer.moduleFile.parent.parent
          val libBuildFileDir = firstFile
            .relativeToOrNull(communityOrUltimateRoot)
            ?.parent?.invariantSeparatorsPathString
            ?: error("Unable to get relative path for $firstFile under $communityOrUltimateRoot" +
                     " for library ${jpsLibrary.name} from module ${module.module.name}" +
                     " (isCommunityLib=$isCommunityLib)")
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
            dependencyLabel = BazelLabel("$prefix:$targetName$targetNameSuffix", null),
            runtimeDeps = runtimeDeps,
            hasSources = hasSources,
            dependentModule = module,
            dependencyModuleDescriptor = null,
            exports = exports,
            provided = provided,
            isExported = isExported,
          )
        }

        // Repository library, meaning library files are under .m2 and not under VCS
        // repositoryJpsLibrary != null
        else -> {
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

          val libLabel = BazelLabel("${libraryContainer.repoLabel}//:$targetName$targetNameSuffix", module = null)

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
        }
      }
    }
  }

  if (exports.isNotEmpty()) {
    require(!exports.any { it.label == "@lib//:kotlinx-serialization-core" }) {
      "Do not export kotlinx-serialization-core (module=$dependentModuleName})"
    }
    require(!exports.any { it.label == "jetbrains-jewel-markdown-laf-bridge-styling"}) {
      "Do not export jetbrains-jewel-markdown-laf-bridge-styling (module=$dependentModuleName})"
    }
  }

  fun checkForDuplicates(listMoniker: String, list: List<BazelLabel>) {
    if (list.distinct() == list) {
      return
    }

    val duplicates = list
      .groupBy { it }
      .filter { it.value.size > 1 }
      .map { it.key.label }
      .sorted()
    error("Duplicate $listMoniker ${duplicates} for module '${module.module.name}',\ncheck ${module.imlFile}")
  }

  val plugins = TreeSet<String>()
  val kotlinFacetModuleExtension = module.module.container.getChild(JpsKotlinFacetModuleExtension.KIND)
  kotlinFacetModuleExtension?.settings?.mergedCompilerArguments?.pluginClasspaths.orEmpty().map(Path::of).forEach {
    if (it.name.startsWith("kotlin-compose-compiler-plugin-") && it.name.endsWith(".jar")) {
      plugins.add("@lib//:compose-plugin")
    }
    else if (it.name.startsWith("rpc-compiler-plugin-") && it.name.endsWith(".jar")) {
      if (module.module.name == "fleet.rpc") {  // other modules use exported_compiler_plugins
        plugins.add("@lib//:rpc-plugin")
      }
    }
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
  deps: MutableList<BazelLabel>,
  dependencyLabel: BazelLabel,
  runtimeDeps: MutableList<BazelLabel>,
  hasSources: Boolean,
  dependentModule: ModuleDescriptor,
  dependencyModuleDescriptor: ModuleDescriptor?,
  exports: MutableList<BazelLabel>,
  provided: MutableList<BazelLabel>,
  isExported: Boolean,
) {
  if (isTest) {
    when (scope) {
      JpsJavaDependencyScope.COMPILE -> {
        deps.add(dependencyLabel)
        if (isExported) {  // e.g. //debugger/intellij.java.debugger.rpc.tests:java-debugger-rpc-tests_test_lib
          exports.add(dependencyLabel)
        }

        if (dependencyModuleDescriptor != null && !dependencyModuleDescriptor.testSources.isEmpty()) {
          if (needsBackwardCompatibleTestDependency(dependencyModuleDescriptor.module.name, dependentModule)) {
            deps.add(getLabelForTest(dependencyLabel))
            if (isExported) {  // e.g. //CIDR-appcode/appcode-coverage:appcode-coverage_test_lib
              exports.add(getLabelForTest(dependencyLabel))
            }
          }
        }
      }
      JpsJavaDependencyScope.TEST, JpsJavaDependencyScope.PROVIDED -> {
        if (dependencyModuleDescriptor == null) {
          deps.add(dependencyLabel)
          if (isExported) {  // e.g. //python/junit5Tests:junit5Tests_test_lib
            exports.add(dependencyLabel)
          }
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
              if (isExported) {  // e.g. @community//python/python-venv:community-impl-venv_test_lib
                exports.add(dependencyLabel)
              }
            }
            if (hasTestSource) {
              deps.add(getLabelForTest(dependencyLabel))
              if (isExported) {  // e.g. //remote-dev/cwm-guest/plugins/java-frontend:java-frontend-split_test_lib
                exports.add(getLabelForTest(dependencyLabel))
              }
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
          println("WARN: dependency scope for ${dependencyLabel.label} should be RUNTIME and not COMPILE (module=${dependentModule.module.name})")
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

private fun addSuffix(s: BazelLabel, @Suppress("SameParameterValue") labelSuffix: String): BazelLabel {
  val lastSlashIndex = s.label.lastIndexOf('/')
  val labelWithSuffix = (if (s.label.indexOf(':', lastSlashIndex) == -1) {
    s.label + ":" + s.label.substring(lastSlashIndex + 1)
  }
  else {
    s.label
  }) + labelSuffix
  return s.copy(label = labelWithSuffix)
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

private fun getLabelForTest(dependencyLabel: BazelLabel): BazelLabel {
  val testLabel = if (dependencyLabel.label.contains(':')) {
    "${dependencyLabel.label}$TEST_LIB_NAME_SUFFIX"
  }
  else {
    "${dependencyLabel.label}:${dependencyLabel.label.substringAfterLast('/')}$TEST_LIB_NAME_SUFFIX"
  }
  return dependencyLabel.copy(label = testLabel)
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