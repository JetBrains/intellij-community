package fleet.modules.api

sealed interface FleetModuleInfo {
  data class WithDescriptor(val serializedModuleDescriptor: String, val path: String) : FleetModuleInfo
  data class Path(val path: String) : FleetModuleInfo
}