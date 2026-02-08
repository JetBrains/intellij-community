package fleet.modules.jvm

import fleet.modules.api.FleetModule
import fleet.modules.api.FleetModuleLayer
import java.util.ServiceLoader
import kotlin.reflect.KClass

data class JvmFleetModuleLayer(val layer: ModuleLayer) : FleetModuleLayer {
  companion object {
    private val loaderConstructor by lazy {
      ServiceLoader::class.java.getDeclaredConstructor(Class::class.java, ModuleLayer::class.java, Class::class.java).also {
        it.isAccessible = true
      }
    }

    internal fun <T : Any> findServices(layer: ModuleLayer,
                                        service: KClass<T>,
                                        requestor: KClass<*>): ServiceLoader<T> {
      return loaderConstructor.newInstance(requestor.java, layer, service.java) as ServiceLoader<T>
    }
  }

  override fun findModule(name: String): FleetModule? {
    return layer
      .findModule(name)
      .map(::JvmFleetModule)
      .orElse(null)
  }

  override fun <T : Any> findServices(service: KClass<T>, requestor: KClass<*>): Iterable<T> {
    return findServices(layer, service, requestor)
  }

  override val modules: Set<FleetModule>
    get() = layer.modules().map(::JvmFleetModule).toSet()

}
