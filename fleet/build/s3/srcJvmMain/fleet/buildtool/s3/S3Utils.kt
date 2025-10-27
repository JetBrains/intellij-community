package fleet.buildtool.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headObject
import aws.sdk.kotlin.services.s3.model.HeadObjectRequest
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.sdk.kotlin.services.s3.model.S3Exception

suspend fun S3Client.objectExists(block: HeadObjectRequest.Builder.() -> Unit): Boolean = try {
  headObject(block)
  true
}
catch (e: S3Exception) {
  when {
    e is NotFound -> false // object does not exist
    e.sdkErrorMetadata.errorCode == "404" -> false // object does not exist
    e.sdkErrorMetadata.errorCode == "403" -> false // object does not exist but credentials lacks `s3:ListBucket` permission
    else -> throw e
  }
}
