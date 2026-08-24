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
import kotlin.io.path.readText

/**
 * The jar a platform content module contributes to a distribution, described well enough to pack it from Bazel labels.
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
 * Which libraries those are is *derived* from the JPS model by [isMergedIntoContentModuleJar], not read from the recipe.
 * The recipe records the set but loses two things the merge needs: a project library's position among a module's
 * libraries, because `projectLibraries:` is sorted by name and hoisted to the jar level when the report is written, and
 * the identity of an unnamed `<orderEntry type="module-library">`, which it keys by jar file name. It is still what says
 * *whether* this module owns a jar and which modules share it - that is a product-layout decision, and evaluating a
 * product layout is the work this generator exists to keep out of a fragment action.
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
 * One jar of a checked-in content report - `module-content.yaml` here, `plugin-content.yaml` in [computePluginContent] -
 * narrowed to what packing and membership need.
 *
 * A narrow schema rather than `com.intellij.platform.distributionContent.testFramework.FileEntry`: that class lives in
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
   * every product and every OS. So any non-null value vetoes the entry in [simplePluginContentModuleName] instead of
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
 * A no-op on every one of the 1233 checked-in reports today; none has a `contentModules:` name with a slash in it.
 * Deliberately not applied to `modules:`, which the plan generator does not strip either.
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
 * Reads the recipe beside [module] and resolves it into merge-ordered Bazel labels, or returns `null` when this module
 * does not own a packable platform content-module jar.
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
    return if (isPrepackedPluginContentModule(module = module, context = context)) {
      ContentModuleJar(
        libraryTargetLabels = emptyList(),
        modulesBefore = emptyList(),
        modulesAfter = emptyList(),
        rewriteBootClassPath = false,
      )
    }
    else {
      null
    }
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

  // A set, because two libraries of one module can intern to the same Bazel target, and Bazel rejects a repeated label
  // in an attribute outright. The first-wins jar order the packer needs is preserved by the rule, which expands these
  // targets in this order and drops a jar it has already seen.
  val libraryTargetLabels = LinkedHashSet<String>()
  val claimed = HashSet<Pair<String, String?>>()
  val derivedNames = HashSet<String>()
  for (packedModuleName in moduleNames) {
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
      if (!isMergedIntoContentModuleJar(
          jpsLibraryName = jpsLibrary.name,
          ownerModuleName = owner,
          packedModuleName = packedModuleName,
          packedModule = packedModule,
          moduleList = moduleList,
          context = context,
        )) {
        continue
      }
      if (!claimed.add(jpsLibrary.name to owner)) {
        continue
      }
      derivedNames.add(distributionLibraryName(jpsLibrary))

      // Collected while the module's dependencies were converted, so an unknown identity means a library this walk can
      // see but the converter did not - which would make the label unresolvable. Bail out rather than emit it.
      val library = context.getLibraryByJpsIdentity(jpsName = jpsLibrary.name, moduleLibraryModuleName = owner) ?: return null
      libraryTargetLabels.add(
        libraryTargetLabel(
          library = library,
          communityRoot = context.communityRoot,
          ultimateRoot = context.ultimateRoot,
          isCommunityDependent = module.isCommunity,
        )
      )
    }
  }

  // The recipe is not the source of the library set any more, but it is still the only record of what the distribution
  // actually ships, so it gets a veto. A disagreement means the derivation is missing a rule this jar depends on, and a
  // jar that differs from the distribution's surfaces at class-load time and nowhere earlier - so leave it to
  // `JarPackager` and say so, loudly enough to be fixed.
  val recordedNames = HashSet<String>()
  entry.modules.flatMapTo(recordedNames) { it.libraries.keys }
  entry.projectLibraries.mapTo(recordedNames) { it.name }
  entry.library?.let { recordedNames.add(it) }
  if (recordedNames != derivedNames) {
    println(
      "WARN: $moduleName keeps being packed by JarPackager: the derived library set does not match its recipe" +
      " (only in the recipe: ${(recordedNames - derivedNames).sorted()}," +
      " only derived: ${(derivedNames - recordedNames).sorted()})"
    )
    return null
  }

  return ContentModuleJar(
    libraryTargetLabels = libraryTargetLabels.toList(),
    modulesBefore = moduleNames.subList(0, ownerIndex),
    modulesAfter = moduleNames.subList(ownerIndex + 1, moduleNames.size),
    rewriteBootClassPath = module.module.name == BOOT_CLASS_PATH_MODULE,
  )
}

/**
 * Whether [module] may use its `content_module_jar` output for a plugin-content relation.
 *
 * The report index proves the plugin jar is a single-module self-named jar. The checks here cover facts local to the
 * module target: a platform recipe must not ask the same output group to produce different bytes, and the descriptor
 * used by `computeModuleSourcesByContent` must remain readable after the raw module jar stops being a fragment input.
 */
internal fun isPrepackedPluginContentModule(module: ModuleDescriptor, context: BazelBuildFileGenerator): Boolean {
  val moduleName = module.module.name
  if (moduleName !in context.pluginContentModuleJarCandidates ||
      moduleName in context.pluginContentModuleJarVetoes ||
      moduleName in EXCLUDED_CONTENT_MODULES ||
      moduleName == BOOT_CLASS_PATH_MODULE) {
    return false
  }

  module.contentModuleRecipe?.let { platformRecipe ->
    if (!isCompatibleSingleModuleRecipe(entry = platformRecipe, moduleName = moduleName)) {
      return false
    }
  }

  // A resource root and nothing else: `_find_descriptor_rel_paths` in `@community//build:jps_model.bzl` derives a
  // module's descriptors from its `java-resource` roots only, so a `<moduleName>.xml` that lives in a source root is
  // absent from the project-model tree the assembly reads. Accepting it here takes the raw module jar out of the
  // fragment while leaving `DescriptorSearchPass.MODULE_OUTPUT` as the only reader that could still find the
  // descriptor, and that reads a label nobody declared - "Bazel input '<label>' is not declared in the explicit input
  // manifest", which names the jar and not the descriptor that made it unreachable.
  return module.module.sourceRoots.any { root ->
    root.rootType == JavaResourceRootType.RESOURCE && context.javaExtensionService.findSourceFile(root, "$moduleName.xml") != null
  }
}

private fun isCompatibleSingleModuleRecipe(entry: RecipeEntry, moduleName: String): Boolean {
  return entry.contentModules.isEmpty() &&
         entry.modules.size == 1 &&
         entry.modules.single().name == moduleName &&
         entry.modules.single().libraries.isEmpty() &&
         entry.projectLibraries.isEmpty() &&
         entry.library == null &&
         entry.module == null &&
         (entry.name == "<file>" || entry.name == "$PLATFORM_LIB_DIST_PREFIX$moduleName.jar")
}

/**
 * The file the repo-global candidate set is recorded in, so that every generator run reaches the same answer.
 *
 * The set is an AND over *every* checked-in `plugin-content.yaml`, which is a question this generator cannot answer
 * for itself: a community checkout does not contain the ultimate reports, so computing it here would produce a
 * different set in both directions - a module whose only report is in ultimate would not be a candidate at all, and a
 * module whose ultimate report disagrees would not be vetoed. Either way the community run would generate
 * `content_module_jar` and `prepacked_content_modules` attributes that differ from the checked-in ones, which is what
 * `Assert Bazel Files Are In Sync With JPS Model (Community Only)` fails on.
 *
 * `plugin-model-tool` records it (`renderPluginContentCandidates` in `devDistPlanGenerator.kt`), exactly as it records
 * the layout-exclusion vetoes in `dev_dist_plugin_content_vetoes.txt` beside it, out of the report index its own plan
 * generation already builds. A plain text file for the same reason as the vetoes: it keeps this reader independent of
 * Starlark. Staleness is caught where the other plan files' is: the blocking `model-generation` validation of
 * `AllProductsPackagingTest` regenerates and diffs it.
 */
internal const val PLUGIN_CONTENT_CANDIDATES_FILE_NAME: String = "dev_dist_plugin_content_candidates.txt"

/**
 * Reads what `plugin-model-tool` recorded, or nothing when no run has recorded it.
 *
 * An empty set rather than a failure, because a project the tool has never run over is a real case and not a mistake:
 * the generator's own integration tests each build a throwaway community project, and so would a checkout predating
 * this file. There is nothing to fall back to and nothing is lost by that - no candidates means every module stays on
 * the `JarPackager` path, which is what every run did before the feature existed. On a real checkout that would
 * generate BUILD files differing from the checked-in ones, so the sync assertion says so loudly. Note the direction:
 * missing candidates prepack less, never more, unlike the sibling vetoes reader whose absence would prepack a module a
 * layout transforms.
 */
internal fun readPluginContentModuleJarCandidates(file: Path): Set<String> {
  if (!Files.exists(file)) {
    return emptySet()
  }
  return Files.readAllLines(file).asSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.toSet()
}

/** The module of a first-tranche plugin entry, or `null` when the entry needs JarPackager. */
internal fun simplePluginContentModuleName(entry: RecipeEntry): String? {
  val contentModule = entry.contentModules.singleOrNull() ?: return null
  val moduleName = contentModule.moduleName
  if (entry.name != "lib/modules/$moduleName.jar" ||
      contentModule.libraries.isNotEmpty() ||
      entry.modules.isNotEmpty() ||
      entry.projectLibraries.isNotEmpty() ||
      entry.library != null ||
      entry.module != null ||
      // An OS-conditional jar; see [RecipeEntry.os]. No report has one today, so this is a guard, not a filter.
      entry.os != null ||
      entry.arch != null ||
      entry.libc != null) {
    return null
  }
  return moduleName
}

/**
 * Whether `JarPackager` merges the library [jpsLibraryName] declares into the content-module jar owned by
 * [packedModuleName], reproducing `JarPackager.computeSourcesForModuleLibs`.
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
 * the file name of its single jar for an unnamed module library.
 *
 * `intellij.relaxng` is why the unnamed case matters: its two `<orderEntry type="module-library">` entries have no name,
 * so the recipe keys them by jar file name. Deriving the merge from the JPS model needs no name at all, and this is only
 * used to compare the result against the recipe.
 */
private fun distributionLibraryName(library: JpsLibrary): String {
  val name = library.name
  if (name.isNotEmpty() && !name.startsWith('#')) {
    return name
  }
  return library.getPaths(JpsOrderRootType.COMPILED).single().fileName.toString()
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
