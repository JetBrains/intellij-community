package fleet.codecache

import fleet.bundles.Coordinates
import io.ktor.http.decodeURLQueryComponent

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.commander.workspace"])
fun Coordinates.relativePathToCodeCache(): String {
  return when (this) {
    is Coordinates.Remote -> {
      // todo hacky, to be reconsidered
      val moduleDividedParts = url.split("&module=")
      val fileName = when (moduleDividedParts.size) {
        1 -> url.substringAfterLast('/') // for "$host/fleet-parts/*" we don't use the new endpoint?
        else -> moduleDividedParts.last()
      }.decodeURLQueryComponent(plusIsSpace = false)
      "$hash/$fileName"
    }
    //is Coordinates.Workspace -> return "$filename#$sha2"
    else -> throw RuntimeException("Unsupported coordinates: $this")
  }
}
