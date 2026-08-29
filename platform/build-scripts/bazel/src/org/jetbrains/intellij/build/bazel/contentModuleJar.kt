// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * The jar a content module contributes to a distribution, described well enough to pack it from Bazel labels.
 *
 * Order is load-bearing - the packer resolves a duplicate entry to the first source that offers it, so a reordered
 * source list is a different jar. Reproducing `JarPackager` means:
 *
 * 1. every library jar first, then every module output - `JarPackager` concatenates its asset-level source list, which
 *    is the libraries, with its per-module list;
 * 2. libraries in `.iml` `orderEntry` order, walked per module in the recipe's `modules:` order, with module libraries
 *    and project libraries interleaved exactly as the module declares them;
 * 3. module outputs in the recipe's `modules:` order, which is order-faithful - the content report writer does not sort
 *    that list.
 *
 * Which libraries those are comes from the JPS model and the recipe together; [mergedLibraryTargetLabels] states which
 * of the two decides, and it is not the same answer for a platform jar and a plugin jar. The recipe alone cannot order
 * them: it loses a project library's position among a module's libraries, because `projectLibraries:` is sorted by name
 * and hoisted to the jar level when the report is written, and it keys an unnamed `<orderEntry type="module-library">`
 * by jar file name. The model alone cannot say *whether* this module owns a jar or which modules share it - that is a
 * product-layout decision, and evaluating a product layout is the work this generator exists to keep out of a fragment
 * action.
 */
internal class ContentModuleJar(
  /**
   * The label of each merged library's own target - the `jvm_import`/`java_library`/`java_import` that groups its jars -
   * in merge order. Every one of them precedes every module output. Version-free by construction, and the rule expands
   * them to jars in this order.
   */
  @JvmField val libraryTargetLabels: List<String>,
  /** Modules merged before the owner's own output. The owner is often not the first - the order is the layout's. */
  @JvmField val modulesBefore: List<String>,
  /** Modules merged after the owner's own output. */
  @JvmField val modulesAfter: List<String>,
  /**
   * Whether the packed jar keeps its merged manifest with `Boot-Class-Path` rewritten to the jar's own name.
   *
   * `mergeJars.kt`'s `checkCoverageAgentManifest` does this for the coverage agent, unconditionally and ahead of the
   * rule that would otherwise drop the manifest: the agent instruments from any class loader, which needs the
   * attribute to name the jar the agent is in, and merging it into `lib/<module>.jar` renames it. It is a
   * product-layout decision, so it is decided here rather than in the packer.
   */
  @JvmField val rewriteBootClassPath: Boolean,
)

/**
 * The name of the target that packs [module]'s `lib/` jar, in the module's own package.
 *
 * Not written into the `BUILD.bazel`: the `content_module_jar` macro derives it from `module`, the way
 * `dev_dist_plugin_content` derives its own from `descriptor_module`. This is the Kotlin half of that one derivation -
 * `content_module_jar_target_name` in `content_module_jar.bzl` is the Starlark half - and it exists because
 * `build/bazel-targets.json` has to record the label the macro will produce, for the plan generator to name as a
 * plugin's prepacked content.
 */
internal fun contentModuleJarTargetName(module: ModuleDescriptor): String = "${module.targetName}_content_module_jar"

/**
 * The label of [module]'s packing target as [dependent]'s package must write it.
 *
 * Derived from the module's own dependency label rather than composed from scratch, so the repository prefix and the
 * community/ultimate rules stay in one place: that label is `<package>` or `<package>:<target>`, and the packing target
 * is a second target in the same package.
 */
internal fun contentModuleJarLabel(module: ModuleDescriptor, dependent: ModuleDescriptor, context: BazelBuildFileGenerator): String {
  val label = context.getBazelDependencyLabel(module, dependent)
  return label.substringBefore(':') + ":" + contentModuleJarTargetName(module)
}

/** [ContentModuleJar] with every merged-module name resolved to a label. */
internal class ContentModuleJarTarget(
  @JvmField val modulesBefore: List<String>,
  @JvmField val modulesAfter: List<String>,
  @JvmField val libraryTargetLabels: List<String>,
  @JvmField val rewriteBootClassPath: Boolean,
)

/**
 * Writes [module]'s packing target into its own `BUILD.bazel`.
 *
 * A target of its own, next to the `jvm_library` whose module it names, rather than attributes on that library. It was
 * attributes while the packer lived in the repository that consumes `rules_jvm`, because `jvm_library` is a `rules_jvm`
 * rule and could not name the tool - so the tool was pushed in through a `label_flag` and the recipe had to travel on
 * the only target that already existed. With the packer in `@community//build/content-module-packer` the rule names it
 * directly.
 *
 * What the target form buys, beyond letting the flag die: a recipe both compile backends see, where the attributes were
 * dropped on the `kt_jvm_library` path; and attributes `dev_dist_content.bzl` reads as its own rather than by name off
 * somebody else's rule. It costs almost nothing in the generated tree, because everything derivable is derived - see
 * the emitter.
 */
internal fun BuildFile.emitContentModuleJar(module: ModuleDescriptor, jar: ContentModuleJarTarget) {
  load((if (module.isCommunity) "" else "@community") + "//platform/build-scripts/bazel-rules:content_module_jar.bzl", "content_module_jar")
  target("content_module_jar") {
    // Emitted in the order the Starlark formatter sorts them - alphabetical - so a regeneration needs no reformat.
    //
    // Four things this deliberately does not write, because 2 524 copies of a derivable fact is what the generated tree
    // pays for: `name`, which the macro derives from `module`; `module_name`, which the rule reads off `module`'s own
    // `KtJvmInfo`; `visibility`, which the macro defaults to public, as every one of these has to be; and `tags`, since
    // the macro adds `manual`. Most of these targets are therefore a single line.
    if (jar.libraryTargetLabels.isNotEmpty()) {
      option("libraries", jar.libraryTargetLabels.unsorted())
    }
    option("module", ":${module.targetName}")
    if (jar.modulesAfter.isNotEmpty()) {
      option("modules_after", jar.modulesAfter.unsorted())
    }
    if (jar.modulesBefore.isNotEmpty()) {
      option("modules_before", jar.modulesBefore.unsorted())
    }
    if (jar.rewriteBootClassPath) {
      option("rewrite_boot_class_path", true)
    }
  }
}

/** `mergeJars.kt` rewrites this module's `Boot-Class-Path`; see [ContentModuleJar.rewriteBootClassPath]. */
private const val BOOT_CLASS_PATH_MODULE = "intellij.platform.coverage.agent"

/**
 * Modules whose jar the distribution builder does more to than merge, so packing it from the recipe would produce a
 * different jar. Each of them stays with `JarPackager`; the comparison in `./build/dev-dist.cmd jars` is
 * what found them.
 *
 * - the five that pack a library named in `ProductProperties.presignedNativeLibs`: their native files are **taken out**
 *   of the jar, signed, and laid beside it as `lib/<libName>/`, and which ones survive depends on the target OS and
 *   architecture (`NativeFileHandler.isCompatibleWithTargetPlatform`). Both are product-layout decisions the packer
 *   deliberately knows nothing about.
 * - `intellij.platform.core`: the build patches `ApplicationNamesInfo.class` in memory and writes it into the jar ahead
 *   of every file source. There is no label for content that does not exist until the layout runs.
 */
private val EXCLUDED_CONTENT_MODULES = setOf(
  "intellij.libraries.jna",
  "intellij.libraries.pty4j",
  "intellij.libraries.skiko",
  "intellij.platform.sqlite",
  "intellij.profiler.asyncOne",
  "intellij.platform.core",
)

internal const val CONTENT_MODULE_RECIPE_FILE_NAME: String = "module-content.yaml"

/**
 * [ModuleDescriptor.contentModuleRecipeFile] as a label path in the module's own package, or `null` when it has none.
 *
 * The twin of [devDistResiduePackagePath], and the converter half of `_find_content_module_recipe_rel_path` in
 * `community/build/jps_model.bzl`: `JpsModuleToBazelTargetsOnly` asserts that the two sides pick out the same recipes,
 * so both have to drop a recipe outside the package the same way - the recipe sits beside the module's first content
 * root, which is not always inside it, and a file outside a package has no label.
 */
internal fun contentModuleRecipePackagePath(module: ModuleDescriptor): String? {
  val file = module.contentModuleRecipeFile ?: return null
  return file.relativeTo(module.bazelBuildFileDir).invariantSeparatorsPathString.takeIf { !it.startsWith("../") }
}
private const val PLATFORM_LIB_DIST_PREFIX = "dist.all/lib/"

/**
 * Project libraries the platform layout packs itself, so no content module may merge a second copy.
 *
 * `BaseLayout.includedProjectLibraries`, filled by the `withProjectLibrary` calls the layout makes. `JarPackager` reads
 * it through `PlatformLayout.hasLibrary`; there is no way to read it from here, because evaluating a product layout is
 * exactly the work this generator exists to keep out of a fragment action, so the names are listed instead. Each entry
 * names its source, and `jpsModelToBazel` reports a difference against the recipe, so a layout change that is not
 * mirrored here surfaces as a generator diff rather than as a jar that only fails at class-load time.
 */
private val LAYOUT_PACKED_PROJECT_LIBRARIES = setOf(
  // PlatformModules.kt: `withProjectLibraries(..., UTIL_8_JAR)` - JPS and `ArtifactRepositoryManager` need them in `util-8.jar`
  "Log4J",
  "kotlin-stdlib",
  "slf4j-api",
  "slf4j-jdk14",
  // PlatformModules.kt: its own jar, IJPL-248572
  "jetbrains.intellij.deps.java.atk.wrapper.linux",
  // PlatformModules.kt: their own jars, IDEA-179784 / IDEA-205600
  "javax.annotation-api",
  "javax.activation",
  "jaxb-runtime",
  "jaxb-api",
  // UltimateRepositoryModules.kt `customizePlatformLayoutForUltimate`: packed into `PRODUCT_BACKEND_JAR`
  "LicenseDecoder",
  "LicenseServerAPI",
  "yFiles",
  // LanguageServerProperties.kt: packed into the language server's `lib.jar`
  "jetbrains.intellij.deps.rocksdbjni",
)

/**
 * Module libraries the platform layout packs itself, by the module that declares them.
 *
 * `BaseLayout.includedModuleLibraries`, filled by `withModuleLibrary`. One entry today - `swingx` is put in its own jar
 * (IJPL-248591), so `intellij.libraries.swingx` ships a descriptor and nothing else.
 */
private val LAYOUT_PACKED_MODULE_LIBRARIES = mapOf(
  "intellij.libraries.swingx" to setOf("swingx"),
)

/** `org.jetbrains.intellij.build.productLayout.LIB_MODULE_PREFIX`. */
internal const val LIB_MODULE_PREFIX = "intellij.libraries."

/**
 * One jar of a content report - a checked-in `module-content.yaml` here, an entry of a distribution build's report zip
 * in [computePluginContent] - narrowed to what packing and membership need.
 *
 * A narrow schema rather than `com.intellij.platform.distributionContent.FileEntry`: that class lives in
 * the platform, and this generator is a standalone Bazel module that gets the platform as published Maven artifacts,
 * which do not include it. Hence also `strictMode = false` - the files carry fields (`reason`, `size`, `files`,
 * `productModules`, ...) this schema deliberately ignores, and enumerating them here would recreate `FileEntry` field by
 * field, which is the coupling the narrow schema exists to avoid.
 *
 * That leniency has a cost: a field this class *forgets to declare* is silently dropped rather than reported, and the
 * same reports are read by a second, independent narrow schema - `indexPluginContentReports`/`MutablePayload` in
 * `platform/buildScripts/src/productLayout/devDistPlanGenerator.kt`, which reads `FileEntry` directly and therefore sees
 * every field. When the two disagree about which entries name a module, the plan generator points a fragment at a content
 * target that does not contain what the plan counted - which is exactly how `module` went missing.
 *
 * So the agreement is enforced rather than asked for. `ContentReportSchemaTest` in
 * `community/platform/build-scripts/testFramework/tests` compares `FileEntry`'s serialization descriptor against the
 * field set this class declares plus an explicit, commented set of fields it deliberately ignores, and fails naming the
 * offending field - on an addition and on a rename alike. It cannot import this class (separate Bazel module, no target
 * to depend on), so it mirrors the field names; a field added or renamed here belongs in that mirror too, and the test
 * says so when it fails.
 */
@Serializable
internal data class RecipeEntry(
  val name: String = "",
  /**
   * The target-platform selectors of an entry the distribution writes for some operating systems only.
   *
   * Declared although no checked-in report carries any of them, because a report is an OS **superset**:
   * `collectPluginContentCategoryFailures` in `contentChecker.kt` unions a plugin's per-OS variants into the one file
   * that is checked in, precisely so a per-OS difference does not need a report per OS. Undeclared, `strictMode = false`
   * would read an OS-conditional entry as an unconditional one - and a handed-off jar is packed unconditionally, for
   * every product and every OS. So any non-null value vetoes the entry in [simplePluginContentEntry] instead of
   * being interpreted here: which files a target platform keeps is a product-layout decision, the same one
   * `EXCLUDED_CONTENT_MODULES` declines for `presignedNativeLibs`.
   */
  val os: String? = null,
  val arch: String? = null,
  val libc: String? = null,
  val modules: List<RecipeModule> = emptyList(),
  val contentModules: List<RecipeModule> = emptyList(),
  val projectLibraries: List<RecipeNamed> = emptyList(),
  val library: String? = null,
  /**
   * The module that owns [library] when this entry is a bare library jar taken out of a module's own jar - the agent
   * jars under `lib/rt/`, `lib/jshell-frontend.jar`, the native-library wrappers.
   *
   * Load-bearing twice over. The module is a member of the plugin like any other, so its own jar has to be declared;
   * and [library] is then that module's *module* library rather than a project library, which is the only thing that
   * makes it resolvable - `getLibraryByJpsIdentity` keys a module library by (name, owning module).
   */
  val module: String? = null,
)

@Serializable
internal data class RecipeModule(
  val name: String = "",
  val libraries: Map<String, List<RecipeNamed>> = emptyMap(),
)

/**
 * The module a `contentModules:` entry names, with the descriptor suffix of a `moduleName/descriptorName` key dropped.
 *
 * A content module can be shipped under a descriptor other than its own, and the report then names it
 * `moduleName/descriptorName` - the same key `moduleToSetChain` uses in the plan generator, whose `MutablePayload.add`
 * and `indexPluginContentReports` both strip it with `substringBeforeLast('/')` because a Bazel output is per module, not
 * per descriptor. Doing it here too is what keeps the two readers agreeing on which module an entry names: a raw
 * `intellij.foo/bar` finds no module descriptor, so the member would be dropped with a warning and its jar would go
 * missing from the fragment manifest - the `module:` failure again, by a different route.
 *
 * Three checked-in `module-content.yaml` files state a `contentModules:` entry, all three under
 * `community/platform/problemsView/`, and none of them holds a slash. So this rule earns its place on the report zip,
 * which is where a plugin's entries come from. Deliberately not applied to `modules:`, which the plan generator does not
 * strip either.
 */
internal val RecipeModule.moduleName: String
  get() = name.substringBeforeLast('/')

@Serializable
internal data class RecipeNamed(val name: String = "")

internal val recipeYaml: Yaml = Yaml(
  configuration = YamlConfiguration(
    strictMode = false,
    codePointLimit = 10 * 1024 * 1024,
  )
)

/**
 * The `content_module_jar` [module] owns, resolved into merge-ordered Bazel labels, or `null` when it owns none.
 *
 * Two kinds of jar, one target. A platform content module has a `module-content.yaml` beside it, which names the
 * modules its jar merges. A plugin content module has none, and its jar is the plain `lib/modules/<module>.jar` that
 * every plugin holding it derives - see [prepackedPluginContentModuleLibraries]. Both resolve their libraries with
 * [mergedLibraryTargetLabels], under the [MergeRules] of the layout that owns them, and both end at the same comparison
 * against the library set the deciding statement records.
 *
 * Returning `null` rather than failing is deliberate: a jar this generator cannot reproduce faithfully - one several
 * modules co-own, one whose merged module the converter does not know, one holding a library it cannot label - must keep
 * being packed by `JarPackager`. Emitting a target for it would produce a jar that differs from the distribution's, and
 * nothing would notice until class-load time.
 */
internal fun computeContentModuleJar(module: ModuleDescriptor, moduleList: ModuleList, context: BazelBuildFileGenerator): ContentModuleJar? {
  val moduleName = module.module.name
  if (moduleName in EXCLUDED_CONTENT_MODULES) {
    return null
  }

  val entry = module.contentModuleRecipe
  if (entry == null) {
    val recordedNames = prepackedPluginContentModuleLibraries(module = module, moduleList = moduleList, context = context)
                        ?: return null
    val libraryTargetLabels = mergedLibraryTargetLabels(
      dependent = module,
      packedModuleNames = listOf(moduleName),
      rules = MergeRules.PLUGIN,
      recordedNames = recordedNames,
      moduleList = moduleList,
      context = context,
    ) ?: return null
    return ContentModuleJar(
      libraryTargetLabels = libraryTargetLabels,
      modulesBefore = emptyList(),
      modulesAfter = emptyList(),
      rewriteBootClassPath = false,
    )
  }
  // `contentModules:` means a plugin content-module jar under `lib/modules/`, which `ij_plugin` owns.
  if (entry.contentModules.isNotEmpty() || entry.modules.isEmpty()) {
    return null
  }

  val jarName = "$moduleName.jar"
  // `<file>` is the placeholder the content report writes for modules that some product packs under a product-wide jar
  // name; for this product it is the self-named jar. Any other name means the jar is a slice of a jar co-owned by
  // several modules - `product.jar`, `intellij.platform.cs.jar` - and one target per module would produce one
  // incomplete jar per module.
  if (entry.name != "<file>" && entry.name != PLATFORM_LIB_DIST_PREFIX + jarName) {
    return null
  }

  val moduleNames = entry.modules.map { it.name }
  // The owner's own jar is merged in place, so the rest are split around it. A jar whose recipe does not list its owner
  // is not this module's to pack.
  val ownerIndex = moduleNames.indexOf(moduleName)
  if (ownerIndex < 0) {
    return null
  }

  // The recipe is not the source of the library set on this path, but it is still the only record of what the
  // distribution ships, so it gets a veto - see [mergedLibraryTargetLabels].
  val recordedNames = HashSet<String>()
  entry.modules.flatMapTo(recordedNames) { it.libraries.keys }
  entry.projectLibraries.mapTo(recordedNames) { it.name }
  entry.library?.let { recordedNames.add(it) }

  val libraryTargetLabels = mergedLibraryTargetLabels(
    dependent = module,
    packedModuleNames = moduleNames,
    rules = MergeRules.PLATFORM,
    recordedNames = recordedNames,
    moduleList = moduleList,
    context = context,
  ) ?: return null

  return ContentModuleJar(
    libraryTargetLabels = libraryTargetLabels,
    modulesBefore = moduleNames.subList(0, ownerIndex),
    modulesAfter = moduleNames.subList(ownerIndex + 1, moduleNames.size),
    rewriteBootClassPath = module.module.name == BOOT_CLASS_PATH_MODULE,
  )
}

/**
 * Whether [module] may use its `content_module_jar` output for a plugin-content relation.
 *
 * The second half is the whole point of asking [computeContentModuleJar] rather than repeating its checks: the emitter
 * writes a packing target where that function returns one, and a `prepacked_content_modules` relation names that
 * target. A relation naming a target nobody wrote does not build, so both answers come from one function.
 *
 * One case the pair does not cover, and it is older than this function. `generateModuleBuildFiles` drops the packing
 * target of a module whose `build` section a person took over, while the `dev` section is written whatever
 * happens - so a hand-written module that is also a plugin candidate would leave a label with no target behind it.
 * `intellij.php.dev` is the one module with a hand-written section, and it is not a candidate. A dangling label fails
 * at analysis rather than silently, so the build says so at once. Closing this needs the skipped sections read before
 * the decision runs.
 */
internal fun isPrepackedPluginContentModule(module: ModuleDescriptor, moduleList: ModuleList, context: BazelBuildFileGenerator): Boolean {
  return prepackedPluginContentModuleLibraries(module = module, moduleList = moduleList, context = context) != null &&
         computeContentModuleJar(module = module, moduleList = moduleList, context = context) != null
}

/**
 * The libraries the derived candidacy records for [module]'s jar, or `null` when [module] hands no jar to a plugin.
 *
 * [ModuleList.pluginContentModuleJarCandidates] proves the plugin jar is a single-module self-named jar and says which
 * libraries it merges. The checks here cover facts local to the module target: a platform recipe must not ask the same
 * output group to produce different bytes, and the descriptor used by `computeModuleSourcesByContent` must remain
 * readable after the raw module jar stops being a fragment input.
 *
 * The candidacy only. Whether the merge is *derivable* from the JPS model is [computeContentModuleJar]'s answer, and
 * [isPrepackedPluginContentModule] is the two together.
 */
private fun prepackedPluginContentModuleLibraries(
  module: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): Set<String>? {
  val moduleName = module.module.name
  val recordedNames = moduleList.pluginContentModuleJarCandidates.get(moduleName) ?: return null
  if (moduleName in context.pluginContentModuleJarVetoes ||
      moduleName in EXCLUDED_CONTENT_MODULES ||
      moduleName == BOOT_CLASS_PATH_MODULE) {
    return null
  }

  module.contentModuleRecipe?.let { platformRecipe ->
    if (!isCompatibleSingleModuleRecipe(entry = platformRecipe, moduleName = moduleName, recordedNames = recordedNames)) {
      return null
    }
  }

  // A resource root and nothing else: `_find_descriptor_rel_paths` in `@community//build:jps_model.bzl` derives a
  // module's descriptors from its `java-resource` roots only, so a `<moduleName>.xml` that lives in a source root is
  // absent from the project-model tree the assembly reads. Accepting it here takes the raw module jar out of the
  // fragment while leaving `DescriptorSearchPass.MODULE_OUTPUT` as the only reader that could still find the
  // descriptor, and that reads a label nobody declared - "Bazel input '<label>' is not declared in the explicit input
  // manifest", which names the jar and not the descriptor that made it unreachable.
  val hasReadableDescriptor = module.module.sourceRoots.any { root ->
    root.rootType == JavaResourceRootType.RESOURCE && context.javaExtensionService.findSourceFile(root, "$moduleName.xml") != null
  }
  return if (hasReadableDescriptor) recordedNames else null
}

/**
 * Whether a platform recipe describes the same jar the plugin candidacy describes, [recordedNames] included.
 *
 * One module, one jar, one target. When both a `module-content.yaml` and a plugin's candidacy cover this module, they
 * have to ask for the same bytes, or the one target would satisfy one of them and break the other.
 */
private fun isCompatibleSingleModuleRecipe(entry: RecipeEntry, moduleName: String, recordedNames: Set<String>): Boolean {
  val single = entry.modules.singleOrNull() ?: return false
  return entry.contentModules.isEmpty() &&
         single.name == moduleName &&
         single.libraries.keys == recordedNames &&
         entry.projectLibraries.isEmpty() &&
         entry.library == null &&
         entry.module == null &&
         (entry.name == "<file>" || entry.name == "$PLATFORM_LIB_DIST_PREFIX$moduleName.jar")
}

/** Whose layout decides which of a walked module's libraries its content-module jar merges. */
private enum class MergeRules {
  /**
   * The platform layout, which this generator mirrors.
   *
   * [isMergedIntoContentModuleJar] reproduces `JarPackager.computeSourcesForModuleLibs` for it, down to the two
   * hand-kept lists `LAYOUT_PACKED_PROJECT_LIBRARIES` and `LAYOUT_PACKED_MODULE_LIBRARIES`. So the model decides and
   * the module's own `module-content.yaml` vetoes, and a veto in either direction is a rule the mirror is missing.
   */
  PLATFORM,

  /**
   * A plugin layout, which this generator does not evaluate - so here the folded candidacy decides the set and the
   * walked module orders it.
   *
   * `excludedModuleLibraries`, `doNotCopyModuleLibrariesAutomatically` and `auto` are all `PluginLayout` state.
   * Evaluating a product layout is the work this generator exists to keep out of a fragment action, and mirroring one
   * of those fields would mean mirroring every plugin layout in the repository. The first attempt over-derived. The 13
   * `doNotCopyModuleLibrariesAutomatically` modules of the database and Rider layouts each declare a module library
   * their jar does not hold, so a model-decides rule refused all 13.
   *
   * The walked module is still needed for everything the candidacy loses: the merge order, which is the module's
   * `orderEntry` order, and the Bazel target of each library. The candidacy is still checked. Every library it names
   * must be one this module declares in production scope, and the comparison below is the same expression on both paths.
   */
  PLUGIN,
}

/**
 * The target that groups the jars of each library [packedModuleNames] merges, in merge order, or `null` when this jar
 * must stay with `JarPackager`.
 *
 * `null` for three reasons, and each is stated where it is decided: a merged module the converter does not know, a
 * merged library with no target it can name, or a set that disagrees with [recordedNames]. A disagreement is warned
 * about, because a jar that differs from the distribution's surfaces at class-load time and nowhere earlier.
 */
private fun mergedLibraryTargetLabels(
  dependent: ModuleDescriptor,
  packedModuleNames: List<String>,
  rules: MergeRules,
  recordedNames: Set<String>,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): List<String>? {
  // A set, because two libraries of one module can intern to the same Bazel target, and Bazel rejects a repeated label
  // in an attribute outright. The first-wins jar order the packer needs is preserved by the rule, which expands these
  // targets in this order and drops a jar it has already seen.
  val targetLabels = LinkedHashSet<String>()
  val claimed = HashSet<Pair<String, String?>>()
  val names = HashSet<String>()
  for (packedModuleName in packedModuleNames) {
    val packedModule = moduleList.getModuleDescriptorOrNull(packedModuleName) ?: return null
    for (element in packedModule.module.dependenciesList.dependencies) {
      if (element !is JpsLibraryDependency) {
        continue
      }

      // `JarPackagerDependencyHelper.getLibraryDependencies` -> `isProductionRuntimeDependency`: COMPILE and RUNTIME
      // reach the production runtime, PROVIDED and TEST do not. `intellij.libraries.coil` is the visible case - it packs
      // three of its four module libraries because the fourth is TEST.
      val scope = context.javaExtensionService.getDependencyExtension(element)?.scope ?: continue
      if (scope != JpsJavaDependencyScope.COMPILE && scope != JpsJavaDependencyScope.RUNTIME) {
        continue
      }

      val parentReference = element.libraryReference.parentReference
      if (parentReference.resolve() is JpsGlobal) {
        continue
      }

      val jpsLibrary = element.library ?: continue
      val owner = (parentReference as? JpsModuleReference)?.moduleName
      // The plugin path has to ask this about every library the module declares, because the recorded set is what
      // selects. An unnamed library this generator cannot name reads as not merged, which the comparison below then
      // reports if the recipe names it.
      val reportName = distributionLibraryName(jpsLibrary)
      val isMerged = when (rules) {
        MergeRules.PLATFORM -> isMergedIntoContentModuleJar(
          jpsLibraryName = jpsLibrary.name,
          ownerModuleName = owner,
          packedModuleName = packedModuleName,
          packedModule = packedModule,
          moduleList = moduleList,
          context = context,
        )
        // Two unnamed libraries of one module whose jars share a file name are one recorded name and two labels here,
        // because the recorded set has one key for both. Neither that set nor this walk can separate them.
        MergeRules.PLUGIN -> reportName != null && reportName in recordedNames
      }
      if (!isMerged || !claimed.add(jpsLibrary.name to owner)) {
        continue
      }
      if (reportName == null) {
        // Reached on the platform path only: the merge rules said yes and the comparison below has nothing to compare.
        context.reportContentModuleJarRefusal(dependent.module.name, "an unnamed merged library has no single jar to name it by")
        return null
      }
      names.add(reportName)

      // Collected while the module's dependencies were converted, so an unknown identity means a library this walk can
      // see but the converter did not - which would make the label unresolvable. Bail out rather than emit it.
      val library = context.getLibraryByJpsIdentity(jpsName = jpsLibrary.name, moduleLibraryModuleName = owner) ?: return null
      targetLabels.add(
        libraryTargetLabel(
          library = library,
          communityRoot = context.communityRoot,
          ultimateRoot = context.ultimateRoot,
          isCommunityDependent = dependent.isCommunity,
        )
      )
    }
  }

  if (names != recordedNames) {
    // Only the halves that have a name. On the plugin path the merged set is a subset of the recorded one by
    // construction, so `only merged` is always empty there and printing it would be dead text.
    val onlyRecorded = (recordedNames - names).sorted()
    val onlyMerged = (names - recordedNames).sorted()
    context.reportContentModuleJarRefusal(
      dependent.module.name,
      "the merged library set does not match its recipe" +
      (if (onlyRecorded.isEmpty()) "" else " (only in the recipe: $onlyRecorded)") +
      (if (onlyMerged.isEmpty()) "" else " (only merged: $onlyMerged)"),
    )
    return null
  }
  return targetLabels.toList()
}

/**
 * The repo-global candidate set: every module whose every `contentModules:` occurrence agrees that the jar is a plain,
 * product-independent, self-named jar **and on the libraries merged into it**, with [overrides] applied last. The value
 * is that agreed library set.
 *
 * Not on the destination. [simplePluginContentEntryPath] accepts two of them. Which one a plugin uses is that plugin's
 * layout decision rather than a fact about the jar, so the fold agrees on the bytes and the relation carries the
 * destination.
 *
 * One exception, and it costs this lift 24 modules. An occurrence at `lib/<module>.jar` that merges a module library is
 * not simple at all, so it vetoes the module for every plugin. 24 of the 114 modules with such an occurrence gain
 * nothing here, although another plugin places them conventionally. Making that refusal per occurrence is a slice of its
 * own, because the fold is what keeps one packing target serving every plugin.
 *
 * Folded over the plugins of a distribution build's report zip, which the residue writer reads and no other run does.
 * [foldDerivedPluginContentCandidacy] is what generation folds, over the project model instead. An occurrence in a main
 * plugin jar (`modules:`) is irrelevant: `content_module_jar` is an extra output and does not change that jar.
 *
 * The library set has to travel with the answer, because one target serves every plugin that ships the module. Two
 * plugins recording different libraries for one module describe two different jars, and neither is the one a single
 * target could pack - so a disagreement is a veto, and it says both sets.
 *
 * An entry with no `contentModules:` but a `module:` is a bare library jar taken out of that module's own jar
 * (`lib/debugger-memory-agent.jar`); a prepacked module skips `computeSourcesForModule` and would silently never write
 * it, so the owner is vetoed on sight.
 *
 * **That veto stays, and a 2026-08-27 measurement says why.** It refuses 47 owning modules, of which 9 would otherwise
 * be candidates. `validatePrepackedPluginContentHandoff` refuses all 9 a second time, and it throws where this fold
 * skips. So dropping the veto here turns a jar that quietly goes missing into a build that fails.
 *
 * **A report records that a library left the module's jar. It does not record which mechanism took it out, and the
 * mechanism decides the answer.** The two mechanisms sit on opposite sides of the call a hand-off skips.
 *
 * - `isSeparateLibraryJar` inside `computeSourcesForModuleLibs` writes 6 of the 9 siblings.
 *   `computeSourcesForModule` is the only caller, and a handed-off module skips it. So those siblings genuinely go missing, and the veto corrects.
 * - A `withModuleLibrary` call in a `PluginLayout` writes the other 3. `JarPackager.pack` calls
 *   `computeModuleCustomLibrarySources` on its own line, outside the skipped call, so those siblings ship whatever this
 *   generator does. The veto is conservative for all 3, and their own jars weigh 1 664 567 bytes together.
 *
 * This generator exists to keep the evaluation of a product layout out of a fragment action. So it cannot read which
 * mechanism took a library out, and it keeps both cases.
 *
 * A per-occurrence *owner* refusal costs nothing measurable either. It is a different refusal from the per-occurrence
 * *entry* refusal this KDoc weighs at 24 modules, above and below.
 *
 * The veto is repo-global, and 37 of the 47 owners have no
 * self-named jar in any report, so no rule would make them candidates. 10 owners do have one. For every one of the 10
 * the *same* report both gives the module its own jar and writes the sibling. One of the 10 is
 * `intellij.java.debugger.impl`, and another occurrence vetoes it anyway. So a narrower rule would gain no module.
 *
 * The bytes agree, and `dev-dist-measurements.md` holds the per-owner table. The 9 own 68 277 444 bytes of self-named
 * jar on one composite tree, and one module holds 97.1 % of that.
 *
 * When [simplePluginContentEntry] refuses an entry, this fold vetoes every content module the entry names. It does not
 * veto only the module the path names. That over-approximates, and a measurement puts the cost at zero. The 50 entries
 * that hold several content modules name 416 distinct modules. Not one of the 416 has a second occurrence that
 * describes its own self-named jar. So reading such an entry as silent would agree on no extra module.
 *
 * The fold is tri-state - unseen, agreed on a library set, or vetoed - which one map cannot hold, so `vetoed` is a
 * collection of its own. A vetoed module is also removed from `agreed`, and every veto path does both. That is what
 * keeps the fold an AND: once a module is vetoed no later occurrence brings it back, whatever order the reports are
 * read in.
 *
 * [overrides] is what a community-only run cannot fold for itself; see [PLUGIN_CONTENT_CANDIDATE_OVERRIDES_FILE_NAME].
 * It is applied after the fold, so it decides both directions. Every caller passes an empty map today, because the
 * residue writer folds this over one build's reports and states rows from the answer alone.
 *
 * A rule changed here belongs in [foldDerivedPluginContentCandidacy] too. The two folds have to reach one verdict for
 * every module both can see, and the residue rows this one drives are what makes the derived fold agree.
 */
internal fun foldPluginContentCandidacy(reports: List<List<RecipeEntry>>, overrides: Map<String, Set<String>?>): Map<String, Set<String>> {
  val agreed = HashMap<String, Set<String>>()
  val vetoed = HashSet<String>()
  for (report in reports) {
    for (entry in report) {
      if (entry.contentModules.isEmpty()) {
        entry.module?.let {
          agreed.remove(it)
          vetoed.add(it)
        }
        continue
      }
      val simple = simplePluginContentEntry(entry)
      for (contentModule in entry.contentModules) {
        val name = contentModule.moduleName
        if (name in vetoed) {
          continue
        }
        if (simple == null || simple.moduleName != name) {
          agreed.remove(name)
          vetoed.add(name)
          continue
        }
        val recorded = agreed.putIfAbsent(name, simple.libraries)
        if (recorded != null && recorded != simple.libraries) {
          println(
            "WARN: $name keeps being packed by JarPackager: its plugin content reports disagree about the libraries" +
            " merged into its jar (${recorded.sorted()} against ${simple.libraries.sorted()})"
          )
          agreed.remove(name)
          vetoed.add(name)
        }
      }
    }
  }

  for ((name, libraries) in overrides) {
    if (libraries == null) {
      agreed.remove(name)
    }
    else {
      agreed.put(name, libraries)
    }
  }
  return agreed
}

/**
 * The file the answers this generator cannot fold for itself are recorded in.
 *
 * The candidacy fold is an AND over every plugin of the project, and a community checkout does not contain the ultimate
 * ones. So a community-only run folds a different answer for a community module the ultimate half has an opinion about,
 * in both directions - a module only an ultimate plugin offers is not a candidate at all, and a module an *ultimate*
 * plugin vetoes is not vetoed. Either way that run generates `content_module_jar` and
 * `prepacked_content_modules` attributes differing from the checked-in ones, which is what
 * `Assert Bazel Files Are In Sync With JPS Model (Community Only)` fails on.
 *
 * Only those modules are recorded, not the whole set. The converter folds both halves and records the global answer for
 * the community modules they disagree about, in `bazel-targets.json`; `plugin-model-tool` only writes those rows out.
 * [communityOnlyCandidacyOverrideRows] is the one producer, and it states 8 rows today, where the whole set was 1892.
 * The sign is that answer, so `+` and `-` both occur. An override always agrees with what an ultimate run folds for
 * itself, by construction, so no run needs to know which kind of checkout it is in.
 *
 * That is why a `+` line also carries the merged library names, space separated after the module name. The fold agrees
 * on a library *set* and not only on a boolean, and the set of a module whose only report is in ultimate is another
 * thing a community-only run cannot see. Without it that run would emit a `libraries` attribute the ultimate run
 * refuses, or refuse one the ultimate run emits. A `-` line records no library, because a vetoed module has no jar.
 *
 * A plain text file for the same reason as the sibling `dev_dist_plugin_content_vetoes.txt`: it keeps this reader
 * independent of Starlark. Staleness is caught where the other plan files' is: the blocking `model-generation`
 * validation of `AllProductsPackagingTest` regenerates and diffs it.
 */
internal const val PLUGIN_CONTENT_CANDIDATE_OVERRIDES_FILE_NAME: String = "dev_dist_plugin_content_candidate_overrides.txt"

/**
 * Reads what `plugin-model-tool` recorded, or nothing when no run has recorded it. A `null` value is "not a candidate".
 *
 * Nothing rather than a failure, because a project the tool has never run over is a real case and not a mistake: the
 * generator's own integration tests each build a throwaway community project. Such a project has one plugin and no
 * ultimate half, so the fold reaches the same verdict with or without this file. What an absent file costs a real
 * checkout is only the modules it would have corrected, which the sync assertion then reports.
 *
 * A line without a sign is a hard error, unlike a missing file: it would silently change how a module is packed, and a
 * jar that differs from the distribution's is not noticed until class-load time. A `-` line with a library is the same
 * class of mistake read from the other side.
 */
internal fun readPluginContentCandidateOverrides(file: Path): Map<String, Set<String>?> {
  if (!Files.exists(file)) {
    return emptyMap()
  }

  val result = HashMap<String, Set<String>?>()
  for (line in Files.readAllLines(file)) {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith('#')) {
      continue
    }

    val isCandidate = when (trimmed.first()) {
      '+' -> true
      '-' -> false
      else -> error("$file: a line must start with `+` (a candidate) or `-` (not a candidate), got `$trimmed`")
    }
    val fields = trimmed.substring(1).split(' ').filter { it.isNotEmpty() }
    val moduleName = fields.firstOrNull() ?: error("$file: a line must name a module, got `$trimmed`")
    if (isCandidate) {
      result.put(moduleName, fields.drop(1).toSet())
    }
    else {
      // The same class of mistake as a line without a sign, and reported the same way: it would silently change how a
      // module is packed.
      if (fields.size != 1) {
        error("$file: a `-` line records no library, got `$trimmed`")
      }
      result.put(moduleName, null)
    }
  }
  return result
}

/**
 * What a first-tranche plugin entry hands over: the content module, where the plugin puts its jar, and the module
 * libraries merged into it.
 *
 * [libraries] is the entry's *record*, not a packing instruction. [computeContentModuleJar] derives the merge from the
 * JPS model and compares the result against this set, for the reason [ContentModuleJar] states: the record loses a
 * library's position among the module's order entries, and the identity of an unnamed `module-library`.
 *
 * Module libraries only. `ModuleEntry.libraries` is the module libraries the layout merged, and a project library is
 * recorded at the jar level as `projectLibraries:` - which still vetoes the entry here, because a plugin merges one
 * only for an `auto` `PluginLayout`, and a `PluginLayout` is exactly what this generator does not evaluate.
 */
internal class SimplePluginContentEntry(
  @JvmField val moduleName: String,
  /**
   * Where this plugin puts the jar, relative to the plugin's `lib/`.
   *
   * A property of the *(plugin, module)* relation and not of the module: 14 candidate modules are placed under
   * `lib/modules/` by one plugin and directly in `lib/` by another, which is one packed jar and two destinations. So
   * [foldPluginContentCandidacy] agrees on [libraries] and never on this, and the relation carries it - see
   * `prepacked_jars` in `dev_dist_content.bzl`.
   */
  @JvmField val relativeOutputFile: String,
  @JvmField val libraries: Set<String>,
)

/**
 * The two shapes a plugin entry may have for its jar to be packable, by the path the entry records.
 *
 * `lib/modules/<module>.jar` is what `computeOutputJarPath` returns for a content module that needs a jar of its own,
 * and `lib/<module>.jar` is what `computeEmbeddedOutputJarPath` returns for an `embedded` one the layout does not pack
 * into the plugin jar. Both are self-named, which is what makes them a jar this generator can reproduce: any other name
 * is a jar whose contents the path does not describe.
 *
 * The second shape is accepted only for a jar that merges no module library. That restriction is a measurement rather
 * than a symmetry. A dev fragment patches the plugin descriptor. `embedContentModule` embeds an embedded module's own
 * descriptor into it. `resolveIncludes` then resolves every `xi:include` that descriptor holds against the fragment's
 * declared inputs. A handed-off jar is not one of those inputs. The project model tree carries a module's own
 * descriptors and nothing from its libraries, so an include whose target sits inside a merged library jar cannot be
 * resolved.
 *
 * The Kotlin plugin proved it. `intellij.libraries.kotlinc.analysis.api.k2` includes
 * `/META-INF/analysis-api/analysis-api-fir.xml`, which the Kotlin compiler FIR library jar holds. Handing off
 * `intellij.libraries.kotlinc.kotlin.compiler.fir` failed the assembly with "Cannot resolve".
 */
private fun simplePluginContentEntryPath(entryName: String, moduleName: String, mergesLibraries: Boolean): String? {
  return when (entryName) {
    "lib/modules/$moduleName.jar" -> "modules/$moduleName.jar"
    "lib/$moduleName.jar" -> if (mergesLibraries) null else "$moduleName.jar"
    else -> null
  }
}

/**
 * The hand-over of a first-tranche plugin entry, or `null` when the entry needs JarPackager.
 *
 * One content module, and that is measured rather than cautious. Every one of the 50 entries that hold several of them
 * is a plugin main jar. Each names one plugin main module in `modules:`, and none has a path that names any member.
 * [ContentModuleJar] could order such members with `modules_before` and `modules_after`. A main jar also holds a raw
 * module output and a patched `META-INF/plugin.xml` the layout builds in memory, so it stays with `JarPackager`. See
 * [foldPluginContentCandidacy] for what this refusal costs.
 */
internal fun simplePluginContentEntry(entry: RecipeEntry): SimplePluginContentEntry? {
  val contentModule = entry.contentModules.singleOrNull() ?: return null
  val moduleName = contentModule.moduleName
  val relativeOutputFile = simplePluginContentEntryPath(
    entryName = entry.name,
    moduleName = moduleName,
    mergesLibraries = contentModule.libraries.isNotEmpty(),
  ) ?: return null
  if (entry.modules.isNotEmpty() ||
      entry.projectLibraries.isNotEmpty() ||
      entry.library != null ||
      entry.module != null ||
      // An OS-conditional jar; see [RecipeEntry.os]. No report has one today, so this is a guard, not a filter.
      entry.os != null ||
      entry.arch != null ||
      entry.libc != null) {
    return null
  }
  return SimplePluginContentEntry(
    moduleName = moduleName,
    relativeOutputFile = relativeOutputFile,
    libraries = contentModule.libraries.keys,
  )
}

/** Whether [relativeOutputFile] is the path `dev_dist_content.bzl` derives, so a relation must not restate it. */
internal fun isConventionalPrepackedPath(moduleName: String, relativeOutputFile: String): Boolean {
  return relativeOutputFile == "modules/$moduleName.jar"
}

/**
 * Whether the **platform** layout merges the library [jpsLibraryName] declares into the content-module jar owned by
 * [packedModuleName], reproducing `JarPackager.computeSourcesForModuleLibs` for it. See [MergeRules.PLATFORM].
 *
 * Everything the platform path of that function tests is here. What is *not* here is deliberate:
 * `IMPLICIT_PLUGIN_PROJECT_LIBRARY_ALLOWLIST` and `LibraryPackMode` are reachable only for an auto `PluginLayout`, and
 * `excludedProjectLibraries`/`excludedModuleLibraries` are `PluginLayout` fields that are empty for the platform.
 *
 * The caller has already applied the scope filter and dropped application-level libraries.
 */
private fun isMergedIntoContentModuleJar(
  jpsLibraryName: String,
  ownerModuleName: String?,
  packedModuleName: String,
  packedModule: ModuleDescriptor,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): Boolean {
  if (ownerModuleName != null) {
    // A module library is private to the module that declares it, so it is merged unless the layout claimed it.
    return LAYOUT_PACKED_MODULE_LIBRARIES[ownerModuleName]?.contains(jpsLibraryName) != true
  }

  // `PlatformLayout.hasLibrary`: `super.hasLibrary` - the layout packs this project library itself.
  if (jpsLibraryName in LAYOUT_PACKED_PROJECT_LIBRARIES) {
    return false
  }

  // `PlatformLayout.hasLibrary`: the `libAsProductModule` half, skipped for a wrapper module itself.
  if (!packedModuleName.startsWith(LIB_MODULE_PREFIX) && context.getLibraryModuleExporting(jpsLibraryName) != null) {
    return false
  }

  return !hasLibraryInDependencyChainOfModuleDependencies(
    dependentModule = packedModule,
    jpsLibraryName = jpsLibraryName,
    moduleList = moduleList,
    context = context,
  )
}

/**
 * `JarPackagerDependencyHelper.hasLibraryInDependencyChainOfModuleDependencies`: a module in the same name group that
 * this module depends on already declares the library, so that module's jar carries it and this one must not.
 *
 * `intellij.rider.problemsView` is the case: its group is `intellij.rider`, it depends on module `intellij.rider`, and
 * `intellij.rider` declares `completion-ranking-csharp` and `wormhole`, which is why `intellij.rider.jar` holds them and
 * `intellij.rider.problemsView.jar` does not.
 *
 * The second branch - a same-group module that is *not* in the layout - is why the original needs
 * `layout.includedModules`, which the generator does not have. [ModuleList.contentModuleNames] stands in for it: a
 * module owns a content-module jar exactly when a recipe sits beside it, and a module that owns a jar is in the layout.
 * `intellij.platform.jps.build` is why the distinction matters - it declares `kotlin-metadata` and depends on
 * `intellij.platform.jps.build.dependencyGraph`, which declares it too, and both jars carry it because both modules are
 * in the layout.
 */
private fun hasLibraryInDependencyChainOfModuleDependencies(
  dependentModule: ModuleDescriptor,
  jpsLibraryName: String,
  moduleList: ModuleList,
  context: BazelBuildFileGenerator,
): Boolean {
  val dependentName = dependentModule.module.name
  val parentGroup = dependentName.substringBeforeLast('.', missingDelimiterValue = "")
  if (parentGroup.isEmpty()) {
    return false
  }

  val prefix = "$parentGroup."
  for (element in dependentModule.module.dependenciesList.dependencies) {
    if (element !is JpsModuleDependency) {
      continue
    }
    val scope = context.javaExtensionService.getDependencyExtension(element)?.scope ?: continue
    if (scope != JpsJavaDependencyScope.COMPILE && scope != JpsJavaDependencyScope.RUNTIME) {
      continue
    }

    val dependencyName = element.moduleReference.moduleName
    if (dependencyName != parentGroup &&
        !(dependencyName.startsWith(prefix) && !moduleList.contentModuleNames.contains(dependencyName))) {
      continue
    }

    val dependency = moduleList.getModuleDescriptorOrNull(dependencyName) ?: continue
    if (declaresLibrary(module = dependency, jpsLibraryName = jpsLibraryName, context = context)) {
      return true
    }
  }
  return false
}

/** As `JarPackagerDependencyHelper.getLibraryDependencies(...).any { ... }`: matched by name, module or project alike. */
private fun declaresLibrary(module: ModuleDescriptor, jpsLibraryName: String, context: BazelBuildFileGenerator): Boolean {
  return module.module.dependenciesList.dependencies.any { element ->
    element is JpsLibraryDependency &&
    element.libraryReference.libraryName == jpsLibraryName &&
    context.javaExtensionService.getDependencyExtension(element)?.scope
      ?.let { it == JpsJavaDependencyScope.COMPILE || it == JpsJavaDependencyScope.RUNTIME } == true
  }
}

/**
 * The name a content report records a library under: `getLibraryFileName` in the platform - the library's own name, or
 * the file name of its single jar for an unnamed module library. `null` when an unnamed library has no single jar.
 *
 * `intellij.relaxng` is why the unnamed case matters: its two `<orderEntry type="module-library">` entries have no name,
 * so the recipe keys them by jar file name. Deriving the merge from the JPS model needs no name at all, and this is only
 * used to compare the result against the recipe.
 *
 * `null` rather than a failure, because the plugin path asks this about every production-scope library of a candidate
 * module, merged or not, so an unnamed library with two jars anywhere in the project would stop the whole run. A jar
 * this generator cannot name is a jar it refuses to pack, which is the file's policy everywhere else.
 */
internal fun distributionLibraryName(library: JpsLibrary): String? {
  val name = library.name
  if (name.isNotEmpty() && !name.startsWith('#')) {
    return name
  }
  return library.getPaths(JpsOrderRootType.COMPILED).singleOrNull()?.fileName?.toString()
}

/** Parses [ModuleDescriptor.contentModuleRecipeFile]; reached only through [ModuleDescriptor.contentModuleRecipe]. */
internal fun parseContentModuleRecipe(file: Path?): RecipeEntry? {
  if (file == null) {
    return null
  }

  val text = file.readText()
  if (text.isBlank()) {
    return null
  }

  val entries = recipeYaml.decodeFromString(ListSerializer(RecipeEntry.serializer()), text)
  // One entry per file today. More than one would mean the module contributes to several jars, which needs more than one
  // target and a way to name them.
  return entries.singleOrNull()
}
