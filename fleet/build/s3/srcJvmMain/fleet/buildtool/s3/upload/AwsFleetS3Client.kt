package fleet.buildtool.s3.upload

import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.asByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import fleet.buildtool.s3.objectExists
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream
import aws.sdk.kotlin.services.s3.S3Client as AwsClient

// Real adapter that delegates to AWS SDK S3Client
class AwsFleetS3Client(private val client: AwsClient) : FleetS3Client {
  override suspend fun objectExists(bucket: String, key: String): Boolean = client.objectExists {
    this.bucket = bucket
    this.key = key
  }

  override suspend fun putObject(bucket: String, key: String, file: Path) {
    client.putObject {
      this.bucket = bucket
      this.key = key
      body = file.asByteStream()
    }
  }

  override suspend fun getObject(bucket: String, key: String, temporaryDir: Path): Path {
    val file = createTempFile(temporaryDir, "s3-download-", "")
    client.getObject(input = GetObjectRequest {
      this.bucket = bucket
      this.key = key
    }) { response ->
      file.outputStream().buffered().use { os ->
        val objectStream = requireNotNull(response.body)
        os.write(objectStream.toByteArray())
      }
    }
    return file
  }
}
