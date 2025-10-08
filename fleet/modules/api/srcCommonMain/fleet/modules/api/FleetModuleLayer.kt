package fleet.modules.api

import kotlin.reflect.KClass

interface FleetModuleLayer {
  val modules: Set<FleetModule>

  fun findModule(name: String): FleetModule?

  fun <T: Any> findServices(service: KClass<T>, requestor: KClass<*>): Iterable<T>
}