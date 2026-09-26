package fleet.codecache

import fleet.bundles.Coordinates
import fleet.bundles.ResolutionException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

private const val MAX_LOCK_WAIT_MS = 60000L
private const val LOCK_DELAY_MS = 100L

data class CodeCachePath(val path: Path, val writable: Boolean)

class CodeCache(
  private val paths: List<CodeCachePath>,
  private val lockDelay: Long = LOCK_DELAY_MS,
  private val maxLockWaitTime: Long = MAX_LOCK_WAIT_MS,
  private val queryParams: (suspend () -> Map<String, String>)? = null
) {
  suspend fun resolve(coord: Coordinates): String {
    return when (coord) {
      is Coordinates.Local -> {
        Path(coord.path).takeIf { SystemFileSystem.exists(it) } ?: throw ResolutionException(coord)
      }
      is Coordinates.Remote -> {
        val relativePath = coord.relativePathToCodeCache()
        val resolved = paths.map { Path(it.path, relativePath) }.firstOrNull { SystemFileSystem.exists(it) }
        if (resolved != null) {
          resolved
        }
        else {
          throw ResolutionException(coord)
        }
      }
    }.toString()
  }
}
