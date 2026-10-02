// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.DerivedPluginCandidacy
import com.intellij.platform.buildScripts.pluginModelTool.descriptorFiles
import com.intellij.platform.buildScripts.pluginModelTool.distributionLibraryName
import com.intellij.platform.buildScripts.pluginModelTool.foldDerivedPluginContentCandidacy
import com.intellij.platform.distributionContent.DevDistPlatformJars
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PRESIGNED_NATIVE_LIBS
import org.jetbrains.intellij.build.SignNativeFileMode
import org.jetbrains.intellij.build.devDist.CanonicalJarRecipe
import org.jetbrains.intellij.build.devDist.JarSourceRecipe
import org.jetbrains.intellij.build.devDist.JarWriterRecipe
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.util.TreeMap

/**
 * One module's `content_module_jar` candidacy from the source-derived plugin and platform rows.
 *
 * [libraries] is the library set the deciding rows record. [modulesBefore] and [modulesAfter] are the co-members of a
 * self-named platform jar, in row order around the module. [hasPlatformRow] says a product packs the module as a content
 * module under its own name, so the platform rows and the platform merge rules decide the jar. Otherwise the plugin
 * offer and the plugin rules decide.
 */
internal class ContentModuleJarCandidate(
  @JvmField val libraries: Set<String>,
  @JvmField val modulesBefore: List<String>,
  @JvmField val modulesAfter: List<String>,
  @JvmField val hasPlatformRow: Boolean,
)

/**
 * The candidate set of the whole repository; see [deriveContentModuleJarCandidacy].
 *
 * [pluginOffers] is every folded plugin offer outside the content vetoes, with its library set. [disagreements] names a
 * module whose self-named platform rows differ, with the reason. [platformModuleNames] is every module a platform jar
 * of any product names.
 */
internal class ContentModuleJarCandidacy(
  @JvmField val candidates: Map<String, ContentModuleJarCandidate>,
  @JvmField val pluginOffers: Map<String, Set<String>>,
  @JvmField val disagreements: Map<String, String>,
  @JvmField val platformModuleNames: Set<String>,
)

/** One platform jar row that packs a product content module: the product, the jar, and the libraries the jar merges. */
private class PlatformAppearance(
  @JvmField val product: String,
  @JvmField val relativeOutputFile: String,
  @JvmField val members: List<String>,
  @JvmField val libraries: Set<String>,
)

/**
 * Derives the candidate set from the platform jars of every product and the folded plugin offers.
 *
 * The plugin half reads the in-memory derivation and [contentVetoes]. The platform half reads the source-derived rows.
 * This is the only producer of the candidate set.
 *
 * A module is a candidate when it is not in [contentVetoes], and one of:
 * - a product lists it in `[platform_content_modules]` and packs it under its own name. The co-members become
 *   [ContentModuleJarCandidate.modulesBefore] and [ContentModuleJarCandidate.modulesAfter] in row order. Every product
 *   that packs the module as a content module must state the same jar, the same members and the same library set. Two
 *   products that differ make a disagreement and no candidate.
 * - no product packs it as a content module, and a plugin offer names it. The offer's library set is the candidate's.
 */
internal fun deriveContentModuleJarCandidacy(
  pluginCandidacies: Collection<DerivedPluginCandidacy>,
  contentVetoes: Collection<String>,
  platform: DevDistPlatformJars,
): ContentModuleJarCandidacy {
  val vetoes = contentVetoes.toHashSet()
  val pluginOffers = TreeMap<String, Set<String>>()
  for ((module, libraries) in foldDerivedPluginContentCandidacy(pluginCandidacies)) {
    if (module !in vetoes) {
      pluginOffers.put(module, libraries)
    }
  }

  val platformLibraries = HashMap<String, MutableSet<String>>()
  for (row in platform.platformLibraries) {
    val path = row.relativeOutputFile ?: continue
    platformLibraries.computeIfAbsent("${row.product}\t$path") { HashSet() }.add(row.library)
  }
  for (row in platform.platformMergedLibraries) {
    platformLibraries.computeIfAbsent("${row.product}\t${row.relativeOutputFile}") { HashSet() }.add(row.library)
  }
  val contentModules = platform.platformContentModules.mapTo(HashSet()) { "${it.product}\t${it.module}" }
  val platformModuleNames = HashSet<String>()
  val appearances = LinkedHashMap<String, MutableList<PlatformAppearance>>()
  for (row in platform.platformJars) {
    val libraries = platformLibraries.get("${row.product}\t${row.relativeOutputFile}") ?: emptySet()
    for (member in row.members) {
      platformModuleNames.add(member)
      if ("${row.product}\t$member" in contentModules) {
        appearances.computeIfAbsent(member) { ArrayList() }
          .add(PlatformAppearance(product = row.product, relativeOutputFile = row.relativeOutputFile, members = row.members, libraries = libraries))
      }
    }
  }

  val candidates = LinkedHashMap<String, ContentModuleJarCandidate>()
  val disagreements = LinkedHashMap<String, String>()
  module@ for ((module, rows) in appearances) {
    if (module in vetoes) {
      continue
    }
    val jarName = "$module.jar"
    val selfNamed = rows.filter { it.relativeOutputFile == jarName || (it.relativeOutputFile == "modules/$jarName" && it.members.size == 1) }
    if (selfNamed.isEmpty()) {
      continue
    }
    val first = selfNamed.first()
    for (row in rows) {
      val reason = when {
        row !in selfNamed -> "product ${row.product} packs it in `${row.relativeOutputFile}`"
        row.relativeOutputFile != first.relativeOutputFile || row.members != first.members -> "the jar or its members differ between products"
        row.libraries != first.libraries -> "the library set of its jar differs between products"
        else -> continue
      }
      disagreements.put(module, "$reason: ${describe(rows)}")
      continue@module
    }
    val ownerIndex = first.members.indexOf(module)
    candidates.put(module, ContentModuleJarCandidate(
      libraries = first.libraries,
      modulesBefore = first.members.subList(0, ownerIndex),
      modulesAfter = first.members.subList(ownerIndex + 1, first.members.size),
      hasPlatformRow = true,
    ))
  }
  for ((module, libraries) in pluginOffers) {
    if (module !in appearances) {
      candidates.put(module, ContentModuleJarCandidate(libraries = libraries, modulesBefore = emptyList(), modulesAfter = emptyList(), hasPlatformRow = false))
    }
  }
  return ContentModuleJarCandidacy(candidates = candidates, pluginOffers = pluginOffers, disagreements = disagreements, platformModuleNames = platformModuleNames)
}

private fun describe(rows: List<PlatformAppearance>): String {
  return rows.joinToString { "${it.product} `${it.relativeOutputFile}` [${it.members.joinToString()}] libraries [${it.libraries.sorted().joinToString()}]" }
}

/** Whose layout decides which of a walked module's libraries its content-module jar merges. */
internal enum class MergeRules {
  /** The platform layout: the JPS model decides, with the two layout-packed lists as the veto. */
  PLATFORM,

  /** A plugin layout: the recorded candidacy decides the set, and the walked module orders it. */
  PLUGIN,
}

/**
 * The `content_module_jar` one module owns, with every merged module and library resolved to a label.
 *
 * [libraryTargetLabels] is in merge order. [modulesBefore] and [modulesAfter] are dependency labels as the owner's
 * package writes them. [recipe] is the canonical recipe of the packed jar. A plugin reuses the jar when its own recipe
 * equals it.
 *
 * [nativeLib] is the Maven name of the one presigned native library the jar merges, and [nativeLibDir] is its folder
 * under `lib/`. Both are `null` for a jar without natives.
 */
internal class ContentModuleJarTarget(
  @JvmField val libraryTargetLabels: List<String>,
  @JvmField val modulesBefore: List<String>,
  @JvmField val modulesAfter: List<String>,
  sources: List<JarSourceRecipe>,
  @JvmField val nativeLib: String? = null,
  @JvmField val nativeLibDir: String? = null,
) {
  @JvmField val recipe: CanonicalJarRecipe = CanonicalJarRecipe(
    sources = sources,
    writer = JarWriterRecipe(
      mergeEntities = true,
      nativeLib = nativeLib ?: "",
    ),
  )
}

/**
 * Renders the `content_module_jar(...)` call of every candidate module. This is the only writer of the call.
 *
 * [computeJar] derives the jar over [mergedLibraryTargetLabels]. [renderFor] adds the two gates that are not a packing
 * decision: a module the JSON does not place gets no `build` section, and a hand-written `build` section drops the call.
 * A refusal is printed once per module.
 */
internal class ContentModuleJarStatements(
  @JvmField val candidacy: ContentModuleJarCandidacy,
  private val outputProvider: ModuleOutputProvider,
  @JvmField val index: DevDistBazelIndex,
  /** Whether a person took over the `build` section of the module. [DevDistBuildFiles.isBuildSectionSkipped] answers it. */
  private val isBuildSectionSkipped: (String) -> Boolean,
) {
  private val jars = HashMap<String, ContentModuleJarTarget?>()
  private val reportedRefusals = HashSet<String>()

  /** Every module [renderFor] answers for, sorted. Only a candidate can get a call, so the candidate keys are the population. */
  val modules: Set<String> by lazy {
    candidacy.candidates.keys.filterTo(java.util.TreeSet()) { renderFor(it) != null }
  }

  /**
   * The rendered call of [module], or `null` when the module gets none.
   *
   * The text is one `Target.render()`: one line for the bare `module` form, one attribute per line otherwise, and a
   * trailing newline in both cases.
   */
  fun renderFor(module: String): String? {
    val jar = computeJar(module) ?: return null
    if (index.location(module) == null || isBuildSectionSkipped(module)) {
      return null
    }
    return renderCall(module = module, jar = jar)
  }

  /** The `load(...)` line the call needs in the package of [module]. The section writer merges it into the file head. */
  fun loadStatement(module: String): LoadStatement {
    val isCommunity = index.isCommunity(module) ?: error("Module '$module' has no Bazel package, so it writes no load")
    return LoadStatement(bzlFile = (if (isCommunity) "" else "@community") + CONTENT_MODULE_JAR_BZL, symbols = listOf(CONTENT_MODULE_JAR_SYMBOL))
  }

  /**
   * The call text of [module] for [jar], in the converter's attribute order: `libraries`, `module`, `modules_after`,
   * `modules_before`, `native_lib`, `native_lib_dir`.
   */
  fun renderCall(module: String, jar: ContentModuleJarTarget): String {
    val targetName = index.targetName(module) ?: error("Module '$module' has no Bazel package, so it gets no call")
    val target = Target(CONTENT_MODULE_JAR_SYMBOL)
    if (jar.libraryTargetLabels.isNotEmpty()) {
      target.option("libraries", jar.libraryTargetLabels.unsorted())
    }
    target.option("module", ":$targetName")
    if (jar.modulesAfter.isNotEmpty()) {
      target.option("modules_after", jar.modulesAfter.unsorted())
    }
    if (jar.modulesBefore.isNotEmpty()) {
      target.option("modules_before", jar.modulesBefore.unsorted())
    }
    jar.nativeLib?.let { target.option("native_lib", it) }
    jar.nativeLibDir?.let { target.option("native_lib_dir", it) }
    return target.render()
  }

  /**
   * The jar [module] owns, resolved into merge-ordered labels, or `null` when it owns none.
   *
   * `null` rather than a failure, as in the converter: a jar this cannot reproduce faithfully must keep being packed by
   * `JarPackager`. The hand-written section gate is not applied here; see [renderFor].
   */
  fun computeJar(module: String): ContentModuleJarTarget? {
    return jars.getOrPut(module) { doComputeJar(module) }
  }

  private fun doComputeJar(moduleName: String): ContentModuleJarTarget? {
    val candidate = candidacy.candidates.get(moduleName)
    if (candidate == null) {
      candidacy.disagreements.get(moduleName)?.let { reportRefusal(moduleName, it) }
      return null
    }
    val module = outputProvider.findModule(moduleName) ?: return null
    val rules: MergeRules
    if (candidate.hasPlatformRow) {
      rules = MergeRules.PLATFORM
    }
    else {
      if (!hasReadableDescriptor(module)) {
        return null
      }
      rules = MergeRules.PLUGIN
    }

    val dependentIsCommunity = index.isCommunity(moduleName) ?: return null
    val packedModuleNames = candidate.modulesBefore + moduleName + candidate.modulesAfter
    val isMerged = { reportName: String?, jpsLibrary: JpsLibrary, owner: String?, packedModule: JpsModule ->
      when (rules) {
        MergeRules.PLATFORM -> isMergedIntoContentModuleJar(jpsLibraryName = jpsLibrary.name, ownerModuleName = owner, packedModule = packedModule)
        MergeRules.PLUGIN -> reportName != null && reportName in candidate.libraries
      }
    }
    val libraryTargetLabels = mergedLibraryTargetLabels(
      packedModuleNames = packedModuleNames,
      recordedNames = candidate.libraries,
      findModule = outputProvider::findModule,
      isMerged = isMerged,
      labelOf = { jpsName, owner -> index.libraryLabel(jpsName = jpsName, owner = owner, dependentIsCommunity = dependentIsCommunity) },
      refuse = { reportRefusal(moduleName, it) },
    ) ?: return null
    // `mergedLibraryTargetLabels` has resolved every packed module, so the lookup cannot fail here.
    val nativeLibs = mergedPresignedNativeLibs(
      packedModuleNames = packedModuleNames,
      findModule = outputProvider::findRequiredModule,
      isMerged = isMerged,
      presignedNativeLibs = PRESIGNED_NATIVE_LIBS,
    )
    if (nativeLibs.size > 1) {
      reportRefusal(moduleName, "several presigned native libraries: $nativeLibs")
      return null
    }
    // The packer neither signs a native file nor substitutes a presigned one.
    if (nativeLibs.isNotEmpty() && DEV_DIST_SIGN_NATIVE_FILE_MODE != SignNativeFileMode.DISABLED) {
      reportRefusal(moduleName, "a presigned native library with native signing enabled")
      return null
    }
    val nativeLib = nativeLibs.singleOrNull()

    return ContentModuleJarTarget(
      libraryTargetLabels = libraryTargetLabels,
      modulesBefore = candidate.modulesBefore.map { memberLabel(member = it, owner = moduleName) },
      modulesAfter = candidate.modulesAfter.map { memberLabel(member = it, owner = moduleName) },
      // The order `pack_jar` writes: the module outputs, then the libraries.
      sources = packedModuleNames.map {
        JarSourceRecipe(input = it, kind = "module", filter = "module-v1")
      } + libraryTargetLabels.map { label ->
        val input = if (dependentIsCommunity && label.startsWith("//")) "@community$label" else label
        JarSourceRecipe(input = input, kind = "library", filter = "library-v1")
      },
      nativeLib = nativeLib,
      nativeLibDir = nativeLib?.let { PRESIGNED_NATIVE_LIBS.getValue(it) },
    )
  }

  /** The label of a merged member as the owner's package writes it. The converter fails on a member it cannot name, and so does this. */
  private fun memberLabel(member: String, owner: String): String {
    return index.dependencyLabel(module = member, dependent = owner)
           ?: error("The package of '$owner' cannot name the merged member '$member'")
  }

  /**
   * Whether the platform layout merges the library [jpsLibraryName] into the jar owned by [packedModule].
   *
   * A module library is always merged: the plan generator refuses a platform layout that places one elsewhere. A
   * project library the layout packs itself is not merged. Any other project library is merged unless a same-group
   * dependency of [packedModule] declares it.
   */
  private fun isMergedIntoContentModuleJar(jpsLibraryName: String, ownerModuleName: String?, packedModule: JpsModule): Boolean {
    if (ownerModuleName != null) {
      return true
    }
    if (jpsLibraryName in LAYOUT_PACKED_PROJECT_LIBRARIES) {
      return false
    }
    return !hasLibraryInDependencyChainOfModuleDependencies(dependentModule = packedModule, jpsLibraryName = jpsLibraryName)
  }

  /**
   * Whether a module in the same name group that [dependentModule] depends on declares [jpsLibraryName] itself.
   *
   * The group is the module name without its last segment. The dependency counts when it is the group module, or when
   * it is in the group and no platform jar of any product names it.
   */
  private fun hasLibraryInDependencyChainOfModuleDependencies(dependentModule: JpsModule, jpsLibraryName: String): Boolean {
    val parentGroup = dependentModule.name.substringBeforeLast('.', missingDelimiterValue = "")
    if (parentGroup.isEmpty()) {
      return false
    }
    val prefix = "$parentGroup."
    for (element in dependentModule.dependenciesList.dependencies) {
      if (element !is JpsModuleDependency || !isProductionRuntimeScope(element)) {
        continue
      }
      val dependencyName = element.moduleReference.moduleName
      if (dependencyName != parentGroup && !(dependencyName.startsWith(prefix) && dependencyName !in candidacy.platformModuleNames)) {
        continue
      }
      val dependency = outputProvider.findModule(dependencyName) ?: continue
      if (declaresLibrary(module = dependency, jpsLibraryName = jpsLibraryName)) {
        return true
      }
    }
    return false
  }

  private fun declaresLibrary(module: JpsModule, jpsLibraryName: String): Boolean {
    return module.dependenciesList.dependencies.any { element ->
      element is JpsLibraryDependency && element.libraryReference.libraryName == jpsLibraryName && isProductionRuntimeScope(element)
    }
  }

  /** Whether [module] declares its `<module>.xml` descriptor at the root of a resource root. */
  private fun hasReadableDescriptor(module: JpsModule): Boolean = descriptorFiles(module, "${module.name}.xml").any()

  private fun reportRefusal(module: String, reason: String) {
    if (reportedRefusals.add(module)) {
      println("WARN: $module keeps being packed by JarPackager: $reason")
    }
  }
}

/**
 * The label of each library [packedModuleNames] merges, in merge order, or `null` when the jar stays with `JarPackager`.
 *
 * This is the converter's `mergedLibraryTargetLabels`. [isMerged] decides a library by its distribution name, its JPS
 * identity, its owner and the packed module that declares it. [labelOf] spells the container label of a JPS identity for
 * the dependent package.
 *
 * `null` keeps the jar with `JarPackager`. That happens when:
 * - [findModule] does not know a merged module
 * - a merged library has no name
 * - [labelOf] gives no label for a merged library
 * - the merged set differs from [recordedNames]
 *
 * The unnamed library and the set mismatch report through [refuse].
 */
internal fun mergedLibraryTargetLabels(
  packedModuleNames: List<String>,
  recordedNames: Set<String>,
  findModule: (String) -> JpsModule?,
  isMerged: (reportName: String?, jpsLibrary: JpsLibrary, owner: String?, packedModule: JpsModule) -> Boolean,
  labelOf: (jpsName: String, owner: String?) -> String?,
  refuse: (String) -> Unit,
): List<String>? {
  // A set, because two libraries of one module can intern to the same Bazel target, and Bazel rejects a repeated
  // label in an attribute.
  val targetLabels = LinkedHashSet<String>()
  val claimed = HashSet<Pair<String, String?>>()
  val names = HashSet<String>()
  for (packedModuleName in packedModuleNames) {
    val packedModule = findModule(packedModuleName) ?: return null
    for (element in packedModule.dependenciesList.dependencies) {
      if (element !is JpsLibraryDependency || !isProductionRuntimeScope(element)) {
        continue
      }

      val parentReference = element.libraryReference.parentReference
      if (parentReference.resolve() is JpsGlobal) {
        continue
      }

      val jpsLibrary = element.library ?: continue
      val owner = (parentReference as? JpsModuleReference)?.moduleName
      val reportName = distributionLibraryName(jpsLibrary)
      if (!isMerged(reportName, jpsLibrary, owner, packedModule) || !claimed.add(jpsLibrary.name to owner)) {
        continue
      }
      if (reportName == null) {
        refuse("an unnamed merged library has no single jar to name it by")
        return null
      }
      names.add(reportName)
      targetLabels.add(labelOf(jpsLibrary.name, owner) ?: return null)
    }
  }

  if (names != recordedNames) {
    val onlyRecorded = (recordedNames - names).sorted()
    val onlyMerged = (names - recordedNames).sorted()
    refuse(
      "the merged library set does not match the table" +
      (if (onlyRecorded.isEmpty()) "" else " (only in the table: $onlyRecorded)") +
      (if (onlyMerged.isEmpty()) "" else " (only merged: $onlyMerged)")
    )
    return null
  }
  return targetLabels.toList()
}

/** `COMPILE` and `RUNTIME` reach the production runtime. `PROVIDED`, `TEST` and a missing extension do not. */
internal fun isProductionRuntimeScope(element: JpsDependencyElement): Boolean {
  val scope = JpsJavaExtensionService.getInstance().getDependencyExtension(element)?.scope ?: return false
  return scope == JpsJavaDependencyScope.COMPILE || scope == JpsJavaDependencyScope.RUNTIME
}

/** The macro symbol, and the rule call name. */
private const val CONTENT_MODULE_JAR_SYMBOL: String = "content_module_jar"

/** The `.bzl` file of the macro, without the repository part. */
internal const val CONTENT_MODULE_JAR_BZL: String = "//platform/build-scripts/bazel-rules:content_module_jar.bzl"

/**
 * Project libraries the platform layout packs itself, so no content module may merge a second copy.
 *
 * The content-module derivation uses these names instead of constructing a platform layout.
 */
@ApiStatus.Internal
val LAYOUT_PACKED_PROJECT_LIBRARIES: Set<String> = setOf(
  "Log4J",
  "kotlin-stdlib",
  "slf4j-api",
  "slf4j-jdk14",
  "jetbrains.intellij.deps.java.atk.wrapper.linux",
  "yFiles",
)
