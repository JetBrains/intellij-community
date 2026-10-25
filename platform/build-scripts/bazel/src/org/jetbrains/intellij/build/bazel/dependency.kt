// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
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
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo

internal data class BazelLabel(
  val label: String,
  // module if label is produced from that module, null in case of a library
  val module: ModuleDescriptor? = null,
) : Renderable, Comparable<BazelLabel> {
  override fun render(): String = "\"$label\""
  override fun compareTo(other: BazelLabel): Int = BazelLabelComparator().compare(this.label, other.label)
}

internal data class ModuleDeps(
  @JvmField val deps: List<BazelLabel>,
  @JvmField val provided: List<BazelLabel>,
  @JvmField val runtimeDeps: List<BazelLabel>,
  /** Dependencies that should be packed together with this module in the plugin distribution; used to populate `packed_deps` attribute in `ij_plugin_module` rule */
  @JvmField val packedDeps: List<BazelLabel>,
  @JvmField val exports: List<BazelLabel>,
  @JvmField val associates: List<BazelLabel>,
  @JvmField val plugins: List<String>,
  /** The plain labels of the `PROVIDED` repository module libraries. [GZIP_RESOURCE_MODULES] read their jars. */
  @JvmField val providedModuleLibraries: List<BazelLabel> = emptyList(),
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
  val packedDeps = mutableListOf<BazelLabel>()
  val provided = mutableListOf<BazelLabel>()
  val providedModuleLibraries = mutableListOf<BazelLabel>()

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
      if (files.isEmpty()) {
        error("library '${jpsLibrary.name}' in module ${module.module.name} has no compiled roots")
      }

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
      val parentLibraryReference = element.libraryReference.parentReference
      val moduleLibraryModuleName = if (parentLibraryReference is JpsModuleReference) parentLibraryReference.moduleName else null
      when {
        // Library from .m2 or from any other place with a snapshot version, or repository kotlin dev libraries treated as snapshot
        isSnapshotVersion || isKotlinDevVersionAsSnapshotOutsideOfTree -> {
          val firstFile = files.first()
          val libraryContainer = context.getLibraryContainer(module.isCommunity)
          val libSnapshotsDir = when (context.snapshotLibraryMode) {
            SnapshotLibraryMode.WRITE_TO_REPO -> libraryContainer.buildFile.parent.resolve("snapshots").createDirectories()
            SnapshotLibraryMode.REUSE_GENERATED -> libraryContainer.buildFile.parent.resolve("snapshots")
          }
          val targetName = camelToSnakeCase(escapeBazelLabel(firstFile.nameWithoutExtension))

          val localFiles = files.map { file ->
            val localFile = libSnapshotsDir.resolve(file.name)
            when (context.snapshotLibraryMode) {
              SnapshotLibraryMode.WRITE_TO_REPO -> file.copyTo(localFile, overwrite = true)
              SnapshotLibraryMode.REUSE_GENERATED -> Unit
            }
            localFile
          }

          val libraryTarget = LibraryTarget(
            targetName = targetName,
            container = libraryContainer,
            jpsName = jpsLibrary.name,
            moduleLibraryModuleName = moduleLibraryModuleName,
          )
          val library = LocalLibrary(files = localFiles, target = libraryTarget, bazelBuildFileDir = libSnapshotsDir)
          context.addLocalLibrary(lib = library, isProvided = isProvided)

          val dependencyLabel = BazelLabel(
            libraryDependencyLabel(
              library = library,
              container = libraryContainer,
              communityRoot = context.communityRoot,
              ultimateRoot = context.ultimateRoot,
              isCommunityDependent = module.isCommunity,
              isProvided = isProvided,
            ),
            null,
          )
          addDep(
            isTest = isTest,
            scope = scope,
            deps = deps,
            dependencyLabel = dependencyLabel,
            runtimeDeps = runtimeDeps,
            hasSources = hasSources,
            dependentModule = module,
            dependencyModuleDescriptor = null,
            exports = exports,
            provided = provided,
            isExported = isExported,
          )
          addPackedDepIfNeeded(dependencyLabel, packedDeps, isTest, scope, moduleLibraryModuleName)
        }

        // repositoryJpsLibrary == null
        // non-repository library, meaning library files are under VCS
        // or from "-SNAPSHOT" versions already resolved to .m2/repo
        repositoryJpsLibrary == null -> {
          val firstFile = files.first()
          val isCommunityLib = firstFile.startsWith(context.communityRoot)
          val libraryContainer = context.getLibraryContainer(isCommunityLib)

          val isModuleLibrary = element.libraryReference.parentReference is JpsModuleReference
          val targetName = if (underKotlinSnapshotLibRoot(firstFile, communityRoot = context.communityRoot) && !isModuleLibrary) {
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

          val library = LocalLibrary(files = files, target = libraryTarget, bazelBuildFileDir = bazelFileDir)
          context.addLocalLibrary(lib = library, isProvided = isProvided)

          if (!isCommunityLib) {
            require(!module.isCommunity) {
              "Module ${module.module.name} must not depend on a non-community libraries because it is a community module" +
              "(library=${jpsLibrary.name}, files=$files, bazelTargetName=$targetName)"
            }
          }

          val dependencyLabel = BazelLabel(
            libraryDependencyLabel(
              library = library,
              container = libraryContainer,
              communityRoot = context.communityRoot,
              ultimateRoot = context.ultimateRoot,
              isCommunityDependent = module.isCommunity,
              isProvided = isProvided,
            ),
            null,
          )
          addDep(
            isTest = isTest,
            scope = scope,
            deps = deps,
            dependencyLabel = dependencyLabel,
            runtimeDeps = runtimeDeps,
            hasSources = hasSources,
            dependentModule = module,
            dependencyModuleDescriptor = null,
            exports = exports,
            provided = provided,
            isExported = isExported,
          )
          addPackedDepIfNeeded(dependencyLabel, packedDeps, isTest, scope, moduleLibraryModuleName)
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

          // always use kotlinc libraries from community to reduce the noise on `kt-master` merge
          // `kotlin.util.compiler-dependencies` module guaranties that all kotlinc libraries are used in the community part
          val isKotlincLib = jpsLibrary.name.startsWith("kotlinc.")
          val isCommunityOrKotlinc = module.isCommunity || isKotlincLib

          var libraryContainer = context.getLibraryContainer(isCommunityOrKotlinc)

          // we process community modules first, so, `addOrGet` (library equality ignores `isCommunity` flag)
          val library = context.addMavenLibrary(
            MavenLibrary(
              mavenCoordinates = "${jpsMavenLibraryDescriptor.groupId}:${jpsMavenLibraryDescriptor.artifactId}:${jpsMavenLibraryDescriptor.version}",
              jars = repositoryJpsLibrary.getPaths(JpsOrderRootType.COMPILED).map { getFileMavenFileDescription(m2Repo, repositoryJpsLibrary, it) },
              sourceJars = repositoryJpsLibrary.getPaths(JpsOrderRootType.SOURCES).map { getFileMavenFileDescription(m2Repo, repositoryJpsLibrary, it) },
              javadocJars = repositoryJpsLibrary.getPaths(JpsOrderRootType.DOCUMENTATION).map { getFileMavenFileDescription(m2Repo, repositoryJpsLibrary, it) },
              target = LibraryTarget(targetName = targetName, container = libraryContainer, jpsName = jpsLibrary.name, moduleLibraryModuleName = moduleLibraryModuleName),
            ),
            isProvided = isProvided,
          )
          libraryContainer = library.target.container

          val containerForLabel = if (isProvided) {
            // provided libraries for ultimate are defined in ultimate, but not kotlinc.* as per ^^
            // provided libraries for community are defined in community
            context.getLibraryContainer(isCommunityOrKotlinc)
          }
          else {
            // libraries (not provided) used both in ultimate & community are defined in community
            libraryContainer
          }

          val libLabel = BazelLabel(
            libraryDependencyLabel(
              library = library,
              container = containerForLabel,
              communityRoot = context.communityRoot,
              ultimateRoot = context.ultimateRoot,
              isCommunityDependent = module.isCommunity,
              isProvided = isProvided,
            ),
            module = null,
          )

          if (isProvided && isModuleLibrary) {
            val plainLabel = libraryDependencyLabel(
              library = library,
              container = containerForLabel,
              communityRoot = context.communityRoot,
              ultimateRoot = context.ultimateRoot,
              isCommunityDependent = module.isCommunity,
              isProvided = false,
            )
            providedModuleLibraries.add(BazelLabel(plainLabel, module = null))
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
          addPackedDepIfNeeded(libLabel, packedDeps, isTest, scope, moduleLibraryModuleName)
        }
      }
    }
  }

  if (exports.isNotEmpty() && !dependentModuleName.startsWith(LIB_MODULE_PREFIX)) {
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
  // other modules use exported_compiler_plugins
  if (module.module.name == "fleet.noria.cells") {
    plugins.add("@community//fleet/compiler-plugins/noria:noria-plugin")
  }

  checkForDuplicates("bazel deps", deps)
  checkForDuplicates("bazel associates", associates)
  checkForDuplicates("bazel runtimeDeps", runtimeDeps)
  checkForDuplicates("bazel packedDeps", packedDeps)
  checkForDuplicates("bazel exports", exports)
  checkForDuplicates("bazel provided", provided)

  return ModuleDeps(deps = deps, associates = associates, runtimeDeps = runtimeDeps, packedDeps = packedDeps, exports = exports, provided = provided, plugins = plugins.toList(),
                    providedModuleLibraries = providedModuleLibraries)
}

private fun addPackedDepIfNeeded(
  dependencyLabel: BazelLabel,
  packedDeps: MutableList<BazelLabel>,
  isTest: Boolean,
  scope: JpsJavaDependencyScope,
  moduleLibraryModuleName: String?,
) {
  if (moduleLibraryModuleName != null && !isTest && scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)) {
    packedDeps.add(dependencyLabel)
  }
}

private fun getLocalLibBazelFileDir(files: List<Path>, communityRoot: Path): Path {
  val dir = files.first().parent

  // Special case, kt-master development places all snapshot Kotlin libraries
  // as a maven repo under community/lib/kotlin
  if (underKotlinSnapshotLibRoot(dir, communityRoot)) {
    return communityRoot.resolve("lib")
  }

  return dir
}

internal fun underKotlinSnapshotLibRoot(dir: Path, communityRoot: Path) =
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
  //
  // Treats PROVIDED-scope dependencies as neverlink as per https://youtrack.jetbrains.com/issue/IJI-3451#focus=Comments-27-13512542.0-0.

  when (scope) {
    JpsJavaDependencyScope.COMPILE, JpsJavaDependencyScope.TEST -> {
      require(scope != JpsJavaDependencyScope.TEST || isTest) {
        "module must not have TEST-scope dependencies that only leak onto the test classpath of dependent modules, please consider changing their scope to RUNTIME or move them to the affected test modules: ${dependentModule.imlFile}"
      }
      // TODO: require scope != JpsJavaDependencyScope.COMPILE || !isTest

      if (hasSources) {
        deps.add(dependencyLabel)
      }
      else {
        // TODO: require scope != JpsJavaDependencyScope.COMPILE || isExported  -- aka TEST_RUNTIME
        runtimeDeps.add(dependencyLabel)
      }
      if (isExported) {
        exports.add(dependencyLabel)
      }
    }
    JpsJavaDependencyScope.RUNTIME -> {
      // TODO: require !isExported
      runtimeDeps.add(dependencyLabel)
    }
    JpsJavaDependencyScope.PROVIDED -> {
      require(!isTest ||
              dependentModule.module.name == "fleet.compose.conformance.tests" || dependentModule.module.name == "fleet.noria.ui.test"  // TODO: remove this
      ) {
        "module must not have PROVIDED-scope dependencies, please change their scope to TEST: ${dependentModule.imlFile}"
      }

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
        // TODO: require isExported
      }
      if (isExported) {
        exports.add(dependencyLabel)
      }
    }
  }
}

internal const val TEST_LIB_NAME_SUFFIX = "_test_lib"

internal const val PRODUCTION_RESOURCES_TARGET_SUFFIX = "_resources"

internal const val TEST_RESOURCES_TARGET_SUFFIX = "_test_resources"

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

/** The name prefix of a library wrapper module. Such a module may export the library it wraps. */
private const val LIB_MODULE_PREFIX = "intellij.libraries."
