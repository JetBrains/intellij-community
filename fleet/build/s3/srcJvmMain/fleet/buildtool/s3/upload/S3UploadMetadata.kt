package fleet.buildtool.s3.upload

import java.nio.file.Path

data class S3UploadMetadata(
  val filepath: Path,
  val s3Location: String,
  val shouldBeArchivedToTarZst: Boolean = false,
  val failUploadIfAlreadyExistingInS3: Boolean,
)
