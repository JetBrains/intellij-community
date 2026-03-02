// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.jps.model.JpsGlobal
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
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo

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
)

internal fun generateDeps(
  m2Repo: Path,
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

  if (isTest) {  // test always depends on production
    if (hasSources && module.sources.isNotEmpty()) {
      // associates also is a dependency
      associates.add(BazelLabel(":${module.targetName}", module))
    }
    else {
      runtimeDeps.add(BazelLabel(":${module.targetName}", module))
    }
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
      if (element.libraryReference.parentReference.resolve() is JpsGlobal) {
        // <orderEntry type="library" name="Python 3.9 interpreter library" level="application" />
        // application level references are something we should not handle (it's outside the current project model anyway)
        println("WARN: application-level library reference '${element.libraryReference}' in module ${module.module.name}, ignored")
        continue
      }

      val jpsLibrary = element.library ?: error("library dependency '$element' from module ${module.module.name} is not resolved")
      val files: List<Path> = jpsLibrary.getPaths(JpsOrderRootType.COMPILED)
      val repositoryJpsLibrary = jpsLibrary.asTyped(JpsRepositoryLibraryType.INSTANCE)
      val isSnapshotOutsideOfTree = files.any { it.name.endsWith("-SNAPSHOT.jar") } &&
                                    files.all { !it.startsWith(context.communityRoot) &&
                                                context.ultimateRoot?.let { ultimateRoot -> !it.startsWith(ultimateRoot) } ?: true }
      val isSnapshotVersion = isSnapshotOutsideOfTree ||
                              repositoryJpsLibrary?.properties?.data?.version?.endsWith("-SNAPSHOT") == true
      val isKotlinDevVersionAsSnapshotOutsideOfTree = System.getenv("JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT")?.let { kotlinCompilerCliVersion ->
        val m2OrgJetBrainsKotlin = m2Repo.resolve("org").resolve("jetbrains").resolve("kotlin")
        files.any { it.name.endsWith("-$kotlinCompilerCliVersion.jar") } && files.all { it.startsWith(m2OrgJetBrainsKotlin) }
      } ?: false
      val targetNameSuffix = if (isProvided) PROVIDED_SUFFIX else ""
      val parentLibraryReference = element.libraryReference.parentReference
      val moduleLibraryModuleName = if (parentLibraryReference is JpsModuleReference) parentLibraryReference.moduleName else null
      when {
        // Library from .m2 or from any other place with a snapshot version, or repository kotlin dev libraries treated as snapshot
        isSnapshotVersion || isKotlinDevVersionAsSnapshotOutsideOfTree -> {
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
            moduleLibraryModuleName = moduleLibraryModuleName,
          )
          context.addLocalLibrary(
            lib = LocalLibrary(
              files = localFilesWithChecksum,
              target = libraryTarget,
              bazelBuildFileDir = libSnapshotsDir,
            ),
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
        // or from "-SNAPSHOT" versions already resolved to .m2/repo
        repositoryJpsLibrary == null -> {
          val firstFile = files.first()
          val isCommunityLib = firstFile.startsWith(context.communityRoot)
          val libraryContainer = context.getLibraryContainer(isCommunityLib)

          val isModuleLibrary = element.libraryReference.parentReference is JpsModuleReference
          val targetName = if (underKotlinSnapshotLibRoot(firstFile, communityRoot = context.communityRoot)) {
            // name the same way as a maven library, so there will be minimal changes
            // migrating from kotlin from maven to kotlin from a snapshot
            escapeBazelLabel(jpsLibrary.name)
          }
          else if (isModuleLibrary) {
            val moduleRef = element.libraryReference.parentReference as JpsModuleReference
            val name = jpsLibrary.name.takeIf { !it.startsWith("#") && it.isNotEmpty() } ?: firstFile.nameWithoutExtension
            camelToSnakeCase(escapeBazelLabel("${moduleRef.moduleName.removePrefix("intellij.")}-${name}"))
          }
          else {
            // name the same way as the project library,
            // otherwise hibernate-3.6.10 and hibernate-4.1.3 will use the same identity dom4j-1-6-1 thus only one of them will be added to projectLibraries
            camelToSnakeCase(escapeBazelLabel(jpsLibrary.name))
          }

          val libraryTarget = LibraryTarget(
            targetName = targetName,
            container = libraryContainer,
            jpsName = jpsLibrary.name,
            moduleLibraryModuleName = moduleLibraryModuleName,
          )

          val bazelFileDir = getLocalLibBazelFileDir(files, communityRoot = context.communityRoot)
          check(files.all { file -> file.startsWith(bazelFileDir) }) {
            "Local (non-maven) library files must be under the same directory as the BUILD.bazel file. " +
            "Expected: ${bazelFileDir}, " +
            "got: ${files.map { it.relativeTo(bazelFileDir).invariantSeparatorsPathString }}. " +
            "Absolute paths:\n${files.joinToString("\n") { it.invariantSeparatorsPathString }}"
          }

          context.addLocalLibrary(
            lib = LocalLibrary(files = files, target = libraryTarget, bazelBuildFileDir = bazelFileDir),
            isProvided = isProvided,
          )

          if (!isCommunityLib) {
          require(!module.isCommunity) {
              "Module ${module.module.name} must not depend on a non-community libraries because it is a community module" +
              "(library=${jpsLibrary.name}, files=$files, bazelTargetName=$targetName)"
            }
          }

          val communityLibsRoot = context.communityRoot.resolve("lib")
          val ultimateLibsRoot = context.ultimateRoot?.resolve("lib")
          val prefix = when {
            // separate Bazel module 'lib'
            bazelFileDir.startsWith(communityLibsRoot) ->
              "@lib//${bazelFileDir.relativeTo(communityLibsRoot).invariantSeparatorsPathString}"
            // separate Bazel module 'ultimate_lib'
            ultimateLibsRoot != null && bazelFileDir.startsWith(ultimateLibsRoot) ->
              "@ultimate_lib//${bazelFileDir.relativeTo(ultimateLibsRoot).invariantSeparatorsPathString}"
            // Bazel module 'community'
            bazelFileDir.startsWith(context.communityRoot) ->
              "${if (module.isCommunity) "//" else "@community//"}${bazelFileDir.relativeTo(context.communityRoot).invariantSeparatorsPathString}"
            // Bazel module 'ultimate'
            context.ultimateRoot != null && bazelFileDir.startsWith(context.ultimateRoot) ->
              "//${bazelFileDir.relativeTo(context.ultimateRoot).invariantSeparatorsPathString}"
            else -> error("Unknown library root location: $bazelFileDir (community=${context.communityRoot}, ultimate=${context.ultimateRoot})")
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
              jars = repositoryJpsLibrary.getPaths(JpsOrderRootType.COMPILED).map { getFileMavenFileDescription(m2Repo, repositoryJpsLibrary, it) },
              sourceJars = repositoryJpsLibrary.getPaths(JpsOrderRootType.SOURCES).map { getFileMavenFileDescription(m2Repo, repositoryJpsLibrary, it) },
              javadocJars = repositoryJpsLibrary.getPaths(JpsOrderRootType.DOCUMENTATION).map { getFileMavenFileDescription(m2Repo, repositoryJpsLibrary, it) },
              target = LibraryTarget(targetName = targetName, container = libraryContainer, jpsName = jpsLibrary.name, moduleLibraryModuleName = moduleLibraryModuleName),
            ),
            isProvided = isProvided,
          ).target.container

          val containerForLabel = if (isProvided) {
            // provided libraries for ultimate are defined in ultimate
            // provided libraries for community are defined in community
            context.getLibraryContainer(module.isCommunity)
          }
          else {
            // libraries (not provided) used both in ultimate & community are defined in community
            libraryContainer
          }

          val libLabel = BazelLabel("${containerForLabel.repoLabel}//:$targetName$targetNameSuffix", module = null)

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

  if (exports.isNotEmpty() && !dependentModuleName.startsWith("intellij.libraries.")) {
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
    error("Duplicate $listMoniker $duplicates for module '${module.module.name}',\ncheck ${module.imlFile}")
  }

  val plugins = TreeSet<String>()
  val kotlinFacetModuleExtension = module.module.container.getChild(JpsKotlinFacetModuleExtension.KIND)
  kotlinFacetModuleExtension?.settings?.mergedCompilerArguments?.pluginClasspaths.orEmpty().map(Path::of).forEach {
    if (it.name.startsWith("kotlin-compose-compiler-plugin-") && it.name.endsWith(".jar")) {
      plugins.add("@lib//:compose-plugin")
    }
    else if (it.name.startsWith("rpc-compiler-plugin-") && it.name.endsWith(".jar")) {
      if (module.module.name == "fleet.rpc") {  // other modules use exported_compiler_plugins
        plugins.add("@community//fleet/compiler-plugins/rpc:rpc-plugin")
      }
    }
    else if (it.name.startsWith("noria-compiler-plugin-") && it.name.endsWith(".jar")) {
      if (module.module.name == "fleet.noria.cells") {
        plugins.add("@community//fleet/compiler-plugins/noria:noria-plugin")
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

private fun getLocalLibBazelFileDir(files: List<Path>, communityRoot: Path): Path {
  val dir = files.first().parent

  // Special case, kt-master development places all snapshot kotlin libraries
  // as a maven repo under community/lib/kotlin
  if (underKotlinSnapshotLibRoot(dir, communityRoot)) {
    return communityRoot.resolve("lib")
  }

  return dir
}

private fun underKotlinSnapshotLibRoot(dir: Path, communityRoot: Path) =
  dir.startsWith(communityRoot.resolve("lib").resolve("kotlin-snapshot"))

private fun getFileMavenFileDescription(m2Repo: Path, lib: JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>, jar: Path): MavenFileDescription {
  require(jar.isAbsolute) {
    "jar path for jps library ${lib.name} must be absolute: $jar"
  }

  require(jar == jar.normalize()) {
    "jar path for jps library ${lib.name} must not contain redundant . and .. segments: $jar"
  }

  val repositoryJarSegments = jar.drop(m2Repo.nameCount).toList()
  val initSubPath = repositoryJarSegments.firstOrNull() ?: throw IllegalStateException("Unable to get .m2/repository/ location for $jar")
  val jarSubPath = Path(initSubPath.toString(), *repositoryJarSegments.drop(1).map { it.toString() }.toTypedArray())

  if (jarSubPath.nameCount < 3) throw IllegalStateException("Unable to get maven coordinates for $jar by its path")

  val artifactStartIndex = jarSubPath.nameCount - 3
  if (artifactStartIndex < 0) throw IllegalStateException("Unable to get artifactId for $jar")

  val groupIdPaths = jarSubPath.subpath(0, artifactStartIndex)
  val groupId = groupIdPaths.joinToString(".")

  val version = jarSubPath.getName(jarSubPath.nameCount - 2).toString()
  val artifactId = jarSubPath.getName(jarSubPath.nameCount - 3).toString()

  val classifier = jar.name
    .removePrefixStrict("$artifactId-$version")
    .removeSuffixStrict(".jar")
    .removePrefix("-")
    .ifEmpty { null }

  val coordinates = MavenCoordinates(
    groupId = groupId,
    artifactId = artifactId,
    version = version,
    classifier = classifier,
  )

  val sha256sum = lib.properties.data.artifactsVerification
    .firstOrNull { JpsPathUtil.urlToNioPath(it.url) == jar }
    ?.sha256sum

  return MavenFileDescription(mavenCoordinates = coordinates, path = jar, sha256checksum = sha256sum)
}

private fun String.removeSuffixStrict(suffix: String): String {
  require(suffix.isNotEmpty()) {
    "suffix must not be empty"
  }

  val result = removeSuffix(suffix)
  require(result != this) {
    "String must end with $suffix: $this"
  }
  return result
}

internal fun String.removePrefixStrict(prefix: String): String {
  require(prefix.isNotEmpty()) {
    "prefix must not be empty"
  }

  val result = removePrefix(prefix)
  require(result != this) {
    "String must start with $prefix: $this"
  }
  return result
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
  // from https://jetbrains.team/p/ij/repositories/ultimate/files/84449419f2776239fb898fe350623dfe2ea074d4/community/jps/model-api/src/org/jetbrains/jps/model/java/JpsJavaDependencyScope.java?tab=source&line=27&lines-count=4:
  // - COMPILE(PRODUCTION_COMPILE, PRODUCTION_RUNTIME, TEST_COMPILE, TEST_RUNTIME)
  // - TEST(TEST_COMPILE, TEST_RUNTIME)
  // - RUNTIME(PRODUCTION_RUNTIME, TEST_RUNTIME)
  // - PROVIDED(PRODUCTION_COMPILE, TEST_COMPILE, TEST_RUNTIME)

  if (isTest) {
    val isIncludedInProductionRuntime = scope == JpsJavaDependencyScope.COMPILE || scope == JpsJavaDependencyScope.RUNTIME  // test always depends on production, skip runtime dependencies to keep the dependency graph clean
    when (scope) {
      JpsJavaDependencyScope.COMPILE, JpsJavaDependencyScope.PROVIDED, JpsJavaDependencyScope.TEST -> {
        // TODO: use non-provided label for libs to include them in test runtime
        if (hasSources) {
          deps.add(dependencyLabel)
        }
        else if (!isIncludedInProductionRuntime) {
          runtimeDeps.add(dependencyLabel)
        }
        if (isExported) {  // e.g. //debugger/intellij.java.debugger.rpc.tests:java-debugger-rpc-tests_test_lib
          exports.add(dependencyLabel)
        }

        if (dependencyModuleDescriptor != null) {
          if (hasSources && needsBackwardCompatibleTestDependency(dependencyModuleDescriptor.module.name, dependentModule)) {
            deps.add(getLabelForTest(dependencyLabel))
          }
          else {
            runtimeDeps.add(getLabelForTest(dependencyLabel))
          }
          if (isExported) {  // e.g. //CIDR-appcode/appcode-coverage:appcode-coverage_test_lib
            exports.add(getLabelForTest(dependencyLabel))
          }
        }
      }
      JpsJavaDependencyScope.RUNTIME -> {
        if (!isIncludedInProductionRuntime) {  // always false, kept for consistency
          runtimeDeps.add(dependencyLabel)
        }

        if (dependencyModuleDescriptor != null) {
          runtimeDeps.add(getLabelForTest(dependencyLabel))
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
          LOG.log(Level.FINE, "dependency scope for ${dependencyLabel.label} should be RUNTIME and not COMPILE (module=${dependentModule.module.name})")
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
        LOG.log(Level.FINE, "WARN: ignoring dependency on $dependencyLabel (module=$dependentModule)")
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

private val LOG = Logger.getLogger("dependency")
