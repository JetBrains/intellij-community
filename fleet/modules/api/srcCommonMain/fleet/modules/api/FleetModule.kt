package fleet.modules.api

import kotlin.reflect.KClass

interface FleetModule {
  val name: String
  val layer: FleetModuleLayer

  @Deprecated("Get rid of it as soon as we drop entities auto-registration")
  fun getEntityTypeProvider(providerName: String): Any?

  fun getResource(path: String): ByteArray?

  fun <T : Any> findServices(service: KClass<T>, requestor: KClass<*>): Iterable<T>
}

