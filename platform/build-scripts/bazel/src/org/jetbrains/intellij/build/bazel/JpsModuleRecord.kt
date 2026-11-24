// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/** The file that declares the `jps_module` macro of a module record. */
internal const val JPS_MODULE_BZL: String = "@community//build:jps_module.bzl"

/**
 * Writes the record of [module], the content of the `iml <module>` section of its BUILD file. The record is a call of
 * the `jps_module` macro of [JPS_MODULE_BZL].
 *
 * The record states only the facts that the convention does not give: the name of the module target in the package,
 * the descriptors, the other files that the package exports for the module, a module without resources, the plugin
 * distribution and the jars of the local module libraries. The record exports the `.iml` file, the descriptors and
 * [exportedFiles], so the section holds no `exports_files`. A descriptor of [packageExportedFiles] is exported
 * elsewhere in the package, so the record names it in `hand_exports`. Returns the files that the package now exports
 * for the module. The
 * `jps_modules` repository rule names the targets of the record by the module name and the package of its `.iml` file.
 * So the package of a module that is not in [customModules] must be the directory of its `.iml` file.
 */
internal fun BuildFile.generateJpsModuleRecord(
  module: ModuleDescriptor,
  targets: BazelBuildFileGenerator.ModuleTargets,
  customModules: Set<String>,
  moduleLibraries: Collection<LocalLibrary>,
  exportedFiles: Set<String>,
  packageExportedFiles: Set<String>,
  communityRoot: Path,
  ultimateRoot: Path?,
): Set<String> {
  val name = module.module.name
  check(name in customModules || module.imlFile.parent == module.bazelBuildFileDir) {
    "The package ${module.bazelBuildFileDir} of module $name is not the directory of its .iml file ${module.imlFile}. " +
    "The jps_modules repository rule finds the record of a module in the directory of its .iml file."
  }

  val packagePrefix = bazelPackagePrefix(module = module, communityRoot = communityRoot, ultimateRoot = ultimateRoot)
  val target = (targets.productionTargets + targets.testTargets).singleOrNull()
               ?: error("Module $name must have one target, but it has ${targets.productionTargets + targets.testTargets}")
  val targetName = target.removePrefix("$packagePrefix:")
  check(targetName != target && ':' !in targetName) { "The target $target of module $name is not in its package $packagePrefix" }

  val hasResources = name !in customModules && (if (module.isTestModule) module.testResources else module.resources).isNotEmpty()
  val iml = imlTargetLabel(module = module, communityRoot = communityRoot, ultimateRoot = ultimateRoot).removePrefix("$packagePrefix:")
  val descriptors = conventionalDescriptorLabels(module = module, communityRoot = communityRoot, ultimateRoot = ultimateRoot)
    .map { it.removePrefix("$packagePrefix:") }
    .distinct()
  // The formatter sorts `exports` as labels.
  val exports = (exportedFiles - descriptors.toSet() - iml).sortedWith(BazelLabelComparator())
  // A file that the package already exports, by hand or by the record of another module, is exported once. A
  // hand-written export keeps its own visibility, see `exportedDescriptorFiles`.
  val handExports = descriptors.filter { it in packageExportedFiles }
  val pluginTarget = targets.pluginDistributionTarget?.target
  check(pluginTarget == null || pluginTarget == "$packagePrefix:${targetName}_plugin") {
    "The plugin distribution $pluginTarget of module $name is not ${targetName}_plugin in its package $packagePrefix"
  }
  // A label of the record is in the repository of the module, so both converters write the same community record.
  val projectRoot = if (module.isCommunity) communityRoot else ultimateRoot ?: error("Ultimate root is not available")
  val libraryJars = moduleLibraries
    .flatMap { libraryJarTargets(library = it, communityRoot = communityRoot, ultimateRoot = ultimateRoot, projectRoot = projectRoot) }
    // `<repository>//:library_jars` has the jars of the library repositories.
    .filterNot { it.startsWith("@lib//") || it.startsWith("@ultimate_lib//") }
    .distinct()
    .sorted()

  load(JPS_MODULE_BZL, "jps_module")
  // `name` first and the other attributes in alphabetical order, as the Starlark formatter leaves them, and `exports` last.
  target("jps_module") {
    option("name", name)
    if (descriptors.isNotEmpty()) {
      option("descriptors", descriptors)
    }
    if (handExports.isNotEmpty()) {
      option("hand_exports", handExports)
    }
    if (iml != "$name.iml") {
      option("iml", iml)
    }
    if (libraryJars.isNotEmpty()) {
      option("libraries", libraryJars)
    }
    if (pluginTarget != null) {
      option("plugin", true)
    }
    if (!hasResources) {
      option("resources", false)
    }
    option("target", targetName)
    // The formatter puts `exports` after the other attributes, as it puts `deps`.
    if (exports.isNotEmpty()) {
      option("exports", exports)
    }
  }
  return (listOf(iml) + descriptors + exports).toSet()
}

/** The build module that every product needs in addition to the modules of `dev-build.json`, see `getBuildModules` in `IdeBuilder.kt`. */
internal const val DEFAULT_BUILD_MODULE: String = "intellij.idea.community.build"

/**
 * Checks that the `jps_modules` call of [moduleFile] mirrors the converter: `packages` names the package of each module
 * of [customModules] in [modules], `imls` names the `.iml` label of each skipped module of [modules], and
 * `build_modules` is [buildModules]. The repository rule finds a record in the directory of the `.iml` file, so only
 * these modules need an attribute. A file that does not use the rule is not checked, so a test project needs no
 * `MODULE.bazel`. A file that uses the rule in a form that the check cannot read fails.
 */
internal fun checkJpsModulesCall(
  moduleFile: Path,
  projectRoot: Path,
  modules: Collection<ModuleDescriptor>,
  skippedModules: Set<String>,
  customModules: Set<String>,
  buildModules: List<String>,
  communityRoot: Path,
  ultimateRoot: Path?,
) {
  val text = if (moduleFile.isRegularFile()) moduleFile.readText() else return
  val call = JPS_MODULES_CALL.find(text)?.value
  if (call == null) {
    check(!text.contains("\"jps_modules\")")) {
      "$moduleFile uses the `jps_modules` repository rule, but the check finds no `jps_modules(` call at the start of a line"
    }
    return
  }

  val expectedPackages = modules.asSequence()
    .filter { it.module.name in customModules }
    .associate { it.module.name to it.bazelBuildFileDir.relativeTo(projectRoot).invariantSeparatorsPathString }
  val expectedImls = modules.asSequence()
    .filter { it.module.name in skippedModules }
    .associate { it.module.name to imlTargetLabel(module = it, communityRoot = communityRoot, ultimateRoot = ultimateRoot) }
  for ((attribute, expected) in listOf("packages" to expectedPackages, "imls" to expectedImls)) {
    val actual = jpsModulesDictionary(call, attribute)
    check(actual == expected) {
      "The `$attribute` attribute of `jps_modules` in $moduleFile must be $expected, but it is $actual. " +
      "It mirrors the custom and the skipped modules of the converter."
    }
  }
  val actualBuildModules = BUILD_MODULES_ATTRIBUTE.find(call)?.groupValues?.get(1)?.let { QUOTED.findAll(it).map { match -> match.groupValues[1] }.toList() }.orEmpty()
  check(actualBuildModules == buildModules) {
    "The `build_modules` attribute of `jps_modules` in $moduleFile must be $buildModules, but it is $actualBuildModules"
  }
}

private val BUILD_MODULES_ATTRIBUTE = Regex("\n    build_modules = \\[([^\\]]*)\\]")

private val QUOTED = Regex("\"([^\"]*)\"")

private val JPS_MODULES_CALL = Regex("^jps_modules\\(\n[\\s\\S]*?^\\)$", RegexOption.MULTILINE)

private val DICTIONARY_ENTRY = Regex("\"([^\"]+)\": \"([^\"]*)\",")

private fun jpsModulesDictionary(call: String, attribute: String): Map<String, String> {
  val start = call.indexOf("\n    $attribute = {")
  if (start == -1) {
    return emptyMap()
  }
  val end = call.indexOf("\n    },", start)
  check(end != -1) { "The `$attribute` attribute of the `jps_modules` call has no end: $call" }
  return DICTIONARY_ENTRY.findAll(call.substring(start, end)).associate { it.groupValues[1] to it.groupValues[2] }
}

/** The suffixes of the targets that the `jps_module` macro declares for a module, see [JPS_MODULE_BZL]. */
internal val JPS_MODULE_RECORD_TARGET_SUFFIXES: List<String> = listOf("_model_files", "_jars", "_test_jars", "_resource_jars", "_plugin_distribution")

private val TARGET_NAME = Regex("""\bname = "([^"]+)"""")

/**
 * Checks that no target of [content] has the name of a target that the record of one of [moduleNames] declares, such
 * as `<module>_jars`. Bazel would report the duplicate name only when it loads the package, and the message would not
 * name the record.
 */
internal fun checkJpsModuleRecordNames(buildFile: Path, content: String, moduleNames: Collection<String>) {
  val names = TARGET_NAME.findAll(content).mapTo(HashSet()) { it.groupValues[1] }
  val collisions = moduleNames.flatMap { module -> JPS_MODULE_RECORD_TARGET_SUFFIXES.map { module + it } }.filter { it in names }
  check(collisions.isEmpty()) {
    "$buildFile declares $collisions, but the `jps_module` record of the module declares a target of that name. Rename the target."
  }
}

/** Matches a hand-written record of [moduleName] in a section that the converter skips. */
internal fun handWrittenRecordPattern(moduleName: String): Regex = Regex("jps_module\\(\\s*name = \"${Regex.escape(moduleName)}\"")
