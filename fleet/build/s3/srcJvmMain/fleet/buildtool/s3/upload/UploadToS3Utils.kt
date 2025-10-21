package fleet.buildtool.s3.upload

import fleet.buildtool.fs.ReproducibilityMode
import fleet.buildtool.fs.ReproducibilityMode.Reproducible.PermissionOption.Preserve
import fleet.buildtool.fs.readBytesForSha256
import fleet.buildtool.fs.sha256
import fleet.buildtool.fs.tarZst
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

/**
 * Upload local files to S3 bucket.
 *
 * Upload strategy is defined by [S3UploadMetadata.failUploadIfAlreadyExistingInS3] parameter.
 * * If set to `true`, the file will be uploaded only if it does not exist in the S3 bucket.
 * Otherwise, checksum of a file will be compared with the one stored in S3. If they match, the file upload will be skipped.
 * Otherwise, the error will be thrown.
 * * If set to `false`, the file will always be uploaded, and replace any existing file in S3.
 *
 * @param client AWS SDK S3 client. Must be configured with credentials and region.
 * @param filesToUpload list of files to upload.
 * @param bucketName name of the S3 bucket to upload to.
 * @param dryRun if true, no actual upload will be performed, only the message will be logged.
 * @param uploadSha if true, SHA256 checksum of each [filesToUpload] will be uploaded to S3 for future validation.
 */
suspend fun uploadToS3(
  filesToUpload: List<S3UploadMetadata>,
  client: FleetS3Client,
  bucketName: String,
  temporaryDir: Path,
  uploadSha: Boolean,
  dryRun: Boolean,
  logger: Logger,
) {
  val metadataToActualFileToUpload = filesToUpload.associateWith { file ->
    when {
      file.shouldBeArchivedToTarZst -> {
        val tmpArchive = temporaryDir.resolve(file.s3Location)
        tarZst(
          source = file.filepath,
          outputFile = tmpArchive,
          withTopLevelFolder = false,
          temporaryDir = temporaryDir,
          logger = logger,
          reproducibilityMode = ReproducibilityMode.Reproducible(permissionOption = Preserve)
        )
      }

      else -> file.filepath
    }
  }

  when {
    !dryRun -> {
      val alreadyExisting = mutableMapOf<S3UploadMetadata, Path>()
      val notExisting = mutableMapOf<S3UploadMetadata, Path>()

      metadataToActualFileToUpload.forEach { (uploadMetadata, actualFile) ->
        if (uploadMetadata.failUploadIfAlreadyExistingInS3 && client.objectExists(bucketName, uploadMetadata.s3Location)) {
          alreadyExisting[uploadMetadata] = actualFile
        }
        else {
          notExisting[uploadMetadata] = actualFile
        }
      }

      validateAlreadyExisting(filesToUpload = alreadyExisting, client = client, bucketName = bucketName, logger = logger, temporaryDir = temporaryDir)
      // NOTE: There is still a potential TOCTOU race here if another process uploads between existence check and put.
      uploadWithoutValidation(filesToUpload = notExisting, client = client, bucketName = bucketName, logger = logger, temporaryDir = temporaryDir, uploadSha = uploadSha)
    }
    else -> metadataToActualFileToUpload.forEach { (uploadMetadata, fileToUpload) ->
      logger.warn("DRY RUN: (would have) Uploaded '$fileToUpload' to '${uploadMetadata.s3Location}' (bucket=$bucketName)")
    }
  }
}


private suspend fun validateAlreadyExisting(
  filesToUpload: Map<S3UploadMetadata, Path>,
  client: FleetS3Client,
  bucketName: String,
  logger: Logger,
  temporaryDir: Path,
) {
  filesToUpload.forEach { (uploadMetadata, fileToUpload) ->
    val fileFromAws = client.getObject(bucketName, uploadMetadata.s3Location, temporaryDir = temporaryDir)
    require(fileFromAws.exists()) {
      "File '$fileFromAws' does not exist after downloading from S3 bucket '$bucketName' under key '${uploadMetadata.s3Location}'"
    }

    val fileToUploadSha256 = sha256(fileToUpload.readBytesForSha256())
    val fileFromAwsSha256 = sha256(fileFromAws.readBytesForSha256())
    fileFromAws.deleteExisting()
    when {
      fileFromAwsSha256 == fileToUploadSha256 ->
        logger.info("File '$fileToUpload' is already present in S3 bucket '$bucketName' under key '${uploadMetadata.s3Location}'")
      else ->
        error("File '$fileToUpload' is already present in S3 bucket '$bucketName' under key '${uploadMetadata.s3Location}' " +
              "but its checksum does not match with the file being uploaded. Please, make sure that the file is not corrupted." +
              "Upload sha: $fileToUploadSha256, AWS sha: $fileFromAwsSha256")
    }
  }
}

private suspend fun uploadWithoutValidation(
  filesToUpload: Map<S3UploadMetadata, Path>,
  client: FleetS3Client,
  bucketName: String,
  logger: Logger,
  temporaryDir: Path,
  uploadSha: Boolean,
) {
  filesToUpload.forEach { (uploadMetadata, fileToUpload) ->
    client.putObject(bucketName, uploadMetadata.s3Location, fileToUpload)
    logger.info("Uploaded '$fileToUpload' to '${uploadMetadata.s3Location}' (bucket=$bucketName)")
    when {
      uploadSha -> uploadSha256(uploadMetadata, fileToUpload, client, bucketName, logger, temporaryDir)
      else -> {}
    }
  }
}

private suspend fun uploadSha256(
  uploadMetadata: S3UploadMetadata,
  fileToUpload: Path,
  client: FleetS3Client,
  bucketName: String,
  logger: Logger,
  temporaryDir: Path,
) {
  val s3LocationPath = Path(uploadMetadata.s3Location)
  val s3Sha256Location = "${s3LocationPath.invariantSeparatorsPathString}.sha256"
  val sha256 = sha256(fileToUpload.readBytes())
  val sha256File = temporaryDir.resolve("${s3LocationPath.fileName}.sha256")
  sha256File.writeText("$sha256 *${s3LocationPath.fileName}")
  client.putObject(bucketName, s3Sha256Location, sha256File)
  logger.info("Uploaded '$sha256File' to '${s3Sha256Location}' (bucket=$bucketName)")
  sha256File.deleteExisting()
}