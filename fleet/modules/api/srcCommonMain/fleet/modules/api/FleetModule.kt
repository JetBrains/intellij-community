package fleet.modules.api

import kotlin.reflect.KClass

interface FleetModule {
  val name: String
  val layer: FleetModuleLayer

  fun getResource(path: String): ByteArray?

  fun <T : Any> findServices(service: KClass<T>, requestor: KClass<*>): Iterable<T>
}

