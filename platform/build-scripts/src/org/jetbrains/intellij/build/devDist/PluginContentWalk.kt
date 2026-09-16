// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/** The plugin's own descriptor, at the load path every plugin uses. */
@ApiStatus.Internal
const val PLUGIN_XML_LOAD_PATH: String = "META-INF/plugin.xml"

/**
 * Every file a production resource root of [module] holds at [loadPath], in resource-root order.
 *
 * Only a resource root the jar takes at its own root is asked, because a load path is jar-relative. A root the layout
 * maps into a subdirectory answers another path, and no descriptor of this project needs that.
 */
@ApiStatus.Internal
fun descriptorFiles(module: JpsModule, loadPath: String): Sequence<Path> {
  return module.getSourceRoots(JavaResourceRootType.RESOURCE).asSequence()
    .filter { it.properties.relativeOutputPath.isEmpty() }
    .map { it.path.resolve(loadPath) }
    .filter { it.isRegularFile() }
}

/**
 * What one walk of a plugin's descriptor closure found, and what it could not follow.
 *
 * [unresolvedIncludes] tells an empty closure from a closure the walk failed to read. The content modules behind an
 * unresolved include are missing, and [derivePluginContentClosure] reads the list to decide on a second round.
 */
@ApiStatus.Internal
class WalkedContentModules(
  /** Every `<module>` name of the resolved `<content>`, in descriptor order. A name can hold a `/`. */
  @JvmField val moduleNames: List<String>,
  /**
   * The `loading` attribute of every `<module>` that states one, by the name the element states.
   *
   * The build branches on this attribute first, so it decides where a member's jar goes.
   */
  @JvmField val loadingRules: Map<String, String>,
  @JvmField val unresolvedIncludes: List<String>,
)

/** What a walk of a plugin that has no descriptor at all found. */
@ApiStatus.Internal
val EMPTY_WALKED_CONTENT_MODULES: WalkedContentModules = WalkedContentModules(
  moduleNames = emptyList(),
  loadingRules = emptyMap(),
  unresolvedIncludes = emptyList(),
)

/**
 * Every `<module/>` of the resolved `<content>` of [descriptor], in descriptor order.
 *
 * [resolveInclude] answers the file behind each `xi:include` load path. A root-level include is replaced with the
 * included root's children at the include's own position, so a `<content>` block an included file states belongs
 * where the include sat. The raw `loading` attribute is kept, because the jar path depends on it.
 */
@ApiStatus.Internal
fun walkContentModules(descriptor: Path, resolveInclude: (String) -> Path?): WalkedContentModules {
  val result = ArrayList<String>()
  val loadingRules = HashMap<String, String>()
  val unresolved = ArrayList<String>()
  appendContentModules(
    root = JDOMUtil.load(descriptor),
    resolveInclude = resolveInclude,
    visited = HashSet(),
    out = result,
    loadingRules = loadingRules,
    unresolved = unresolved,
  )
  return WalkedContentModules(
    moduleNames = result,
    loadingRules = loadingRules,
    unresolvedIncludes = unresolved,
  )
}

private fun appendContentModules(
  root: Element,
  resolveInclude: (String) -> Path?,
  visited: MutableSet<String>,
  out: MutableList<String>,
  loadingRules: MutableMap<String, String>,
  unresolved: MutableList<String>,
) {
  for (child in root.children) {
    if (child.name == "content") {
      for (moduleElement in child.getChildren("module")) {
        val name = moduleElement.getAttributeValue("name") ?: continue
        out.add(name)
        moduleElement.getAttributeValue("loading")?.let { loadingRules.putIfAbsent(name, it) }
      }
      continue
    }
    if (child.name != "include" || child.namespace != JDOMUtil.XINCLUDE_NAMESPACE) {
      continue
    }
    val href = child.getAttributeValue("href") ?: continue
    // An optional or a conditional include contributes no child at all.
    if (child.getChild("fallback", child.namespace) != null ||
        child.getAttribute("includeIf") != null ||
        child.getAttribute("includeUnless") != null) {
      continue
    }
    // Any other pointer selects a subtree instead of the included root's children. The position a `<content>` block
    // lands at is then not the include's, so such an include is not followed.
    if (child.getAttributeValue("xpointer").let { it != null && it != DEFAULT_XPOINTER }) {
      continue
    }
    val loadPath = toLoadPath(href)
    if (!visited.add(loadPath)) {
      continue
    }
    val file = resolveInclude(loadPath)?.takeIf { it.isRegularFile() }
    if (file == null) {
      unresolved.add(loadPath)
      continue
    }
    val included = JDOMUtil.load(file)
    // An included root that is not the pointer's root tag splices no child and contributes no content module.
    if (included.name != "idea-plugin") {
      continue
    }
    appendContentModules(
      root = included,
      resolveInclude = resolveInclude,
      visited = visited,
      out = out,
      loadingRules = loadingRules,
      unresolved = unresolved,
    )
  }
}

/** The `xpointer` an `xi:include` takes when it states none. It selects every child. */
private const val DEFAULT_XPOINTER: String = "xpointer(/idea-plugin/*)"

/**
 * The load path an `xi:include` href asks the descriptor cache for.
 *
 * Three prefixes name a module descriptor at a resource root. Every other relative href names a file under `META-INF/`.
 */
@ApiStatus.Internal
fun toLoadPath(href: String): String {
  return when {
    href.startsWith("/") -> href.substring(1)
    href.startsWith("intellij.") || href.startsWith("fleet.") || href.startsWith("kotlin.") -> href
    else -> "META-INF/$href"
  }
}

/**
 * The plugin's own resolved `<content>`, or `null` when no production resource root of [module] holds `META-INF/plugin.xml`.
 *
 * A plugin with no descriptor of its own is a hold-out, and `null` says so rather than an empty closure.
 * [includeFiles] answers an `xi:include` load path that no convention resolves, by load path. [layoutMembers] are the
 * modules the plugin's layout packs, which the include probe searches too; see [walkPluginContentClosure].
 */
@ApiStatus.Internal
fun derivePluginContentClosure(
  module: JpsModule,
  findModule: (String) -> JpsModule?,
  includeFiles: Map<String, Path> = emptyMap(),
  layoutMembers: Collection<String> = emptyList(),
): WalkedContentModules? {
  val descriptor = descriptorFiles(module = module, loadPath = PLUGIN_XML_LOAD_PATH).firstOrNull() ?: return null
  return walkPluginContentClosure(
    module = module,
    descriptor = descriptor,
    findModule = findModule,
    includeFiles = includeFiles,
    layoutMembers = layoutMembers,
  )
}

/**
 * [walkContentModules] over [descriptor], with every `xi:include` resolved from the project model.
 *
 * Two probes resolve an include, and neither scans a directory:
 *
 * 1. a load path that names a module descriptor names its module too, by the longest dotted prefix that is a module
 *    of this project;
 * 2. any other load path is looked for in the production resource roots of the plugin's own members, of the modules
 *    the main module depends on, and of the modules the layout packs ([layoutMembers]).
 *
 * The second probe needs the member set, and the member set needs the include. So the walk repeats while it learns a
 * new module to probe, and [MAX_INCLUDE_ROUNDS] bounds it. The build resolves an include over the whole plugin
 * classpath, and a layout member holds a file the main module neither owns nor depends on:
 * `AllDatabaseDialectsShared.xml` of `intellij.database.dialects.core` in `language-server.plugins.sql` is the case.
 */
private fun walkPluginContentClosure(
  module: JpsModule,
  descriptor: Path,
  findModule: (String) -> JpsModule?,
  includeFiles: Map<String, Path>,
  layoutMembers: Collection<String>,
): WalkedContentModules {
  val resolver = ConventionIncludeResolver(mainModule = module, findModule = findModule, includeFiles = includeFiles)
  resolver.learnMembers(layoutMembers)
  var walked = walkContentModules(descriptor = descriptor, resolveInclude = resolver::resolve)
  repeat(MAX_INCLUDE_ROUNDS) {
    if (walked.unresolvedIncludes.isEmpty() || !resolver.learnMembers(walked.moduleNames)) {
      return walked
    }
    walked = walkContentModules(descriptor = descriptor, resolveInclude = resolver::resolve)
  }
  return walked
}

/**
 * How many times [walkPluginContentClosure] repeats its walk.
 *
 * One round per level of includes that hides the member holding the next level's file. The loop also stops as soon
 * as a round learns no member.
 */
private const val MAX_INCLUDE_ROUNDS: Int = 3

/** Resolves an `xi:include` load path against [includeFiles] first, then the two conventions; see [walkPluginContentClosure]. */
private class ConventionIncludeResolver(
  mainModule: JpsModule,
  private val findModule: (String) -> JpsModule?,
  private val includeFiles: Map<String, Path>,
) {
  private val searchModules = LinkedHashSet<JpsModule>()

  init {
    searchModules.add(mainModule)
    // A `META-INF/xxx.xml` include names no module, and the file sits in a module the plugin main module depends on.
    // The dependency is the only statement of that relation the model has, so the probe set is seeded with it.
    for (element in mainModule.dependenciesList.dependencies) {
      if (element is JpsModuleDependency) {
        findModule(element.moduleReference.moduleName)?.let { searchModules.add(it) }
      }
    }
  }

  fun resolve(loadPath: String): Path? {
    includeFiles.get(loadPath)?.let {
      return it
    }
    declaringModuleOf(loadPath)?.let { declaring ->
      descriptorFiles(module = declaring, loadPath = loadPath).firstOrNull()?.let {
        return it
      }
    }
    for (member in searchModules) {
      descriptorFiles(module = member, loadPath = loadPath).firstOrNull()?.let {
        return it
      }
    }
    return null
  }

  /** Adds the modules [moduleNames] declares to the probe set, and answers whether the set grew. */
  fun learnMembers(moduleNames: Collection<String>): Boolean {
    val before = searchModules.size
    for (name in moduleNames) {
      findModule(name.substringBeforeLast('/'))?.let { searchModules.add(it) }
    }
    return searchModules.size > before
  }

  /** The module a module-descriptor load path names, by the longest dotted prefix that is a module of this project. */
  private fun declaringModuleOf(loadPath: String): JpsModule? {
    if (!loadPath.endsWith(DESCRIPTOR_FILE_SUFFIX)) {
      return null
    }
    var name = loadPath.removeSuffix(DESCRIPTOR_FILE_SUFFIX)
    while (name.isNotEmpty()) {
      findModule(name)?.let {
        return it
      }
      name = name.substringBeforeLast('.', missingDelimiterValue = "")
    }
    return null
  }
}

private const val DESCRIPTOR_FILE_SUFFIX: String = ".xml"
