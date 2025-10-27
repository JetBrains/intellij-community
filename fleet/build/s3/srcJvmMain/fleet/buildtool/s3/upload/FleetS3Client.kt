package fleet.buildtool.s3.upload

import java.nio.file.Path

/**
 * Abstraction for minimal S3 operations required by `uploadToS3` utility to make it easily testable.
 */
interface FleetS3Client {
  suspend fun objectExists(bucket: String, key: String): Boolean
  suspend fun getObject(bucket: String, key: String, temporaryDir: Path): Path
  suspend fun putObject(bucket: String, key: String, file: Path)
}
