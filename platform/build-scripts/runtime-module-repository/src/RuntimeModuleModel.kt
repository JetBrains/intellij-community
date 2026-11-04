// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.runtimeModuleRepository

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap

private const val FACTS_FORMAT_VERSION = 1

private val factsJson = Json {
  encodeDefaults = false
  explicitNulls = false
  prettyPrint = true
  prettyPrintIndent = "  "
}

/**
 * The facts of the project model that the runtime module repository reads. [JpsRuntimeModuleModel] takes them from a JPS project, and
 * [RuntimeModuleFactsTable] from a facts file.
 */
@ApiStatus.Internal
interface RuntimeModuleModel {
  /** Returns the facts of the module [name], or `null` if the project has no such module. */
  fun findModule(name: String): RuntimeModuleFacts?

  /** Returns `true` if the project has the project-level library [name]. */
  fun hasProjectLibrary(name: String): Boolean
}

/**
 * A module or a project-level library of the project model, by name.
 */
internal data class RuntimeModelElement(val kind: Kind, val name: String) {
  enum class Kind { MODULE, PROJECT_LIBRARY }

  override fun toString(): String = when (kind) {
    Kind.MODULE -> "module $name"
    Kind.PROJECT_LIBRARY -> "library $name"
  }
}

internal fun moduleElement(name: String): RuntimeModelElement = RuntimeModelElement(RuntimeModelElement.Kind.MODULE, name)

internal fun projectLibraryElement(name: String): RuntimeModelElement = RuntimeModelElement(RuntimeModelElement.Kind.PROJECT_LIBRARY, name)

/**
 * The facts of one module.
 */
@ApiStatus.Internal
@Serializable
class RuntimeModuleFacts(
  /** `true` if the module has source roots, and each of them is a test root. */
  @JvmField val testOnly: Boolean = false,
  /**
   * The direct production runtime dependencies on modules and on project-level libraries, in the order of the module. The order is in the bytes of
   * the repository, so no reader sorts the list.
   */
  @JvmField val dependencies: List<RuntimeModuleDependency> = emptyList(),
)

/**
 * A dependency on a module or on a project-level library. Exactly one of [module] and [library] is set.
 */
@ApiStatus.Internal
@Serializable
class RuntimeModuleDependency(
  @JvmField val module: String? = null,
  /** `true` if the production roots of [module] hold its `<name>.xml` descriptor. */
  @JvmField val contentModule: Boolean = false,
  @JvmField val library: String? = null,
) {
  init {
    require((module == null) != (library == null)) { "A dependency names exactly one of a module and a library: module=$module, library=$library" }
    require(module != null || !contentModule) { "The dependency on the library $library cannot be a content module" }
  }
}

/**
 * The facts file: the facts of each module by name, and the names of the project-level libraries. The writer sorts both, so equal facts give equal
 * bytes.
 */
@OptIn(ExperimentalSerializationApi::class)
@ApiStatus.Internal
@Serializable
class RuntimeModuleFactsTable(
  @EncodeDefault @JvmField val version: Int = FACTS_FORMAT_VERSION,
  @JvmField val modules: Map<String, RuntimeModuleFacts>,
  @JvmField val projectLibraries: List<String>,
) : RuntimeModuleModel {
  @Transient
  private val projectLibrarySet = HashSet(projectLibraries)

  override fun findModule(name: String): RuntimeModuleFacts? = modules.get(name)

  override fun hasProjectLibrary(name: String): Boolean = name in projectLibrarySet
}

@ApiStatus.Internal
fun readRuntimeModuleFacts(file: Path): RuntimeModuleFactsTable {
  val table = factsJson.decodeFromString<RuntimeModuleFactsTable>(Files.readString(file))
  require(table.version == FACTS_FORMAT_VERSION) {
    "$file has the facts format version ${table.version}, but $FACTS_FORMAT_VERSION is expected"
  }
  return table
}

@ApiStatus.Internal
fun writeRuntimeModuleFacts(table: RuntimeModuleFactsTable, file: Path) {
  val sorted = RuntimeModuleFactsTable(
    version = table.version,
    modules = TreeMap(table.modules),
    projectLibraries = table.projectLibraries.sorted(),
  )
  Files.writeString(file, factsJson.encodeToString(sorted) + "\n")
}

/**
 * Reads the facts from a JPS project, with the same calls that the generator made before it had [RuntimeModuleModel].
 */
@ApiStatus.Internal
class JpsRuntimeModuleModel(private val project: JpsProject) : RuntimeModuleModel {
  private val facts = HashMap<JpsModule, RuntimeModuleFacts>()
  private val isContentModule = HashMap<JpsModule, Boolean>()

  override fun findModule(name: String): RuntimeModuleFacts? {
    val module = project.findModuleByName(name) ?: return null
    return facts.getOrPut(module) { readFacts(module) }
  }

  override fun hasProjectLibrary(name: String): Boolean = project.libraryCollection.findLibrary(name) != null

  /** Returns the facts of every module and the names of every project-level library. */
  fun toFactsTable(): RuntimeModuleFactsTable {
    val modules = TreeMap<String, RuntimeModuleFacts>()
    for (module in project.modules) {
      modules.put(module.name, findModule(module.name)!!)
    }
    return RuntimeModuleFactsTable(modules = modules, projectLibraries = project.libraryCollection.libraries.map { it.name }.sorted())
  }

  private fun readFacts(module: JpsModule): RuntimeModuleFacts {
    val dependencies = ArrayList<RuntimeModuleDependency>()
    JpsJavaExtensionService.dependencies(module).withoutSdk().withoutModuleSourceEntries().runtimeOnly().productionOnly().forEachModuleAndLibrary(
      { dependency ->
        dependencies.add(RuntimeModuleDependency(module = dependency.name, contentModule = isContentModule(dependency)))
      },
      { library ->
        if (library.isProjectLevel) {
          dependencies.add(RuntimeModuleDependency(library = library.name))
        }
      },
    )
    val sourceRoots = module.sourceRoots
    return RuntimeModuleFacts(testOnly = sourceRoots.isNotEmpty() && sourceRoots.all { it.rootType.isForTests }, dependencies = dependencies)
  }

  private fun isContentModule(module: JpsModule): Boolean {
    return isContentModule.getOrPut(module) {
      /* we have a few modules that contain the module XML descriptor but aren't registered as content modules (IJPL-210868), but given that the
         generator uses this flag only for dependencies on modules outside the distribution, this should not lead to problems */
      JpsJavaExtensionService.getInstance().findSourceFileInProductionRoots(module, "${module.name}.xml") != null
    }
  }
}

private val JpsLibrary.isProjectLevel: Boolean
  get() = (this as JpsElementBase<*>).parent.parent is JpsProject
