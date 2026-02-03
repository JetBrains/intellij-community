package fleet.modules.jvm

import fleet.modules.api.FleetModuleInfo
import fleet.modules.api.FleetModuleLayer
import fleet.modules.api.FleetModuleLayerLoader
import fleet.util.logging.KLoggers
import fleet.util.modules.FleetModuleFinderLogger
import fleet.util.modules.ModuleInfo
import fleet.util.modules.ModuleLayers
import fleet.util.modules.ModuleLayers.deserializeModuleDescriptor
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier


private val logger by lazy { KLoggers.logger(JvmFleetModuleLayerLoader::class) }

/**
 * Returns java module layer for a module path and a list of parents
 */
object JvmFleetModuleLayerLoader {
  private val moduleFinderLogger = object : FleetModuleFinderLogger {
    override fun warn(message: Supplier<String>) {
      logger.warn(message.get())
    }

    override fun error(t: Throwable?, message: Supplier<String>) {
      logger.error(t, message.get())
    }
  }

  fun jvmModulePath(modulePath: Set<FleetModuleInfo>): Collection<ModuleInfo> {
    return modulePath.map { moduleInfo ->
      when (moduleInfo) {
        is FleetModuleInfo.Path -> ModuleInfo.Path(moduleInfo.path)
        is FleetModuleInfo.WithDescriptor -> {
          runCatching {
            val jvmDescriptor = deserializeModuleDescriptor(moduleInfo.serializedModuleDescriptor)
            ModuleInfo.WithDescriptor(jvmDescriptor, moduleInfo.path)
          }.getOrElse { t ->
            logger.warn(t) { "Cannot deserialize module descriptor $moduleInfo" }
            ModuleInfo.Path(moduleInfo.path)
          }
        }
      }
    }
  }

  fun production(): FleetModuleLayerLoader = FleetModuleLayerLoader { parentLayers, modulePath ->
    val jvmParentLayers = parentLayers.map { (it as JvmFleetModuleLayer).layer }
    val jvmModulePath = jvmModulePath(modulePath)
    JvmFleetModuleLayer(ModuleLayers.moduleLayer(jvmParentLayers, jvmModulePath, moduleFinderLogger))
  }.memoizing()

  fun test(modulePath: List<ModuleInfo>): FleetModuleLayerLoader {
    val layer = ModuleLayers.moduleLayer(emptyList(), modulePath, moduleFinderLogger) // TODO: this operation is very slow, we need to investigate it
    val testModuleLayer = TestJvmFleetModuleLayer(layer, modulePath) // share heavy calculations in `TestJvmFleetModuleLayer` amongst all caller of the loader
    return FleetModuleLayerLoader { _, _ -> testModuleLayer }
  }

  private fun FleetModuleLayerLoader.memoizing(): FleetModuleLayerLoader {
    data class Key(@JvmField val parents: List<FleetModuleLayer>, @JvmField val modulePath: Set<FleetModuleInfo>)

    val layerByModuleInfo = ConcurrentHashMap<Key, FleetModuleLayer>()
    return FleetModuleLayerLoader { parentLayers, modulePath ->
      layerByModuleInfo.computeIfAbsent(Key(parentLayers, modulePath)) {
        moduleLayer(parentLayers, modulePath)
      }
    }
  }

}