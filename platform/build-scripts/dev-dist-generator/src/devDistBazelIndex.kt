// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString

/** The repository half a Bazel package belongs to. The converter spells a label by the half of the module that writes it. */
@ApiStatus.Internal
enum class RepositoryHalf {
  COMMUNITY,
  ULTIMATE,
}

/**
 * Where the converter placed one module: its repository half, its package path and its `jvm_library` target name.
 *
 * [packagePath] is relative to the root of [half] with `/` separators, and empty for a root package.
 */
@ApiStatus.Internal
class BazelModuleLocation(
  @JvmField val half: RepositoryHalf,
  @JvmField val packagePath: String,
  @JvmField val targetName: String,
) {
  /** The `@community//pkg` or `//pkg` prefix of an absolute label of this package. */
  val absolutePackage: String
    get() = when (half) {
      RepositoryHalf.COMMUNITY -> COMMUNITY_REPOSITORY_PREFIX + packagePath
      RepositoryHalf.ULTIMATE -> "//$packagePath"
    }
}

/**
 * What `build/bazel-targets.json` says about the labels and the target names the converter writes.
 *
 * The converter derives a package and a target name from the JPS model and from its custom-module table. The JSON
 * records the result, so the tool reads the result instead of repeating the derivation. Every label rule here composes
 * from the recorded absolute form. A function returns `null` for a module the JSON does not place. A skipped module and
 * an unknown module are both unplaced.
 *
 * A label inside the run keeps the recorded absolute form, `@community//pkg` for a community package, so the index
 * compares it with a label of the JSON as it is. A generated file of the run spells it through [planLabel].
 *
 * A project-relative path is relative to [projectRoot], the root of the half of the run. For the community half,
 * [projectRoot] and [communityRoot] are one directory, so every path is a community path.
 */
@ApiStatus.Internal
class DevDistBazelIndex(
  @JvmField val targets: BazelTargetsInfo.TargetsFile,
  /** The root of the half of the run. Every project-relative path of the run is relative to it. */
  @JvmField val projectRoot: Path,
  /** The community checkout: a directory below [projectRoot] for the ultimate half, and [projectRoot] itself for the community half. */
  @JvmField val communityRoot: Path = projectRoot.resolve("community"),
  /**
   * Whether the generated plan packages of the run are community packages. The community half sets it, so a plan label
   * is spelled for a community dependent, see [planLabel].
   */
  @JvmField val planPackageIsCommunity: Boolean = false,
) {
  /** The path of [communityRoot] relative to [projectRoot]: `community`, or the empty string for the community half. */
  private val communityDirectory: String = projectRoot.relativize(communityRoot).invariantSeparatorsPathString

  // The index is read beside itself, so the memo takes concurrent readers. Two readers of one module may both
  // compute it; the result is the same, and the first one in stays. `Optional` holds the `null` of an unplaced module.
  private val locations = ConcurrentHashMap<String, Optional<BazelModuleLocation>>()

  /** The `imlTargets` entry of every module, keyed by module name. Skipped modules have an entry too. */
  private val imlTargetByModule: Map<String, String> = HashMap<String, String>().also { map ->
    for (entry in targets.imlTargets) {
      val imlPath = entry.substringAfterLast(':')
      map.put(imlPath.substringAfterLast('/').removeSuffix(".iml"), entry)
    }
  }

  /** The half, package path and target name of [module], or `null` when the JSON records no production target for it. */
  fun location(module: String): BazelModuleLocation? {
    locations.get(module)?.let { return it.orElse(null) }
    val computed = Optional.ofNullable(computeLocation(module))
    return (locations.putIfAbsent(module, computed) ?: computed).orElse(null)
  }

  private fun computeLocation(module: String): BazelModuleLocation? {
    val label = moduleRuleTarget(module = module, targets = targets) ?: return null
    val repository = label.substringBefore("//")
    val half = when (repository) {
      "" -> RepositoryHalf.ULTIMATE
      COMMUNITY_REPOSITORY_NAME -> RepositoryHalf.COMMUNITY
      else -> error("The production target '$label' of module '$module' is in a repository the index does not know")
    }
    val rest = label.substringAfter("//")
    return BazelModuleLocation(half = half, packagePath = rest.substringBeforeLast(':'), targetName = rest.substringAfterLast(':'))
  }

  private val modulesByPackage: Map<String, List<String>> by lazy {
    val result = HashMap<String, MutableList<String>>()
    for (module in targets.modules.keys) {
      val location = location(module) ?: continue
      result.computeIfAbsent(location.absolutePackage) { ArrayList() }.add(module)
    }
    for (modules in result.values) {
      modules.sort()
    }
    result
  }

  /** The modules the converter placed in the package [absolutePackage], `@community//pkg` or `//pkg`, sorted by name. */
  fun modulesInPackage(absolutePackage: String): List<String> = modulesByPackage.get(absolutePackage).orEmpty()

  /** Whether the converter placed [module] in the community half. `null` when the JSON does not place it. */
  fun isCommunity(module: String): Boolean? = location(module)?.let { it.half == RepositoryHalf.COMMUNITY }

  /** The `name =` of the `jvm_library` target of [module]. */
  fun targetName(module: String): String? = location(module)?.targetName

  /** The directory that holds the `BUILD.bazel` of [module], resolved against [communityRoot] or [projectRoot]. */
  fun packageDir(module: String): Path? {
    val location = location(module) ?: return null
    val root = when (location.half) {
      RepositoryHalf.COMMUNITY -> communityRoot
      RepositoryHalf.ULTIMATE -> projectRoot
    }
    return if (location.packagePath.isEmpty()) root else root.resolve(location.packagePath)
  }

  /**
   * The label of [module] as the `BUILD.bazel` of [dependent] writes it.
   *
   * A custom module keeps its `@community//build:<name>` spelling for every dependent. A community module is `//pkg` from
   * a community dependent and `@community//pkg` from an ultimate one. The `:name` part is dropped when the last package
   * segment equals the target name, and kept for a root package. A community dependent cannot name an ultimate module,
   * and the result is `null` then.
   */
  fun dependencyLabel(module: String, dependent: String): String? {
    val dependentIsCommunity = isCommunity(dependent) ?: error("Module '$dependent' has no Bazel package, so it writes no label")
    return dependencyLabel(module = module, dependentIsCommunity = dependentIsCommunity)
  }

  /** [dependencyLabel] for a dependent package whose half is known without a module, such as a plan package. */
  fun dependencyLabel(module: String, dependentIsCommunity: Boolean): String? {
    DEFAULT_CUSTOM_MODULE_LABELS.get(module)?.let { return it }
    val location = location(module) ?: return null
    val prefix = when (location.half) {
      RepositoryHalf.ULTIMATE -> if (dependentIsCommunity) return null else "//"
      RepositoryHalf.COMMUNITY -> if (dependentIsCommunity) "//" else COMMUNITY_REPOSITORY_PREFIX
    }
    val path = location.packagePath
    val shortForm = path.isNotEmpty() && path.substringAfterLast('/') == location.targetName
    return prefix + path + (if (shortForm) "" else ":${location.targetName}")
  }

  /**
   * The absolute prefix the converter keys a content-module descriptor with, `@community//pkg` or `//pkg`.
   *
   * Read from `imlTargets`, so it is dependent-blind and stays `@community//` inside a community package. A module of a
   * standalone repository gives `@jps_to_bazel//...` or `@rules_jvm//...`.
   */
  fun bazelPackagePrefix(module: String): String? {
    imlTargetByModule.get(module)?.let { return it.substringBeforeLast(':') }
    return location(module)?.absolutePackage
  }

  /** The name of the `content_module_jar` target of [module]. The macro derives it, nothing writes it as `name =`. */
  fun contentModuleJarTargetName(module: String): String? = targetName(module)?.let { it + CONTENT_MODULE_JAR_TARGET_SUFFIX }

  /**
   * The label of the `content_module_jar` target of [module] as a dependent package of the given half writes it.
   * The label is the dependency label's package plus the target name.
   */
  fun contentModuleJarLabel(module: String, dependentIsCommunity: Boolean): String? {
    val label = dependencyLabel(module = module, dependentIsCommunity = dependentIsCommunity) ?: return null
    val name = contentModuleJarTargetName(module) ?: return null
    return label.substringBefore(':') + ":" + name
  }

  /** The name of the `dev_dist_plugin_descriptor` target of [mainModule] for [variant], which is empty for the one shared layout. */
  fun pluginDescriptorTargetName(mainModule: String, variant: String): String {
    return if (variant.isEmpty()) mainModule + DEV_DESCRIPTOR_TARGET_SUFFIX else "${mainModule}_$variant$DEV_DESCRIPTOR_TARGET_SUFFIX"
  }

  /**
   * The container label of one library by its JPS identity, as a dependent of the given half writes it.
   *
   * [owner] is `null` for a project library. An unnamed module library is keyed `#`, `#2` in both models. `null` when
   * the JSON records no container for the library.
   */
  fun libraryLabel(jpsName: String, owner: String?, dependentIsCommunity: Boolean): String? {
    val recorded = when (owner) {
      null -> targets.projectLibraries.get(jpsName)?.target
      else -> targets.modules.get(owner)?.moduleLibraries?.get(jpsName)?.target
    }
    return recorded?.let { respellLibraryLabel(it, dependentIsCommunity = dependentIsCommunity) }
  }

  /** A recorded library label as a dependent of the given half writes it: `@community//` becomes `//` for a community dependent. */
  fun respellLibraryLabel(label: String, dependentIsCommunity: Boolean): String {
    return if (dependentIsCommunity && label.startsWith(COMMUNITY_REPOSITORY_PREFIX)) "//" + label.removePrefix(COMMUNITY_REPOSITORY_PREFIX) else label
  }

  /**
   * [label], an absolute label in the recorded form, as a generated plan package of the run writes it: `@community//`
   * becomes `//` in the community half. Every other label stays as it is.
   */
  fun planLabel(label: String): String = respellLibraryLabel(label, dependentIsCommunity = planPackageIsCommunity)

  /** [projectRelativePath] relative to [communityRoot], or `null` for a path outside the community checkout. */
  fun communityRelativePath(projectRelativePath: String): String? {
    return when {
      communityDirectory.isEmpty() -> projectRelativePath
      projectRelativePath == communityDirectory -> ""
      projectRelativePath.startsWith("$communityDirectory/") -> projectRelativePath.substring(communityDirectory.length + 1)
      else -> null
    }
  }

  /**
   * The project-relative directory of the package of [moduleTarget], a label in the recorded form:
   * `@community//plugins/xpath:xpath` is `community/plugins/xpath` for the ultimate half and `plugins/xpath` for the
   * community half.
   */
  fun packageDirectory(moduleTarget: String): String {
    val withoutTargetName = moduleTarget.substringBeforeLast(':')
    val insideCommunity = when {
      withoutTargetName.startsWith(COMMUNITY_REPOSITORY_PREFIX) -> withoutTargetName.removePrefix(COMMUNITY_REPOSITORY_PREFIX)
      withoutTargetName.startsWith("//") -> return withoutTargetName.removePrefix("//")
      else -> error("Module target '$moduleTarget' names a repository this plan cannot map to a directory")
    }
    return when {
      communityDirectory.isEmpty() -> insideCommunity
      insideCommunity.isEmpty() -> communityDirectory
      else -> "$communityDirectory/$insideCommunity"
    }
  }

  /**
   * The label of [projectRelativePath] in the recorded form, composed from the Bazel package that holds the file, or
   * `null` when no directory above it holds a `BUILD.bazel`. A package below [communityRoot] is `@community//pkg`, and
   * every other package is `//pkg`.
   *
   * ### Why a second rule, and why it reads the tree
   *
   * An ultimate module keeps its resources in the community tree in seven places today, `intellij.php.dev` and the six
   * `dotenv-ultimate` ones. `getModuleDescriptor` walks such a module's Bazel package up until it holds every content
   * root, which lands it on the ultimate root, and `exportDescriptorFiles` then writes the `exports_files` entry there.
   * **That entry is inert.** `community` is a `.bazelignore` entry of the ultimate root, so Bazel leaves the whole subtree
   * out of the main repository's execroot symlink farm: `//:community/<path>` resolves and analyses, and an action that
   * declares it gets a symlink to a file that is not there. So a community path that reaches a package outside
   * [communityRoot] gives `null`.
   *
   * So the label has to name the community package that holds the file, and no module lives in that package for
   * `bazel-targets.json` to name. The tree is the authority Bazel itself uses - the first ancestor directory with a
   * `BUILD.bazel` is the package - and this asks the tree the same question.
   *
   * ### What happens when the export is missing
   *
   * The package must export the file. Such a package is hand-written, because the generator's file writing is scoped to
   * one repository half, and an entry it is missing fails the descriptor action at analysis with the label it could not
   * find. That is loud, and it is the reason this rule may compose a label it cannot verify.
   */
  fun containingPackageLabel(projectRelativePath: String): String? {
    val segments = projectRelativePath.split('/')
    val pathIsCommunity = communityRelativePath(projectRelativePath) != null
    for (depth in segments.size - 1 downTo 0) {
      val packageSegments = segments.subList(0, depth)
      val packageDirectory = packageSegments.fold(projectRoot) { directory, segment -> directory.resolve(segment) }
      if (!Files.exists(packageDirectory.resolve("BUILD.bazel"))) {
        continue
      }
      val insidePackage = segments.subList(depth, segments.size).joinToString("/")
      val packagePath = packageSegments.joinToString("/")
      val communityPackage = communityRelativePath(packagePath)
      if (communityPackage == null) {
        return if (pathIsCommunity) null else "//$packagePath:$insidePackage"
      }
      return "$COMMUNITY_REPOSITORY_PREFIX$communityPackage:$insidePackage"
    }
    return null
  }

  /**
   * Whether a package of the given half can name [label], by the repository part before `//`. An ultimate package can
   * name every repository.
   */
  fun canName(label: String, dependentIsCommunity: Boolean): Boolean {
    return !dependentIsCommunity || label.substringBefore("//") in COMMUNITY_NAMEABLE_REPOSITORIES
  }
}

/** The repository name of a community label, the part before `//`. */
private const val COMMUNITY_REPOSITORY_NAME: String = "@community"

/** The name suffix of a `content_module_jar` target. `content_module_jar_target_name` of `content_module_jar.bzl` is the Starlark half. */
private const val CONTENT_MODULE_JAR_TARGET_SUFFIX: String = "_content_module_jar"

/** The name suffix of a `dev_dist_plugin_descriptor` target. `dev_dist_plugin_descriptor.bzl` is the Starlark half. */
private const val DEV_DESCRIPTOR_TARGET_SUFFIX: String = "_dev_descriptor"

/**
 * The repositories a community package may name: its own, `@community`, and the community library container `@lib`.
 * A label from another repository vetoes a community descriptor target, and it moves the `jars` of a community section
 * to the product package.
 */
private val COMMUNITY_NAMEABLE_REPOSITORIES: Set<String> = setOf("", COMMUNITY_REPOSITORY_NAME, "@lib")

/**
 * The dependency label of each custom module of the converter, spelled the same for every dependent.
 *
 * The converter's `DEFAULT_CUSTOM_MODULES` places each one in `@community//build`. It returns this label before it
 * reads the half of the dependent. So a community dependent writes `@community//` here too.
 */
private val DEFAULT_CUSTOM_MODULE_LABELS: Map<String, String> = java.util.Map.of(
  "intellij.idea.community.build.zip", "@community//build:zip",
  "intellij.platform.jps.build.dependencyGraph", "@community//build:dependency-graph",
  "intellij.platform.jps.build.javac.rt", "@community//build:build-javac-rt",
)
