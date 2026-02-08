package fleet.modules.jvm

import fleet.modules.api.FleetModule
import fleet.modules.api.FleetModuleLayer
import fleet.util.modules.ModuleInfo
import java.lang.module.ModuleFinder
import kotlin.io.path.Path
import kotlin.reflect.KClass

/**
 * The module layer used in Fleet's test runner.
 * Fleet's runtime is modularized to ensure isolation between Dock, SHIP and plugins, however Fleet modules/jars are not modularized (do not
 * have `module-info.class`).
 * This module layer abstraction bridges between the non-modularized classpath used in tests, and the modularized application definitions
 * (plugin descriptors, init module descriptors).
 *
 * The provided [layer] is only used to construct a set of [TestJvmFleetModule], and not as a backing module layer for loading services,
 * resources, etc.
 * Indeed, packages of modules in that module layer are already loaded in the SystemClassLoader's unnamed module.
 * Even if we created a module layer backed by this class loader, it would for example fail with
 * `Package kotlinx/coroutines/test for module kotlinx.coroutines.test is already in the unnamed module defined to the class loader`.
 * So instead we implement the delegation ourselves as part of the [TestJvmFleetModule]'s abstraction.
 *
 * @param layer the module layer containing every test modules and Fleet runtime modules
 * @param modulePath the module path of the provided layer
 */
class TestJvmFleetModuleLayer(
  val layer: ModuleLayer,
  private val modulePath: List<ModuleInfo>,
) : FleetModuleLayer {
  private val moduleInfosByName: Map<String?, ModuleInfo> by lazy {
    modulePath.mapNotNull { it.name()?.let { name -> name to it } }.toMap() // TODO: check duplicates
  }

  private val moduleByName: Map<String, TestJvmFleetModule> by lazy {
    layer.modules().mapNotNull {
      TestJvmFleetModule(
        moduleName = it.name,
        moduleLayer = this,
        moduleInfo = moduleInfosByName[it.name],
      )
    }.toSet().associateBy { it.name }
  }

  private val cachedModules by lazy {
    moduleByName.values.toSet()
  }

  override val modules: Set<FleetModule>
    get() = cachedModules

  override fun findModule(name: String): FleetModule? = moduleByName[name]

  override fun <T : Any> findServices(service: KClass<T>, requestor: KClass<*>): Iterable<T> =
    modules.flatMap { it.findServices(service, requestor) } // TODO: could we do better in terms of performance here?
}

private fun ModuleInfo.name(): String? = when (this) {
  is ModuleInfo.Path -> ModuleFinder.of(Path(path)).findAll().singleOrNull()?.descriptor()?.name()
  is ModuleInfo.WithDescriptor -> descriptor.name()
}
