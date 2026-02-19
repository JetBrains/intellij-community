package fleet.bundles

private fun PluginLayer.intersection(other: PluginLayer?): PluginLayer? =
  when {
    other == null -> null
    else -> PluginLayer(modulePath = modulePath.intersect(other.modulePath),
                        modules = modules.intersect(other.modules),
                        resources = resources.intersect(other.resources))
  }

private fun PluginLayer.merge(other: PluginLayer?): PluginLayer =
  other?.let {
    PluginLayer(modulePath = modulePath.union(other.modulePath),
                modules = modules.union(other.modules),
                resources = resources.union(other.resources))
  } ?: this


private fun PluginLayer.subtract(other: PluginLayer?): PluginLayer =
  when {
    other == null -> this
    else -> PluginLayer(modulePath = modulePath.subtract(other.modulePath),
                        modules = modules.subtract(other.modules),
                        resources = resources.subtract(other.resources))
  }

fun PluginParts.eliminateIntersections(): PluginParts =
  eliminateIntersectionsImpl(frontendLayer,
                             workspaceLayer,
                             commonLayer)
    .eliminateIntersectionsImpl(frontendImplLayer,
                                workspaceImplLayer,
                                commonImplLayer)
    .eliminateIntersectionsImpl(frontendApiLayer,
                                workspaceApiLayer,
                                commonApiLayer)

private fun PluginParts.eliminateIntersectionsImpl(frontendS: LayerSelector,
                                                   workspaceS: LayerSelector,
                                                   commonS: LayerSelector): PluginParts {
  val common = layers[frontendS]?.intersection(layers[workspaceS])
  val pairs = listOf(commonS to (layers[commonS]?.merge(common) ?: common),
                     frontendS to layers[frontendS]?.subtract(common),
                     workspaceS to layers[workspaceS]?.subtract(common))
  return copy(layers = layers.toMutableMap().apply {
    pairs.forEach { (selector, layer) ->
      when {
        layer != null -> put(selector, layer)
        else -> remove(selector)
      }
    }
  })
}
