@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package fleet.codecache

import fleet.bundles.LayerSelector
import fleet.bundles.ResolvedPluginLayer
import fleet.bundles.ResourceBundle
import fleet.bundles.dockLayer
import fleet.bundles.internalReadability
import fleet.modules.api.FleetModule
import fleet.modules.api.FleetModuleInfo
import fleet.modules.api.FleetModuleLayer
import fleet.modules.api.FleetModuleLayerLoader
import fleet.util.logging.KLoggers.logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private val logger by lazy { logger(ResolvedPluginLayer::class) }

suspend fun loadPluginModulesAndResources(
  moduleLayerLoader: FleetModuleLayerLoader,
  resolvedLayers: Map<LayerSelector, ResolvedPluginLayer>,
  sortedSelectors: List<LayerSelector>,
  externalDependencies: Map<LayerSelector, List<FleetModuleLayer>>,
  baseLayers: List<FleetModuleLayer>,
  loadDockModuleLayer: (modulePath: Set<FleetModuleInfo>) -> FleetModuleLayer,
): Map<LayerSelector, PluginModulesAndResources> {
  val result = HashMap<LayerSelector, PluginModulesAndResources>(sortedSelectors.size)
  for (selector in sortedSelectors) {
    val resolvedLayer = resolvedLayers.get(selector) ?: continue
    val moduleLayer = when (selector) {
      dockLayer -> loadDockModuleLayer(moduleInfos(resolvedLayer))
      else -> {
        val internal = internalReadability[selector]?.mapNotNull { dependency ->
          result[dependency]?.layer
        } ?: emptyList()
        val external = externalDependencies[selector] ?: emptyList()
        moduleLayerLoader.moduleLayer(modulePath = moduleInfos(resolvedLayer), parentLayers = baseLayers + external + internal)
      }
    }
    val modules = coroutineScope {
      resolvedLayer.modules.map { moduleName ->
        async { moduleLayer.findModule (moduleName) }
      }.awaitAll().filterNotNull()
    }
    result.put(selector, PluginModulesAndResources(layer = moduleLayer, modules = modules, resources = resolvedLayer.resources))
  }
  return result
}

fun moduleInfos(resolvedPluginLayer: ResolvedPluginLayer): Set<FleetModuleInfo> {
  return resolvedPluginLayer.modulePath.mapTo(HashSet(resolvedPluginLayer.modulePath.size)) { path ->
    val filePath = path.path
    when (val second = path.serializedModuleDescriptor) {
      null -> FleetModuleInfo.Path(filePath)
      else ->
        resolvedPluginLayer.runCatching {
          FleetModuleInfo.WithDescriptor(serializedModuleDescriptor = second, path = filePath)
        }.getOrElse { message ->
          logger.warn("$message: Cannot deserialize descriptor for ${resolvedPluginLayer}")
          FleetModuleInfo.Path(filePath)
        }
    }
  }
}

data class PluginModulesAndResources(val modules: List<FleetModule>, val layer: FleetModuleLayer, val resources: Set<ResourceBundle>)