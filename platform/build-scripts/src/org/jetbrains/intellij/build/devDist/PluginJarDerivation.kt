// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule

/**
 * One jar of a plugin, as the project model states it before any build runs.
 *
 * Three facts, and every one of them is what a per-jar packing action has to declare: where the jar goes, and whose
 * module output it holds.
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
   * Whether a packing target packs this jar, so no fragment packs it.
   *
   * Two producers, two keys. A jar named after one member is read by that member. A jar the layout names itself is
   * read by its own destination. The keys cannot be swapped: one member can be a member of two jars the layout names,
   * and it then keeps its declaration while a packing target owns both jars.
   */
  @JvmField val isHandedOver: Boolean,
  /**
   * Whether this is the plugin's main jar, which is where the derivation co-packs a member with no jar of its own.
   *
   * A member the layout names a jar for is not co-packed, so it leaves this jar; see [PluginContentResidue.memberJars].
   */
  @JvmField val isMainJar: Boolean = false,
  /**
   * The module libraries a packing target merges into this jar, or `null` where no packing target packs it.
   *
   * The union over [members] of [DerivedPluginCandidacy.memberLibraries], stated for a handed-over jar alone. `null`
   * as well for a handed-over jar with a member the derivation states no library set for.
   */
  @JvmField val libraries: List<String>? = null,
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
  /**
   * Where this plugin offers each member's jar to the member's own packing target, by module name.
   *
   * The eligible half of [memberPaths], with the raw members taken out. A raw member keeps the jar it derives, because
   * a jar the layout names holds the module's raw output; see [layoutJarMembers].
   */
  @JvmField val prepackedPaths: Map<String, String>,
  /**
   * The members of [prepackedPaths] whose own packing target really packs their jar.
   *
   * Whether a target exists is a fact the caller states through the `isPrepackedContentModule` predicate of
   * [derivePluginPacking]. The default predicate hands every offered member over.
   */
  @JvmField val handedOverMembers: Set<String>,
  /** The members the plugin's own `<content>` names, which is what splits `contentModules` from `modules`. */
  @JvmField val closureMembers: Set<String>,
  /** See [DerivedPluginCandidacy.memberLibraries]. */
  @JvmField val memberLibraries: Map<String, Set<String>?>,
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
 * Every jar the plugin [mainModule] puts in its own directory, derived from the project model and [facts].
 *
 * Five derivations meet here:
 *
 * 1. [derivePluginContent] gives the members, from the plugin's own `<content>` with every `xi:include` followed plus
 *    the members [facts] merge, and where the plugin puts each member's jar;
 * 2. [facts] gives the plugin's directory and main jar name;
 * 3. [autoLayoutChildren] gives the members an `auto` layout takes from the main module's dependency group.
 *    [isPackedElsewhere] answers which candidate the platform or another plugin layout packs already;
 * 4. [isPrepackedContentModule] says which member's own jar a packing target already packs. The default hands every
 *    offered member over;
 * 5. [PluginContentResidue.memberJars] gives the jars the layout names itself. A row states the member's whole jar set,
 *    so it wins over the path of 1 and over the main-jar co-pack.
 *
 * A member with neither a row nor a jar of its own is co-packed into the plugin's main jar.
 *
 * `null` for a module the project does not hold. A module with no `META-INF/plugin.xml` in a production resource root
 * has an empty closure, so its members are the ones [facts] state, and its main jar holds the main module.
 *
 * [frontendRoots] are the modules a frontend-compatible module must not reach; see [FrontendCompatibility]. An empty
 * list is a product without an embedded frontend.
 */
@ApiStatus.Internal
fun derivePluginPacking(
  mainModule: String,
  facts: PluginLayoutFacts,
  project: JpsProject,
  outputProvider: ModuleOutputProvider,
  frontendRoots: List<String>,
  isPrepackedContentModule: (String) -> Boolean = { true },
  isPackedElsewhere: (String) -> Boolean = { false },
): DerivedPluginPacking? {
  val module = outputProvider.findModule(mainModule) ?: return null
  val findModule: (String) -> JpsModule? = outputProvider::findModule
  val closure = derivePluginContentClosure(module = module, findModule = findModule, layoutMembers = facts.memberJars.keys)
                ?: EMPTY_WALKED_CONTENT_MODULES
  val frontend = FrontendCompatibility(roots = frontendRoots.toSet(), findModule = project::findModuleByName)
  val closureMembers = closure.moduleNames.mapTo(HashSet()) { it.substringBeforeLast('/') }
  var effectiveResidue = layoutResidueOf(mainModule = mainModule, facts = facts, closureMembers = closureMembers)
  if (facts.auto) {
    // A `<content>` member and a layout member are packed already, so the rule leaves them where they are.
    val packedByPlugin = closureMembers + facts.memberJars.keys
    val children = autoLayoutChildren(module = module, isPackedElsewhere = { it in packedByPlugin || isPackedElsewhere(it) })
    if (children.isNotEmpty()) {
      effectiveResidue += autoLayoutResidue(mainModule = mainModule, mainJarName = facts.mainJarName, children = children, frontend = frontend)
    }
  }
  val coPacked = coPackedMembers(memberJars = effectiveResidue.memberJars, closureMembers = closureMembers)
  if (coPacked.isNotEmpty()) {
    effectiveResidue += PluginContentResidue(vetoedMembers = coPacked)
  }
  val candidacy = derivePluginContentCandidacy(
    mainModule = mainModule,
    mainJarName = facts.mainJarName,
    findModule = findModule,
    frontend = frontend,
    residue = effectiveResidue,
    closure = closure,
  )
  val content = derivePluginContent(
    module = module,
    mainJarName = facts.mainJarName,
    closure = closure,
    candidacy = candidacy,
    residue = effectiveResidue,
    findModule = findModule,
    isPrepackedContentModule = isPrepackedContentModule,
  )
  // The jar of each member this project holds a module for.
  val derivedJars = LinkedHashMap<String, String>()
  for ((memberName, relativeOutputFile) in content.memberPaths) {
    if (findModule(memberName) != null) {
      derivedJars.put(memberName, relativeOutputFile)
    }
  }
  val jars = composeDerivedPluginJars(
    libDir = "plugins/${facts.directoryName}/lib/",
    mainJarName = facts.mainJarName,
    mainModule = mainModule,
    // A member with a derived jar this project holds no module for gets no jar at all. Nothing can say who packs a
    // path with no module behind it, and the plugin's main jar does not hold the member either.
    memberNames = content.memberNames.filter { it !in content.memberPaths || it in derivedJars },
    derivedJars = derivedJars,
    handedOverMembers = content.handedOverMembers,
    closureMembers = content.closureMembers,
    memberJars = effectiveResidue.memberJars,
    memberLibraries = content.memberLibraries,
    mainModuleJar = content.mainModuleJar,
  )
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
  val frontendJarName = mainJarName.removeSuffix(".jar") + "-frontend.jar"
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
 * member comes from [candidacy], which holds the one derivation of that question. A member with no offer keeps its
 * path and loses only the hand-off.
 */
private fun derivePluginContent(
  module: JpsModule,
  mainJarName: String,
  closure: WalkedContentModules,
  candidacy: DerivedPluginCandidacy,
  residue: PluginContentResidue,
  findModule: (String) -> JpsModule?,
  isPrepackedContentModule: (String) -> Boolean,
): DerivedPluginContent {
  val moduleName = module.name
  // A module shipped under another descriptor names one member, by the module name before the `/`.
  val memberNames = closure.moduleNames.mapTo(LinkedHashSet()) { it.substringBeforeLast('/') }
  memberNames.addAll(residue.extraMembers)
  memberNames.remove(moduleName)
  val memberPaths = candidacy.memberPaths.filterKeys { it in memberNames }
  val closureMembers = closure.moduleNames.mapTo(HashSet()) { it.substringBeforeLast('/') }
  // The hand-off is the narrow half. A raw member keeps the jar it derives above, and only its hand-off goes, because
  // a jar the layout names holds the module's raw output.
  val rawMembers = layoutJarMembers(residue = residue, closureMembers = closureMembers, mainJarName = mainJarName)
  val prepackedPaths = candidacy.offers.asSequence()
    .filterNot { it.moduleName in rawMembers }
    .filter { it.moduleName in memberNames }
    .associate { it.moduleName to it.relativeOutputFile }
  val handedOverMembers = prepackedPaths.keys.filterTo(LinkedHashSet()) { findModule(it) != null && isPrepackedContentModule(it) }
  return DerivedPluginContent(
    memberNames = memberNames.toList(),
    memberPaths = memberPaths,
    prepackedPaths = prepackedPaths,
    handedOverMembers = handedOverMembers,
    closureMembers = closureMembers,
    memberLibraries = candidacy.memberLibraries,
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
 * the layout names holds its members in [memberNames] order, which is `<content>` order first and then the layout
 * members in the sorted order [PluginLayoutFacts.memberJars] gives them. A member-named jar holds one member.
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
  /** The members whose own packing target packs their jar; see [DerivedPluginJar.isHandedOver]. */
  handedOverMembers: Set<String>,
  /** The members the plugin's own `<content>` names, which is what splits `contentModules` from `modules`. */
  closureMembers: Set<String>,
  /** See [PluginContentResidue.memberJars]. */
  memberJars: Map<String, Set<String>>,
  /**
   * The destinations the plugin's own packing targets pack, the second key of [DerivedPluginJar.isHandedOver].
   *
   * A Bazel fact. The platform derivation states none, and the converter that emits the targets supplies it.
   */
  handedOverJars: Set<String> = emptySet(),
  /** See [DerivedPluginJar.libraries]; a member absent here has an unknown set. */
  memberLibraries: Map<String, Set<String>?> = emptyMap(),
  /** See [DerivedPluginContent.mainModuleJar]: the main module's own jar where its `<content>` embeds it, else `null`. */
  mainModuleJar: String? = null,
): List<DerivedPluginJar> {
  fun librariesOf(members: List<String>): List<String>? {
    val libraries = sortedSetOf<String>()
    for (member in members) {
      libraries.addAll(memberLibraries.get(member) ?: return null)
    }
    return libraries.toList()
  }

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
    // The destination's key. The plugin's own packing target is the producer of a jar the layout names, and that
    // target states a destination.
    isHandedOver = path in handedOverJars,
    libraries = if (path in handedOverJars) librariesOf(members) else null,
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
          // The member's key. This jar is the member's own, so the member's own packing target is the producer.
          isHandedOver = memberName in handedOverMembers,
          libraries = if (memberName in handedOverMembers) librariesOf(listOf(memberName)) else null,
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
        isHandedOver = false,
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
          isHandedOver = false,
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
      // The plugin's main jar holds the plugin's own descriptor, so it is a jar only a fragment packs.
      isHandedOver = false,
      isMainJar = true,
    )
  )
  return result
}
