// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.nio.file.Path
import kotlin.io.path.isRegularFile
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

private const val CONTENT_MODULE_RECIPE_FILE_NAME = "module-content.yaml"
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
 * Entries of a `module-content.yaml`, narrowed to what packing needs.
 *
 * A narrow schema rather than `com.intellij.platform.distributionContent.testFramework.FileEntry`: that class lives in
 * the platform, and this generator is a standalone Bazel module that gets the platform as published Maven artifacts,
 * which do not include it. Hence also `strictMode = false` - the files carry fields (`reason`, `os`, `size`, ...) this
 * schema deliberately ignores.
 */
@Serializable
private data class RecipeEntry(
  val name: String = "",
  val modules: List<RecipeModule> = emptyList(),
  val contentModules: List<RecipeModule> = emptyList(),
  val projectLibraries: List<RecipeNamed> = emptyList(),
  val library: String? = null,
)

@Serializable
private data class RecipeModule(
  val name: String = "",
  val libraries: Map<String, List<RecipeNamed>> = emptyMap(),
)

@Serializable
private data class RecipeNamed(val name: String = "")

private val recipeYaml = Yaml(
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

  val entry = readRecipe(module) ?: return null
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
  // The rule packs the owner's own jar in place, so the merged modules are split around it. A jar whose recipe does not
  // list its owner is not this module's to pack.
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
 * Whether `JarPackager` merges the library [jpsLibraryName] declares into the content-module jar owned by
 * [packedModuleName], reproducing `JarPackager.computeSourcesForModuleLibs`.
 *
 * Everything the platform path of that function tests is here. What is *not* here is deliberate: the 88-entry
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

/** Whether [module] has a content-module jar recipe beside it, i.e. whether some product ships it as a content module. */
internal fun hasContentModuleRecipe(module: ModuleDescriptor): Boolean {
  val file = module.contentRoots.firstOrNull()?.resolve(CONTENT_MODULE_RECIPE_FILE_NAME) ?: return false
  return file.isRegularFile()
}

private fun readRecipe(module: ModuleDescriptor): RecipeEntry? {
  // The recipe sits in the module's first content root, which is not always the directory holding the `.iml`.
  val file: Path = module.contentRoots.firstOrNull()?.resolve(CONTENT_MODULE_RECIPE_FILE_NAME) ?: return null
  if (!file.isRegularFile()) {
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
