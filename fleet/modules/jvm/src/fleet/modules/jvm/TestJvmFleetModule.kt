package fleet.modules.jvm

import fleet.modules.api.FleetModule
import fleet.modules.api.FleetModuleLayer
import fleet.util.logging.KLoggers
import fleet.util.modules.ModuleInfo
import java.io.InputStream
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.reflect.KClass

private val logger by lazy { KLoggers.logger(TestJvmFleetModule::class) }

data class TestJvmFleetModule(
  private val moduleName: String,
  private val moduleLayer: FleetModuleLayer,
  private val moduleInfo: ModuleInfo? = null,
) : FleetModule {

  /**
   * The unnamed module of the system classloader.
   *
   * In tests, we operate with `--classpath`, both in Gradle and especially in IDE's gutter run where we do not control the `java` call.
   * So, all our classes are loaded in the AppClassloader and so [fleet.testlib.core.TestJvmFleetModule] delegates to it in the relevant places.
   */
  private val classpathUniqueModule: Module
    get() = ClassLoader.getSystemClassLoader().unnamedModule

  override val name: String
    get() = moduleName

  override val layer: FleetModuleLayer
    get() = moduleLayer

  @Deprecated("Get rid of it as soon as we drop entities auto-registration")
  override fun getEntityTypeProvider(providerName: String): Any? {
    val providerClass = classpathUniqueModule.classLoader.loadClass(providerName)
    return providerClass.getField("INSTANCE").get(null)
  }

  override fun getResource(path: String): ByteArray? =
    when (val codeLocation = moduleInfo?.codeLocation()) {
      null -> null
      is CodeLocation.Directory -> Path(codeLocation.path).resolve(path).takeIf { it.exists() }?.readBytes()
      is CodeLocation.Jar -> JarFile(codeLocation.path).use { jar -> jar.readBytesOfJarEntry(path) }
    }

  // caches provided services resolving, could be slow when it involves reading from a file from the jar
  private val providedServices: Map<String, List<String>> by lazy {
    moduleInfo?.providedServices() ?: emptyMap()
  }

  override fun <T : Any> findServices(service: KClass<T>, requestor: KClass<*>): Iterable<T> =
    when (moduleInfo) {
      null -> {
        logger.warn("Trying to find implementation for service '${service.qualifiedName}' in module '${name}' but that module had no module info")
        emptyList()
      }
      else -> providedServices[service.qualifiedName]?.map { loadService(it) } ?: emptyList()
    }

  // we need to load the class from `classpathUniqueModule.classLoader`, so using ServiceLoader and an ephemeral classloader here would be redundant
  private fun <T> loadService(serviceClass: String): T =
    classpathUniqueModule.classLoader.loadClass(serviceClass).getDeclaredConstructor().newInstance() as T
}

private fun JarFile.readBytesOfJarEntry(path: String): ByteArray? = when (val resourceFile = getJarEntry(path)) {
  null -> null
  else -> getInputStream(resourceFile).use { it.readBytes() }
}

private fun ModuleInfo.providedServices(): Map<String, List<String>> = when (this) {
  is ModuleInfo.Path -> codeLocation().readServices()
  is ModuleInfo.WithDescriptor -> descriptor.provides().associate { it.service() to it.providers() }
}

private fun ModuleInfo.codeLocation(): CodeLocation {
  val ppath = when (this) {
    is ModuleInfo.Path -> path
    is ModuleInfo.WithDescriptor -> path
  }
  return when {
    Path(ppath).isDirectory() -> CodeLocation.Directory(ppath)
    ppath.endsWith(".jar") -> CodeLocation.Jar(ppath)
    else -> error("Unsupported code location: $ppath, must be a directory or a jar file")
  }
}

private sealed class CodeLocation(val path: String) {
  class Jar(path: String) : CodeLocation(path)
  class Directory(path: String) : CodeLocation(path)
}

/**
 * Manually reads services provided by a JAR or a compialtion output directory containing META-INF/
 */
private fun CodeLocation.readServices(): Map<String, List<String>> {
  val servicesDirectory = "META-INF/services/"
  return when (this) {
    is CodeLocation.Directory -> Path(path).resolve(servicesDirectory).takeIf { it.exists() }?.listDirectoryEntries()?.associate { entry ->
      entry.fileName.toString() to entry.inputStream().readServiceImplementations()
    } ?: emptyMap()
    is CodeLocation.Jar -> JarFile(path).use { jar ->
      jar.entries().asSequence().filter { entry ->
        !entry.isDirectory && entry.name.startsWith(servicesDirectory) && entry.name.length > servicesDirectory.length
      }.associate { entry ->
        entry.name.removePrefix(servicesDirectory) to jar.getInputStream(entry).readServiceImplementations()
      }
    }
  }
}

private fun InputStream.readServiceImplementations(): List<String> = bufferedReader().useLines { lines ->
  lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
}.toList()

