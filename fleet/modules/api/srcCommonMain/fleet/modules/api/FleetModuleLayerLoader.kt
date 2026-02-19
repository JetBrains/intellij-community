package fleet.modules.api

fun interface FleetModuleLayerLoader {
  fun moduleLayer(parentLayers: List<FleetModuleLayer>, modulePath: Set<FleetModuleInfo>): FleetModuleLayer
}