package fleet.net

import fleet.multiplatform.shims.multiplatformIO
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import io.ktor.http.fileExtensions
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.outputStream

suspend fun HttpClient.downloadFile(url: String): Path {
  return prepareGet(url) {
    expectSuccess = false
  }.execute { response ->
    if (!response.status.isSuccess()) {
      error("Couldn't download $url. Status code: ${response.status}. Body: ${response.bodyAsText()}")
    }
    val channel: ByteReadChannel = response.body()
    withContext(Dispatchers.multiplatformIO) {
      val suffix = response.contentType()?.fileExtensions()?.firstOrNull()?.let { ".$it" } ?: ""
      val targetFile = Files.createTempFile("fleetDownloadFile", suffix)
      targetFile.outputStream(StandardOpenOption.CREATE).buffered().use { fos ->
        channel.copyTo(fos)
      }
      targetFile
    }
  }
}
