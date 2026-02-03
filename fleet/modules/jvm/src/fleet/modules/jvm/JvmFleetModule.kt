package fleet.modules.jvm

import fleet.modules.api.FleetModule
import fleet.modules.api.FleetModuleLayer
import java.util.ServiceLoader
import kotlin.reflect.KClass
import kotlin.streams.asSequence

data class JvmFleetModule(val module: Module) : FleetModule {
  override val name: String
    get() = module.name

  override val layer: FleetModuleLayer
    get() = JvmFleetModuleLayer(module.getLayer())

  override fun getEntityTypeProvider(providerName: String): Any? {
    val providerClass = module.classLoader.loadClass(providerName)
    return providerClass.getField("INSTANCE").get(null)
  }

  override fun getResource(path: String): ByteArray? {
    return module.getResourceAsStream(path)?.readBytes()
  }

  override fun <T : Any> findServices(service: KClass<T>, requestor: KClass<*>): Iterable<T> {
    val moduleLayer = module.layer
    return JvmFleetModuleLayer.findServices(moduleLayer, service, requestor).stream().asSequence().takeWhile {
      it.type().module.layer == moduleLayer
    }.filter {
      it.type().module == module
    }.map(ServiceLoader.Provider<T>::get).asIterable()
  }
}
