package fleet.buildtool.http

import fleet.buildtool.fs.sha256
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import io.ktor.client.plugins.logging.Logger as KtorLogger

fun fleetBuildHttpClient(logger: Logger, userAgent: String = "FleetBuildTooling/1.0"): HttpClient = HttpClient {
  expectSuccess = true
  followRedirects = true
  install(HttpTimeout) {
    requestTimeoutMillis = Duration.ofMinutes(5).toMillis()
  }
  install(HttpRequestRetry) {
    retryOnServerErrors(maxRetries = 10)
    exponentialDelay()
  }
  install(Logging) {
    this.logger = object : KtorLogger {
      override fun log(message: String) {
        logger.info(message)
      }
    }
    level = LogLevel.ALL
    sanitizeHeader { header -> header == HttpHeaders.Authorization }
  }
  defaultRequest {
    header("User-Agent", userAgent)
  }
}

fun HttpClient.downloadFileBlocking(url: String, destination: Path, checksumValidation: ChecksumValidation = ChecksumValidation.Disabled, block: HttpRequestBuilder.() -> Unit = {}): Path = runBlocking {
  downloadFile(url, destination, checksumValidation, block)
}

sealed class ChecksumValidation {
  /**
   * Download the file without checking its integrity once on disk.
   *
   * Always re-downloads the file, regardless of whether the target file is already present on disk.
   */
  object Disabled : ChecksumValidation()

  /**
   * Checks integrity of the downloaded file.
   *
   * Checks whether the target file is already on disk and matching the provided [expected] hash and if the hash matches the file won't be re-downloaded.
   *
   * @param algorithm of the [expected] hash string
   * @param expected the expected hash of the downloaded file
   */
  data class UsingHash(val algorithm: ChecksumAlgorithm, val expected: String) : ChecksumValidation()

  /**
   * Checks integrity of the downloaded file by downloading first the file pointed by [url] which should contain the hash of the downloaded
   * file.
   *
   * Checks whether the target file is already on disk and matching the hash contained in the file downloaded from [url] and if the hash
   * matches the file won't be re-downloaded.
   *
   * @param algorithm of the hash present in the file downloaded from [url]
   * @param url url to a remote checksum file to download
   */
  data class UsingHashDownloadedFile(val algorithm: ChecksumAlgorithm, val url: String) : ChecksumValidation()
}

enum class ChecksumAlgorithm {
  SHA256,
}

suspend fun HttpClient.downloadFile(url: String, destination: Path, checksumValidation: ChecksumValidation, block: HttpRequestBuilder.() -> Unit = {}): Path {
  return when (val hashResult = checkIfAlreadyDownloaded(destination, checksumValidation)) {
    is HashResult.AlreadyDownloaded -> destination
    HashResult.NotDownloaded, is HashResult.HashMismatched -> {
      config {
        install(Logging) {
          level = LogLevel.HEADERS // a bug in Ktor Logging plugin leads to OOM if downloaded file is too big
        }
      }.prepareGet(url) {
        expectSuccess = false
        block()
      }.execute { response ->
        if (!response.status.isSuccess()) {
          withContext(Dispatchers.IO) { Files.deleteIfExists(destination) }
          error("failed to download $url: ${response.status} ${response.bodyAsText()}")
        }

        val channel: ByteReadChannel = response.body()
        withContext(Dispatchers.IO) {
          destination.parent.createDirectories()
          destination.outputStream(StandardOpenOption.CREATE).buffered().use { fos ->
            channel.copyTo(fos)
          }
        }
      }

      when (checksumValidation) {
        ChecksumValidation.Disabled -> destination // no integrity validation
        is ChecksumValidation.UsingHash,
        is ChecksumValidation.UsingHashDownloadedFile,
          -> {
          val expected = when (hashResult) {
            HashResult.NotDownloaded -> resolveChecksumHash(checksumValidation) ?: error("impossible to get an `null` hash here")
            is HashResult.HashMismatched -> hashResult.expectedHash // do not re-resolve the expected hash, instead use the result of the previous hash check that happened at checkIfAlreadyDownloaded
            is HashResult.AlreadyDownloaded -> error("impossible") // would have hit first when branch
          }
          val actual = sha256(destination.readBytes())
          when (expected == actual) {
            true -> destination
            else -> error("downloaded file '$destination' failed integrity check, expected=$expected, actual=$actual")
          }
        }
      }
    }
  }
}

private suspend fun HttpClient.resolveChecksumHash(checksumValidation: ChecksumValidation): String? = when (checksumValidation) {
  ChecksumValidation.Disabled -> null
  is ChecksumValidation.UsingHashDownloadedFile -> {
    require(checksumValidation.algorithm == ChecksumAlgorithm.SHA256) { "Only SHA-256 algorithm is supported for now" }
    val tmp = createTempFile(prefix = "hash", suffix = ".sha256")
    val hashFile = downloadFile(checksumValidation.url, tmp, ChecksumValidation.Disabled)
    val hash = hashFile.readText().split(" ", limit = 2).first()
    tmp.deleteIfExists()
    hash
  }
  is ChecksumValidation.UsingHash -> {
    require(checksumValidation.algorithm == ChecksumAlgorithm.SHA256) { "Only SHA-256 algorithm is supported for now" }
    checksumValidation.expected
  }
}

private suspend fun HttpClient.checkIfAlreadyDownloaded(destination: Path, checksumValidation: ChecksumValidation): HashResult = when (destination.exists()) {
  false -> HashResult.NotDownloaded
  true -> when (checksumValidation) {
    ChecksumValidation.Disabled -> {
      destination.deleteIfExists()
      HashResult.NotDownloaded
    }
    is ChecksumValidation.UsingHashDownloadedFile,
    is ChecksumValidation.UsingHash,
      -> {
      val expected = resolveChecksumHash(checksumValidation) ?: error("impossible to get an `null` hash here")
      val actual = sha256(destination.readBytes())
      when (expected == actual) {
        true -> HashResult.AlreadyDownloaded(hash = actual)
        else -> {
          withContext(Dispatchers.IO) { destination.deleteIfExists() }
          HashResult.HashMismatched(expectedHash = expected)
        }
      }
    }
  }
}

private sealed class HashResult {
  data class AlreadyDownloaded(val hash: String) : HashResult()
  object NotDownloaded : HashResult()
  data class HashMismatched(val expectedHash: String) : HashResult()
}
