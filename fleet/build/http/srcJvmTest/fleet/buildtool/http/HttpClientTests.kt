package fleet.buildtool.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpClientTests {
  @OptIn(ExperimentalPathApi::class)
  @Test
  fun `should always download file when checksum validation is disabled`() {
    val resourceUrl = "https://example.com/some-resource.txt"
    val requestCounter = mapOf(
      resourceUrl to AtomicInteger(0),
    )

    val mockEngine = MockEngine { request ->
      requestCounter.getValue(request.url.toString()).incrementAndGet()
      respond(content = ByteReadChannel("sometestdata"), status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/txt"))
    }

    val httpClient = HttpClient(mockEngine) {}
    val destination = createTempDirectory().resolve("some-resource.txt")
    destination.parent.createDirectories()

    requestCounter.getValue(resourceUrl).set(0)
    destination.deleteIfExists()
    httpClient.downloadFileBlocking(resourceUrl, destination, ChecksumValidation.Disabled)
    assertEquals(1, requestCounter.getValue(resourceUrl).get(), "should have tried to download the file")
    assertEquals("sometestdata", destination.readText())

    requestCounter.getValue(resourceUrl).set(0)
    destination.parent.createDirectories()
    destination.writeText("some other data but that will be overridden anyway")
    httpClient.downloadFileBlocking(resourceUrl, destination, ChecksumValidation.Disabled)
    assertEquals(1, requestCounter.getValue(resourceUrl).get(), "should have tried to download the file")
    assertEquals("sometestdata", destination.readText())

    requestCounter.getValue(resourceUrl).set(0)
    destination.parent.createDirectories()
    destination.writeText("sometestdata")
    httpClient.downloadFileBlocking(resourceUrl, destination, ChecksumValidation.Disabled)
    assertEquals(1, requestCounter.getValue(resourceUrl).get(), "should have tried to download the file despite it already existing and matching checksum, because checksum validation is disabled")
    assertEquals("sometestdata", destination.readText())

    destination.parent.deleteRecursively()
  }

  @OptIn(ExperimentalPathApi::class)
  @Test
  fun `should not re-downloaded file if already on disk and matching the hash resolve by URL`() {
    val resourceUrl = "https://example.com/some-resource.txt"
    val resourceUrlSha = "$resourceUrl.sha256"
    val requestCounter = mapOf(
      resourceUrl to AtomicInteger(0),
      resourceUrlSha to AtomicInteger(0),
    )

    val mockEngine = MockEngine { request ->
      requestCounter.getValue(request.url.toString()).incrementAndGet()
      when (request.url.toString()) {
        resourceUrl -> respond(content = ByteReadChannel("sometestdata"), status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/txt"))
        resourceUrlSha -> respond(content = ByteReadChannel("10ba28f2395f43928e1fcc3dd482dc2f1b6a9cef7334d1afda11369f2a1a5486 *some-resource.txt"), status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/txt"))
        else -> error("unexpected request")
      }
    }

    val httpClient = HttpClient(mockEngine) {}
    val destination = createTempDirectory().resolve("some-resource.txt")
    destination.parent.createDirectories()

    requestCounter.getValue(resourceUrl).set(0)
    requestCounter.getValue(resourceUrlSha).set(0)
    destination.deleteIfExists()
    httpClient.downloadFileBlocking(resourceUrl, destination, ChecksumValidation.UsingHashDownloadedFile(ChecksumAlgorithm.SHA256, resourceUrlSha))
    assertEquals(1, requestCounter.getValue(resourceUrlSha).get(), "should have tried to download the checksum file once for integrity check")
    assertEquals(1, requestCounter.getValue(resourceUrl).get(), "should have tried to download the file because it did not exist")
    assertEquals("sometestdata", destination.readText())

    requestCounter.getValue(resourceUrl).set(0)
    requestCounter.getValue(resourceUrlSha).set(0)
    destination.parent.createDirectories()
    destination.writeText("some other data but that will be overridden anyway")
    httpClient.downloadFileBlocking(resourceUrl, destination, ChecksumValidation.UsingHashDownloadedFile(ChecksumAlgorithm.SHA256, resourceUrlSha))
    assertEquals(1, requestCounter.getValue(resourceUrlSha).get(), "should have tried to download the checksum file once for integrity check, but not twice because it re-used the first download of the checksum file")
    assertEquals(1, requestCounter.getValue(resourceUrl).get(), "should have tried to download the file because it existed but did not matched checksum")
    assertEquals("sometestdata", destination.readText())

    requestCounter.getValue(resourceUrl).set(0)
    requestCounter.getValue(resourceUrlSha).set(0)
    destination.parent.createDirectories()
    destination.writeText("sometestdata")
    httpClient.downloadFileBlocking(resourceUrl, destination, ChecksumValidation.UsingHashDownloadedFile(ChecksumAlgorithm.SHA256, resourceUrlSha))
    assertEquals(1, requestCounter.getValue(resourceUrlSha).get(), "should have tried to download the checksum file once for integrity check, but not twice because it re-used the first download of the checksum file")
    assertEquals(0, requestCounter.getValue(resourceUrl).get(), "should not have tried to download the file because it exists and matched checksum")
    assertEquals("sometestdata", destination.readText())

    destination.parent.deleteRecursively()
  }

  @OptIn(ExperimentalPathApi::class)
  @Test
  fun `should not re-downloaded file if already on disk and matching the raw hash`() {
    val resourceUrl = "https://example.com/some-resource.txt"
    val requestCounter = mapOf(
      resourceUrl to AtomicInteger(0),
    )

    val mockEngine = MockEngine { request ->
      requestCounter.getValue(request.url.toString()).incrementAndGet()
      when (request.url.toString()) {
        resourceUrl -> respond(content = ByteReadChannel("sometestdata"), status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/txt"))
        else -> error("unexpected request")
      }
    }

    val httpClient = HttpClient(mockEngine) {}
    val destination = createTempDirectory().resolve("some-resource.txt")
    destination.parent.createDirectories()

    requestCounter.getValue(resourceUrl).set(0)
    destination.deleteIfExists()
    httpClient.downloadFileBlocking(resourceUrl, destination, ChecksumValidation.UsingHash(ChecksumAlgorithm.SHA256, "10ba28f2395f43928e1fcc3dd482dc2f1b6a9cef7334d1afda11369f2a1a5486"))
    assertEquals(1, requestCounter.getValue(resourceUrl).get(), "should have tried to download the file because it did not exist")
    assertEquals("sometestdata", destination.readText())

    requestCounter.getValue(resourceUrl).set(0)
    destination.parent.createDirectories()
    destination.writeText("some other data but that will be overridden anyway")
    httpClient.downloadFileBlocking(resourceUrl, destination, ChecksumValidation.UsingHash(ChecksumAlgorithm.SHA256, "10ba28f2395f43928e1fcc3dd482dc2f1b6a9cef7334d1afda11369f2a1a5486"))
    assertEquals(1, requestCounter.getValue(resourceUrl).get(), "should have tried to download the file because it existed but did not matched checksum")
    assertEquals("sometestdata", destination.readText())

    requestCounter.getValue(resourceUrl).set(0)
    destination.parent.createDirectories()
    destination.writeText("sometestdata")
    httpClient.downloadFileBlocking(resourceUrl, destination, ChecksumValidation.UsingHash(ChecksumAlgorithm.SHA256, "10ba28f2395f43928e1fcc3dd482dc2f1b6a9cef7334d1afda11369f2a1a5486"))
    assertEquals(0, requestCounter.getValue(resourceUrl).get(), "should not have tried to download the file because it exists and matched checksum")
    assertEquals("sometestdata", destination.readText())

    destination.parent.deleteRecursively()
  }

  @OptIn(ExperimentalPathApi::class)
  @Test
  fun `should fail when server returned a file not matching checksum`() {
    val resourceUrl = "https://example.com/some-resource.txt"
    val requestCounter = mapOf(
      resourceUrl to AtomicInteger(0),
    )

    val mockEngine = MockEngine { request ->
      requestCounter.getValue(request.url.toString()).incrementAndGet()
      when (request.url.toString()) {
        resourceUrl -> respond(content = ByteReadChannel("sometestdataDJFBSIDFUBSDIFUSDBFIUDB"), status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/txt"))
        else -> error("unexpected request")
      }
    }

    val httpClient = HttpClient(mockEngine) {}
    val destination = createTempDirectory().resolve("some-resource.txt")
    destination.parent.createDirectories()

    requestCounter.getValue(resourceUrl).set(0)
    destination.deleteIfExists()
    val exception = assertFailsWith<IllegalStateException> {
      httpClient.downloadFileBlocking(resourceUrl, destination, ChecksumValidation.UsingHash(ChecksumAlgorithm.SHA256, "10ba28f2395f43928e1fcc3dd482dc2f1b6a9cef7334d1afda11369f2a1a5486"))
    }
    assertEquals("downloaded file '$destination' failed integrity check, expected=10ba28f2395f43928e1fcc3dd482dc2f1b6a9cef7334d1afda11369f2a1a5486, actual=5d2080c90b2d11ff9f17adb960c0f579931d94311e2c47cf5c2d97de4865d4f0", exception.message)
    assertEquals(1, requestCounter.getValue(resourceUrl).get(), "should have tried to download the file because it did not exist")
    assertEquals("sometestdataDJFBSIDFUBSDIFUSDBFIUDB", destination.readText())

    destination.parent.deleteRecursively()
  }
}
