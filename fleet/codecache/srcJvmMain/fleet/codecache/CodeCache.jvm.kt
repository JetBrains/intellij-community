package fleet.codecache

import fleet.bundles.Coordinates
import fleet.bundles.ResolutionException
import fleet.util.logging.KLoggers.logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.VisibleForTesting
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.text.DecimalFormat
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.outputStream
import kotlin.math.log10
import kotlin.math.pow

private const val MAX_LOCK_WAIT_MS = 60000L
private const val LOCK_DELAY_MS = 100L

data class CodeCachePath(val path: Path, val writable: Boolean)

class CodeCache(
  private val httpClientFn: suspend () -> HttpClient,
  private val paths: List<CodeCachePath>,
  private val lockDelay: Long = LOCK_DELAY_MS,
  private val maxLockWaitTime: Long = MAX_LOCK_WAIT_MS,
  private val queryParams: (suspend () -> Map<String, String>)? = null
) {
  private val hasher: CodeCacheHasher = CodeCacheHasher()

  companion object {
    private val logger = logger(CodeCache::class)
  }

  suspend fun resolve(coord: Coordinates): String {
    return when (coord) {
      is Coordinates.Local -> {
        Path.of(coord.path).takeIf { it.exists() } ?: throw ResolutionException(coord)
      }
      is Coordinates.Remote -> {
        val relativePath = coord.relativePathToCodeCache()
        val resolved = paths.map { it.path.resolve(relativePath) }.firstOrNull { it.exists() }
        if (resolved != null) {
          resolved
        }
        else {
          val writableCachePath = paths.firstOrNull { it.writable }?.path ?: error("No writable code cache is provided")
          val targetFile = writableCachePath.resolve(relativePath)
          val tmpFile = writableCachePath.resolve("tmp").resolve(relativePath)
          if (downloadWithLock(targetFile, tmpFile, coord, queryParams?.invoke() ?: emptyMap())) {
            targetFile
          }
          else {
            throw ResolutionException(coord)
          }
        }
      }
    }.absolutePathString()
  }

  private suspend fun downloadWithLock(targetFile: Path,
                                       tmpFile: Path,
                                       coord: Coordinates.Remote,
                                       queryParams: Map<String, String>): Boolean {
    logger.debug("Downloading $coord to $targetFile")

    ensureDirExists(targetFile.parent)
    ensureDirExists(tmpFile.parent)

    return withFileLock(tmpFile.parent, tmpFile.name, coord) {
      if (!targetFile.exists()) {
        httpClientFn().downloadFile(coord.url, tmpFile, queryParams)
        val actualHash = hash(tmpFile)
        val hashesMatch = actualHash == coord.hash
        if (hashesMatch) {
          Files.move(tmpFile, targetFile) // due to compatibility with the Gradle plugin and kotlin 1.6
        }
        else {
          logger.error(Throwable("Hash mismatch")) {
            val fileSize = tmpFile.fileSize()
            val buffer = CharArray(1024 * 4)
            runInterruptible {
              kotlin.runCatching {
                Files.newBufferedReader(tmpFile, StandardCharsets.ISO_8859_1)
                  .use { reader -> reader.read(buffer) }
              }.onFailure {
                logger.error(it) {
                  "empty message"
                }
              }
            }
            "Hash mismatch (${coord.url}). Expected: ${coord.hash}, actual: $actualHash, " +
            "downloaded file size: ${formatFileSize(fileSize)}, target: ${targetFile.absolute()}, tmp: ${tmpFile.absolute()}, " +
            "content: `${buffer.concatToString()}`"
          }
          Files.deleteIfExists(tmpFile) // due to compatibility with the Gradle plugin and kotlin 1.6
        }
        hashesMatch
      }
      else {
        true
      }
    }
  }

  private fun ensureDirExists(dir: Path) {
    try {
      Files.createDirectories(dir)
    }
    catch (e: FileAlreadyExistsException) {
      if (dir.isDirectory()) {
        // it was a symlink
      }
      else {
        throw e
      }
    }
    catch (e: Exception) {
      throw RuntimeException("Couldn't create cache directory: $dir", e)
    }
  }

  @VisibleForTesting
  suspend fun <T> withFileLock(folder: Path, filename: String, coord: Coordinates, body: suspend CoroutineScope.() -> T): T {
    val lockFile = folder.resolve("$filename.lock")
    fun tryLock(lockFile: Path): Boolean = try {
      Files.createFile(lockFile) // due to compatibility with the Gradle plugin and kotlin 1.6
      true
    }
    catch (_: FileAlreadyExistsException) {
      false
    }

    try {
      val success = withTimeoutOrNull(maxLockWaitTime) {
        while (!tryLock(lockFile)) {
          delay(lockDelay)
        }
        true
      }

      if (success == null) {
        throw ResolutionException(coord, Throwable(
          "Waited on a lock for more than $maxLockWaitTime ms. Please remove the lock file by hand: $lockFile"))
      }

      return coroutineScope(body)
    }
    finally {
      Files.deleteIfExists(lockFile) // due to compatibility with the Gradle plugin and kotlin 1.6
    }
  }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.commander.workspace"])
  fun hash(path: Path): String = hasher.hash(path)
}

class CodeCacheHasher(hashAlgorithm: String = Coordinates.Remote.HASH_ALGORITHM) {
  private val digestToClone = MessageDigest.getInstance(hashAlgorithm)

  fun hash(path: Path): String {
    val buffer = ByteArray(1024 * 1024)
    val digest = digestToClone.clone() as MessageDigest
    digest.reset()
    path.inputStream().buffered().use {
      while (true) {
        val read = it.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }

    val shaBytes = digest.digest()
    return buildString {
      shaBytes.forEach { byte -> append(String.format("%1$02x", byte)) }
    }
  }
}

// due to compatibility with the Gradle plugin and kotlin 1.6
private fun Path.exists() = toFile().exists()
private fun Path.inputStream() = toFile().inputStream()
private fun Path.absolute() = toFile().isAbsolute
private val Path.name: String
  get() {
    return toFile().name
  }

private fun Path.fileSize() = Files.size(this)

private suspend fun HttpClient.downloadFile(url: String, destination: Path, queryParams: Map<String, String>): Path {
  prepareGet(url) {
    url {
      queryParams.forEach {
        parameters.append(it.key, it.value)
      }
    }
    expectSuccess = false
  }.execute { response ->
    if (!response.status.isSuccess()) {
      withContext(Dispatchers.IO) { Files.deleteIfExists(destination) }
      error("Couldn't download $url. Status code: ${response.status}. Body: ${response.bodyAsText()}")
    }

    val channel: ByteReadChannel = response.body()
    withContext(Dispatchers.IO) {
      destination.outputStream(StandardOpenOption.CREATE).buffered().use { fos ->
        channel.copyTo(fos)
      }
    }
  }

  return destination
}

private fun formatFileSize(fileSize: Long): String {
  if (fileSize == 0L) return "0 B"
  val rank = ((log10(fileSize.toDouble()) + 0.0000021714778384307465) / 3).toInt()
  val value = fileSize / 1000.0.pow(rank.toDouble())
  val units = arrayOf("B", "kB", "MB", "GB", "TB", "PB", "EB")
  return DecimalFormat("0.##").format(value) + units[rank]
}

suspend fun codeCacheParams(machineId: Deferred<String>?, build: String?): Map<String, String> {
  return buildMap {
    machineId?.await()?.let { put("uuid", it) }
    build?.let { put("build", it) }
  }
}