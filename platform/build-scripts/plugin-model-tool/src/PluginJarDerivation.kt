// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.pluginModelTool

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.impl.pluginDefaultJarName
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/**
 * One jar of a plugin, as the project model states it before any build runs.
 *
 * These facts define the jar path and its ordered module content.
 */
@ApiStatus.Internal
class DerivedPluginJar(
  /** The jar's path relative to the distribution root. */
  @JvmField val name: String,
  /**
   * The jar's path relative to the plugin's own `lib/`.
   *
   * [name] is this under the plugin's directory, and the directory is a `PluginLayout` decision.
   */
  @JvmField val relativeOutputFile: String,
  /**
   * Every member of the jar, in the order the layout merges them.
   *
   * [modules] and [contentModules] are the same names split by where the plugin names the member. The order is lost in
   * that split, and a packing action needs it: the packer resolves an entry two sources both offer to the first one.
   */
  @JvmField val members: List<String>,
  /** The plugin's main module, and every member the layout packs from its raw output. */
  @JvmField val modules: List<String>,
  /** The members that come from the plugin's own `<content>`. */
  @JvmField val contentModules: List<String>,
  /**
   * Whether this is the plugin's main jar, which is where the derivation co-packs a member with no jar of its own.
   *
   * A member the layout names a jar for is not co-packed, so it leaves this jar; see [PluginContentResidue.memberJars].
   */
  @JvmField val isMainJar: Boolean = false,
)

/** What [derivePluginContent] produced: the plugin's members and the facts the jar composition reads. */
@ApiStatus.Internal
class DerivedPluginContent(
  /** The members the model states, without the main module. */
  @JvmField val memberNames: List<String>,
  /**
   * Where this plugin puts each member's jar, by module name; see [DerivedPluginCandidacy.memberPaths].
   *
   * Every member, whatever its eligibility and whatever the residue vetoes. A member absent from the map has no
   * derivable jar at all, and the plugin's main jar holds it.
   */
  @JvmField val memberPaths: Map<String, String>,
  /** The members the plugin's own `<content>` names, which is what splits `contentModules` from `modules`. */
  @JvmField val closureMembers: Set<String>,
  /**
   * The jar the main module's own output goes to when the plugin's `<content>` names the main module itself, or `null`.
   *
   * `<module name="<main module>" loading="embedded"/>` in the plugin's own descriptor sends the main module to
   * `lib/<main module>.jar`. The conventional main jar is then written only if another member is co-packed into it.
   */
  @JvmField val mainModuleJar: String? = null,
)

/** Every jar of one plugin, with the content and the candidacy the jars were composed from. */
@ApiStatus.Internal
class DerivedPluginPacking(
  @JvmField val jars: List<DerivedPluginJar>,
  @JvmField val content: DerivedPluginContent,
  @JvmField val candidacy: DerivedPluginCandidacy,
)

/**
 * The time of each [derivePluginPacking] phase, summed over every plugin of one run.
 *
 * The workers of a run share one instance. Each field is a [LongAdder], so the workers do not contend.
 * A layout variant packing is one phase, [variantNanos], and it adds to no other phase.
 */
@ApiStatus.Internal
class PluginPackingStats {
  /** The time of [derivePluginContentClosure]. */
  @JvmField val closureNanos: LongAdder = LongAdder()
  /** The time of the layout residue and of the co-packed members. */
  @JvmField val residueNanos: LongAdder = LongAdder()
  /** The time of [autoLayoutChildren] and of the `auto` residue. */
  @JvmField val autoLayoutNanos: LongAdder = LongAdder()
  /** The time of [derivePluginContentCandidacy]. */
  @JvmField val candidacyNanos: LongAdder = LongAdder()
  /** The time of [derivePluginContent]. */
  @JvmField val contentNanos: LongAdder = LongAdder()
  /** The time of the packings of the layout variants. */
  @JvmField val variantNanos: LongAdder = LongAdder()
  /** The time of [composeDerivedPluginJars]. */
  @JvmField val composeNanos: LongAdder = LongAdder()
  /** The number of layout variants. */
  @JvmField val variantCount: LongAdder = LongAdder()
  /** The number of layout variants that got a packing of their own. A variant equal to an earlier one gets none. */
  @JvmField val packedVariantCount: LongAdder = LongAdder()
  /** The whole time of each plugin, by main module. */
  @JvmField val pluginNanos: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
}

/**
 * The answers that every packing of one run shares, by module.
 *
 * A key is a [JpsModule] instance, so one instance can serve the modules of two projects. Concurrent packings may share it.
 */
@ApiStatus.Internal
class PluginPackingCache {
  private val closures = ConcurrentHashMap<ClosureKey, WalkedContentModules>()
  private val memberDescriptors = ConcurrentHashMap<JpsModule, MemberDescriptorFacts>()

  /** [derivePluginContentClosure] of [module], or [EMPTY_WALKED_CONTENT_MODULES] for a module with no descriptor. */
  fun closure(module: JpsModule, findModule: (String) -> JpsModule?, layoutMembers: Collection<String>): WalkedContentModules {
    val key = ClosureKey(module = module, layoutMembers = layoutMembers.toList())
    closures.get(key)?.let {
      return it
    }
    val closure = derivePluginContentClosure(module = module, findModule = findModule, layoutMembers = layoutMembers)
                  ?: EMPTY_WALKED_CONTENT_MODULES
    return closures.putIfAbsent(key, closure) ?: closure
  }

  /** [memberDescriptor] of [module]. */
  fun memberDescriptorOf(module: JpsModule): MemberDescriptorFacts? {
    val cached = memberDescriptors.get(module) ?: run {
      val facts = memberDescriptor(module) ?: NO_MEMBER_DESCRIPTOR
      memberDescriptors.putIfAbsent(module, facts) ?: facts
    }
    return if (cached === NO_MEMBER_DESCRIPTOR) null else cached
  }

  private data class ClosureKey(@JvmField val module: JpsModule, @JvmField val layoutMembers: List<String>)
}

/** The cached answer for a module with no descriptor of its own. */
private val NO_MEMBER_DESCRIPTOR = MemberDescriptorFacts(hasPackageAttribute = false)

/**
 * Every fact of a layout variant, in iteration order, or `null` for a variant with variants of its own.
 *
 * Two variants with one key get one packing.
 */
private fun layoutVariantKey(facts: PluginLayoutFacts): List<Any?>? {
  if (facts.layoutVariants.isNotEmpty()) {
    return null
  }
  return listOf(
    facts.directoryName,
    facts.mainJarName,
    facts.memberJars.entries.map { listOf(it.key, it.value.toList()) },
    facts.unmergedMembers.toList(),
    facts.excludedModuleLibraries.entries.map { listOf(it.key, it.value.toList()) },
    facts.projectLibraries.entries.map { listOf(it.key, it.value) },
    facts.moduleLibraries.map { listOf(it.moduleName, it.libraryName, it.relativeOutputPath) },
    facts.generatorLibraries.toList(),
    facts.noEmbedding,
    facts.auto,
    facts.layoutJarMembers.entries.map { listOf(it.key, it.value) },
  )
}

/** Runs [block] and adds its time to [counter]. A `null` [counter] measures nothing. */
private inline fun <T> timed(counter: LongAdder?, block: () -> T): T {
  if (counter == null) {
    return block()
  }
  val start = System.nanoTime()
  try {
    return block()
  }
  finally {
    counter.add(System.nanoTime() - start)
  }
}

/**
 * Every jar the plugin [mainModule] puts in its own directory, derived from the project model and [facts].
 *
 * Four derivations meet here:
 *
 * 1. [derivePluginContent] gives the members, from the plugin's own `<content>` with every `xi:include` followed plus
 *    the members [facts] merge, and where the plugin puts each member's jar;
 * 2. [facts] gives the plugin's directory and main jar name;
 * 3. [autoLayoutChildren] gives the members an `auto` layout takes from the main module's dependency group.
 *    [isPackedElsewhere] answers which candidate the platform or another plugin layout packs already;
 * 4. [PluginContentResidue.memberJars] gives the jars the layout names itself. A row states the member's whole jar set,
 *    so it wins over the path of 1 and over the main-jar co-pack.
 *
 * A member with neither a row nor a jar of its own is co-packed into the plugin's main jar.
 *
 * `null` for a module the project does not hold. A module with no `META-INF/plugin.xml` in a production resource root
 * has an empty closure, so its members are the ones [facts] state, and its main jar holds the main module.
 *
 * [frontendRoots] are the modules a frontend-compatible module must not reach; see [FrontendCompatibility]. An empty
 * list is a product without an embedded frontend. [frontend] is the filter over them, which the plugins of one run can share.
 *
 * [stats] gets the time of each phase. A `null` [stats] measures nothing. [cache] holds the answers the packings of one run share.
 */
@ApiStatus.Internal
fun derivePluginPacking(
  mainModule: String,
  facts: PluginLayoutFacts,
  project: JpsProject,
  outputProvider: ModuleOutputProvider,
  frontendRoots: List<String>,
  isPackedElsewhere: (String) -> Boolean = { false },
  frontend: FrontendCompatibility = FrontendCompatibility(roots = frontendRoots.toSet(), findModule = project::findModuleByName),
  stats: PluginPackingStats? = null,
  cache: PluginPackingCache = PluginPackingCache(),
): DerivedPluginPacking? {
  require(frontend.roots == frontendRoots.toSet()) { "The frontend filter has other roots than $frontendRoots" }
  val module = outputProvider.findModule(mainModule) ?: return null
  val findModule: (String) -> JpsModule? = outputProvider::findModule
  val closure = timed(stats?.closureNanos) {
    cache.closure(module = module, findModule = findModule, layoutMembers = facts.memberJars.keys)
  }
  val closureMembers = closure.moduleNames.mapTo(HashSet()) { it.substringBeforeLast('/') }
  var effectiveResidue = timed(stats?.residueNanos) {
    layoutResidueOf(mainModule = mainModule, facts = facts, closureMembers = closureMembers)
  }
  if (facts.auto) {
    timed(stats?.autoLayoutNanos) {
      // A `<content>` member and a layout member are packed already, so the rule leaves them where they are.
      val packedByPlugin = closureMembers + facts.memberJars.keys
      val children = autoLayoutChildren(module = module, isPackedElsewhere = { it in packedByPlugin || isPackedElsewhere(it) })
      if (children.isNotEmpty()) {
        effectiveResidue += autoLayoutResidue(mainModule = mainModule, mainJarName = facts.mainJarName, children = children, frontend = frontend)
      }
    }
  }
  timed(stats?.residueNanos) {
    val coPacked = coPackedMembers(memberJars = effectiveResidue.memberJars, closureMembers = closureMembers)
    if (coPacked.isNotEmpty()) {
      effectiveResidue += PluginContentResidue(vetoedMembers = coPacked)
    }
  }
  val candidacy = timed(stats?.candidacyNanos) {
    derivePluginContentCandidacy(
      mainModule = mainModule,
      mainJarName = facts.mainJarName,
      findModule = findModule,
      frontend = frontend,
      residue = effectiveResidue,
      closure = closure,
      readMemberDescriptor = cache::memberDescriptorOf,
    )
  }
  val content = timed(stats?.contentNanos) {
    derivePluginContent(
      module = module,
      closure = closure,
      candidacy = candidacy,
      residue = effectiveResidue,
    )
  }
  // The jar of each member this project holds a module for.
  val derivedJars = LinkedHashMap<String, String>()
  for ((memberName, relativeOutputFile) in content.memberPaths) {
    if (findModule(memberName) != null) {
      derivedJars.put(memberName, relativeOutputFile)
    }
  }
  val layoutJarMembers = if (facts.layoutVariants.isEmpty()) {
    facts.layoutJarMembers
  }
  else {
    timed(stats?.variantNanos) {
      stats?.variantCount?.add(facts.layoutVariants.size.toLong())
      val orders = LinkedHashMap<String, MutableList<List<String>>>()
      // A variant equal to an earlier one gets no packing.
      val packedVariants = HashSet<List<Any?>>()
      for (variant in facts.layoutVariants) {
        val key = layoutVariantKey(variant)
        if (key != null && !packedVariants.add(key)) {
          continue
        }
        stats?.packedVariantCount?.increment()
        val packing = requireNotNull(derivePluginPacking(
          mainModule = mainModule,
          facts = variant,
          project = project,
          outputProvider = outputProvider,
          frontendRoots = frontendRoots,
          isPackedElsewhere = isPackedElsewhere,
          frontend = frontend,
          cache = cache,
        ))
        for (jar in packing.jars) {
          if (jar.relativeOutputFile in facts.layoutJarMembers) {
            orders.computeIfAbsent(jar.relativeOutputFile) { ArrayList() }.add(jar.members)
          }
        }
      }
      mergeLayoutJarOrders(mainModule, orders)
    }
  }
  val jars = timed(stats?.composeNanos) {
    composeDerivedPluginJars(
      libDir = "plugins/${facts.directoryName}/lib/",
      mainJarName = facts.mainJarName,
      mainModule = mainModule,
      // A member with a derived jar this project holds no module for gets no jar at all. Nothing can say who packs a
      // path with no module behind it, and the plugin's main jar does not hold the member either.
      memberNames = content.memberNames.filter { it !in content.memberPaths || it in derivedJars },
      derivedJars = derivedJars,
      closureMembers = content.closureMembers,
      memberJars = effectiveResidue.memberJars,
      mainModuleJar = content.mainModuleJar,
      layoutJarMembers = layoutJarMembers,
    )
  }
  return DerivedPluginPacking(jars = jars, content = content, candidacy = candidacy)
}

/**
 * The `<content>` members whose own jar the layout packs another member into.
 *
 * `intellij.station.plugin` is the case: the layout puts `intellij.station.comms.mcp` into `modules/intellij.station.aia.jar`,
 * the jar `intellij.station.aia` gets from the convention. The member's own packing target would write a jar without
 * the second member, so no target may serve the module, for any plugin.
 */
private fun coPackedMembers(memberJars: Map<String, Set<String>>, closureMembers: Set<String>): Set<String> {
  val result = LinkedHashSet<String>()
  for ((member, paths) in memberJars) {
    for (path in paths) {
      val named = path.removePrefix("modules/").removeSuffix(".jar")
      if (named != member && named in closureMembers) {
        result.add(named)
      }
    }
  }
  return result
}

/**
 * The `auto` children of a plugin as members, with the `-frontend.jar` stated for a child the frontend filter splits.
 *
 * A child goes where a plain `withModule(name)` item goes: the main jar, or the plugin's `-frontend.jar` when the child
 * is frontend-compatible and the main module is not. The main jar is the default of the composition, so only the
 * frontend jar is a [PluginContentResidue.memberJars] row.
 */
private fun autoLayoutResidue(mainModule: String, mainJarName: String, children: List<String>, frontend: FrontendCompatibility): PluginContentResidue {
  val frontendJarName = pluginDefaultJarName(mainJarName, frontendSplit = true)
  val memberJars = LinkedHashMap<String, Set<String>>()
  for (child in children) {
    if (frontend.isSplit(mainModule = mainModule, member = child)) {
      memberJars.put(child, setOf(frontendJarName))
    }
  }
  return PluginContentResidue(extraMembers = children.toSet(), memberJars = memberJars)
}

/**
 * The producer of a plugin's dev-distribution content, from the project model.
 *
 * The members come from the plugin's own resolved `<content>` plus the layout members of [residue]. The jar of each
 * member comes from [candidacy], which holds the one derivation of that question.
 */
private fun derivePluginContent(
  module: JpsModule,
  closure: WalkedContentModules,
  candidacy: DerivedPluginCandidacy,
  residue: PluginContentResidue,
): DerivedPluginContent {
  val moduleName = module.name
  // A module shipped under another descriptor names one member, by the module name before the `/`.
  val memberNames = closure.moduleNames.mapTo(LinkedHashSet()) { it.substringBeforeLast('/') }
  memberNames.addAll(residue.extraMembers)
  memberNames.remove(moduleName)
  val memberPaths = candidacy.memberPaths.filterKeys { it in memberNames }
  val closureMembers = closure.moduleNames.mapTo(HashSet()) { it.substringBeforeLast('/') }
  return DerivedPluginContent(
    memberNames = memberNames.toList(),
    memberPaths = memberPaths,
    closureMembers = closureMembers,
    mainModuleJar = selfEmbeddedMainModuleJar(module = module, closure = closure),
  )
}

/**
 * `lib/<main module>.jar` when the plugin's own `<content>` names its main module as an `embedded` member, else `null`.
 *
 * Such a member gets its own jar and the main
 * module is a member like any other there. See [DerivedPluginContent.mainModuleJar].
 */
private fun selfEmbeddedMainModuleJar(module: JpsModule, closure: WalkedContentModules): String? {
  val mainModule = module.name
  if (closure.loadingRules.get(mainModule) != EMBEDDED_LOADING_RULE) {
    return null
  }
  return "$mainModule.jar"
}

/**
 * The jars of one plugin, from the facts [derivePluginPacking] gathers and nothing else.
 *
 * The whole rule, and it reads no project model. Every fact is a parameter, so a caller states them directly.
 *
 * Member order. The main jar holds the co-packed members in `<content>` order, and the main module comes last. A jar
 * the layout names holds its members in [layoutJarMembers] order. A member-named jar holds one member.
 *
 * One jar per path. The build packs one jar at a path, so a member-named jar and a jar the layout names at the same
 * path are one jar. The member the jar is named after comes first, and the layout members follow. Such a jar reads its
 * destination, the way a jar the layout names does.
 */
@ApiStatus.Internal
fun composeDerivedPluginJars(
  libDir: String,
  mainJarName: String,
  mainModule: String,
  /**
   * The plugin's members, in the order the jars take. The caller already dropped a member that the derivation states a
   * jar for and this project holds no module for. Such a member gets no jar at all.
   */
  memberNames: List<String>,
  /**
   * Where the derivation puts each member's jar, relative to the plugin's `lib/`.
   *
   * [mainJarName] is one of the values it may hold, and it means the plugin co-packs the member. A member absent from
   * the map has no derivable jar, and the main jar holds it too.
   */
  derivedJars: Map<String, String>,
  /** The members the plugin's own `<content>` names, which is what splits `contentModules` from `modules`. */
  closureMembers: Set<String>,
  /** See [PluginContentResidue.memberJars]. */
  memberJars: Map<String, Set<String>>,
  /** See [DerivedPluginContent.mainModuleJar]: the main module's own jar where its `<content>` embeds it, else `null`. */
  mainModuleJar: String? = null,
  /** The custom jar members in source layout order. This does not change the order of the jars. */
  layoutJarMembers: Map<String, List<String>> = emptyMap(),
): List<DerivedPluginJar> {
  val result = ArrayList<DerivedPluginJar>()
  val mainJarContentModules = ArrayList<String>()
  val mainJarModules = ArrayList<String>()
  mainJarModules.add(mainModule)
  // The main jar's merge order, which its two split lists cannot state. The build merges the co-packed members in
  // `<content>` order and the main module after them.
  val mainJarMembers = ArrayList<String>()
  // The members of each jar the layout names, by the jar's path under the plugin's `lib/`. One jar can hold several
  // members, so the rows are grouped rather than turned into one jar each.
  val statedJarMembers = LinkedHashMap<String, MutableList<String>>()
  // The member each member-named jar is named after, by the jar's path. A member's own jar is named after that member,
  // so no two members share a path here.
  val memberNamedJars = LinkedHashMap<String, String>()
  fun statedJar(path: String, members: List<String>): DerivedPluginJar = DerivedPluginJar(
    name = libDir + path,
    relativeOutputFile = path,
    members = members,
    modules = members.filter { it !in closureMembers },
    contentModules = members.filter { it in closureMembers },
  )
  for (memberName in memberNames) {
    val statedJars = memberJars.get(memberName)
    if (statedJars != null) {
      for (path in statedJars) {
        if (path == mainJarName) {
          (if (memberName in closureMembers) mainJarContentModules else mainJarModules).add(memberName)
          mainJarMembers.add(memberName)
        }
        else {
          statedJarMembers.computeIfAbsent(path) { ArrayList() }.add(memberName)
        }
      }
      // A jar under a subdirectory is a second jar beside a `<content>` member's own, and a flat one replaces it. The
      // build admits a second item for one module only when one of the two paths holds a `/`. So only a `<content>`
      // member whose every stated jar is nested keeps the jar the convention gives it below. A pure layout member has
      // no convention jar, and its stated jars are all of it.
      if (memberName !in closureMembers || statedJars.any { !it.contains('/') }) {
        continue
      }
    }
    val relativeOutputFile = derivedJars.get(memberName)
    if (relativeOutputFile == null || relativeOutputFile == mainJarName) {
      (if (memberName in closureMembers) mainJarContentModules else mainJarModules).add(memberName)
      mainJarMembers.add(memberName)
      continue
    }
    memberNamedJars.put(relativeOutputFile, memberName)
  }
  for ((path, members) in statedJarMembers) {
    val layoutOrder = layoutJarMembers.get(path) ?: continue
    val orderedMembers = layoutOrder.filter { it in members }
    check(orderedMembers.size == members.size) {
      "Plugin `$mainModule`: the layout does not state every member order in `$path`"
    }
    members.clear()
    members.addAll(orderedMembers)
  }
  for ((path, memberName) in memberNamedJars) {
    val statedMembers = statedJarMembers.remove(path)
    if (statedMembers == null) {
      result.add(
        DerivedPluginJar(
          name = libDir + path,
          relativeOutputFile = path,
          members = listOf(memberName),
          modules = if (memberName in closureMembers) emptyList() else listOf(memberName),
          contentModules = if (memberName in closureMembers) listOf(memberName) else emptyList(),
        )
      )
    }
    else {
      // The layout names the member's own jar for another member too, so the two are one jar. The member's own
      // packing target cannot pack a jar with a member the layout adds, so the jar reads its destination.
      result.add(statedJar(path = path, members = listOf(memberName) + statedMembers))
    }
  }
  for ((path, members) in statedJarMembers) {
    result.add(statedJar(path = path, members = members))
  }
  if (mainModuleJar != null) {
    // The main module is an `embedded` member of its own `<content>`, so it gets a jar of its own. The conventional
    // main jar exists only for the members the convention co-packs into it.
    result.add(
      DerivedPluginJar(
        name = libDir + mainModuleJar,
        relativeOutputFile = mainModuleJar,
        members = listOf(mainModule),
        modules = emptyList(),
        contentModules = listOf(mainModule),
        isMainJar = mainJarMembers.isEmpty(),
      )
    )
    if (mainJarMembers.isNotEmpty()) {
      result.add(
        DerivedPluginJar(
          name = libDir + mainJarName,
          relativeOutputFile = mainJarName,
          members = mainJarMembers,
          modules = mainJarModules.filter { it != mainModule },
          contentModules = mainJarContentModules,
          isMainJar = true,
        )
      )
    }
    return result
  }
  result.add(
    DerivedPluginJar(
      name = libDir + mainJarName,
      relativeOutputFile = mainJarName,
      members = mainJarMembers + mainModule,
      modules = mainJarModules,
      contentModules = mainJarContentModules,
      isMainJar = true,
    )
  )
  return result
}
