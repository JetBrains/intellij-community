package fleet.buildtool.s3.upload

import fleet.buildtool.s3.upload.FakeFleetS3Client.Call
import kotlinx.coroutines.test.runTest
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UploadToS3UtilsTest {

  private lateinit var tempDir: Path
  private lateinit var storageDir: Path


  @BeforeTest
  fun setUp() {
    tempDir = createTempDirectory(UploadToS3UtilsTest::class.simpleName)
    storageDir = tempDir.resolve("storage")
    storageDir.createDirectories()
  }

  @OptIn(kotlin.io.path.ExperimentalPathApi::class)
  @AfterTest
  fun tearDown() {
    try {
      tempDir.deleteRecursively()
    }
    catch (_: Throwable) {
      // ignore on Windows file locks etc.
    }
  }

  @Test
  fun put_when_object_does_not_exist(): Unit = runTest {
    // Given
    val bucket = "test-bucket"
    val s3location = "path/in/s3/file.bin"
    val file = tempFile("hello")
    val meta = S3UploadMetadata(
      filepath = file,
      s3Location = s3location,
      shouldBeArchivedToTarZst = false,
      failUploadIfAlreadyExistingInS3 = true,
    )

    val client = FakeFleetS3Client(storageDir = storageDir)

    // When
    uploadToS3(
      filesToUpload = listOf(meta),
      client = client,
      bucketName = bucket,
      temporaryDir = tempDir,
      dryRun = false,
      logger = NOPLogger.NOP_LOGGER,
      uploadSha = false,
    )

    // Then
    assertEquals(
      expected = listOf(
        Call.Exists(bucket, s3location),
        Call.Put(bucket, s3location, file)
      ),
      actual = client.calls,
    )
    val call = client.calls.filterIsInstance<Call.Put>().single()
    assertEquals(bucket, call.bucket)
    assertEquals(meta.s3Location, call.key)
    assertEquals(file, call.file)
  }

  @Test
  fun put_with_sha_when_uploadSha(): Unit = runTest {
    // Given
    val bucket = "test-bucket"
    val s3location = "path/in/s3/file.bin"
    val s3Sha256location = "path/in/s3/file.bin.sha256"
    val file = tempFile("hello")
    val sha256File = tempDir.resolve("file.bin.sha256")
    val meta = S3UploadMetadata(
      filepath = file,
      s3Location = s3location,
      shouldBeArchivedToTarZst = false,
      failUploadIfAlreadyExistingInS3 = true,
    )

    val client = FakeFleetS3Client(storageDir = storageDir)

    // When
    uploadToS3(
      filesToUpload = listOf(meta),
      client = client,
      bucketName = bucket,
      temporaryDir = tempDir,
      dryRun = false,
      logger = NOPLogger.NOP_LOGGER,
      uploadSha = true,
    )

    // Then
    assertEquals(
      expected = listOf(
        Call.Exists(bucket, s3location),
        Call.Put(bucket, s3location, file),
        Call.Put(bucket, s3Sha256location, sha256File),
      ),
      actual = client.calls,
    )
    val putFileCall = client.calls.filterIsInstance<Call.Put>()[0]
    val putShaCall = client.calls.filterIsInstance<Call.Put>()[1]
    assertEquals(bucket, putFileCall.bucket)
    assertEquals(meta.s3Location, putFileCall.key)
    assertEquals(file, putFileCall.file)

    assertEquals(bucket, putShaCall.bucket)
    assertEquals(s3Sha256location, putShaCall.key)
    assertEquals(sha256File, putShaCall.file)

    val storedSha256 = storageDir.resolve(bucket).resolve(s3Sha256location)
    assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824 *file.bin", storedSha256.readText())
  }

  @Test
  fun validate_existing_when_checksum_matches_logs_and_skips_put(): Unit = runTest {
    // Given existing object with matching checksum
    val bucket = "bucket"
    val content = "content"
    val file = tempFile(text = content)
    val meta = S3UploadMetadata(
      filepath = file,
      s3Location = "key/file.txt",
      shouldBeArchivedToTarZst = false,
      failUploadIfAlreadyExistingInS3 = true,
    )

    storageDir.resolve(bucket).resolve(meta.s3Location)
      .apply { parent.createDirectories() }
      .createFile()
      .apply { writeText(content) }

    val client = FakeFleetS3Client(storageDir)

    // When/Then (no exception)
    uploadToS3(
      filesToUpload = listOf(meta),
      client = client,
      bucketName = bucket,
      temporaryDir = tempDir,
      dryRun = false,
      logger = NOPLogger.NOP_LOGGER,
      uploadSha = false,
    )

    // It should not try to re-upload
    assertEquals(
      expected = client.calls,
      actual = listOf(
        Call.Exists(bucket, meta.s3Location),
        Call.Get(bucket, meta.s3Location, tempDir)
      )
    )
  }

  @Test
  fun put_called_twice_validate_existing_when_checksum_matches_logs_and_skips_put(): Unit = runTest {
    // Given existing object with matching checksum
    val bucket = "bucket"
    val content = "content"
    val file = tempFile(text = content)
    val meta = S3UploadMetadata(
      filepath = file,
      s3Location = "key/file.txt",
      shouldBeArchivedToTarZst = false,
      failUploadIfAlreadyExistingInS3 = true,
    )

    val client = FakeFleetS3Client(storageDir)

    // When/Then (no exception)
    uploadToS3(
      filesToUpload = listOf(meta),
      client = client,
      bucketName = bucket,
      temporaryDir = tempDir,
      dryRun = false,
      logger = NOPLogger.NOP_LOGGER,
      uploadSha = false,
    )

    uploadToS3(
      filesToUpload = listOf(meta),
      client = client,
      bucketName = bucket,
      temporaryDir = tempDir,
      dryRun = false,
      logger = NOPLogger.NOP_LOGGER,
      uploadSha = false,
    )

    // It should not try to re-upload
    assertEquals(
      expected = client.calls,
      actual = listOf(
        Call.Exists(bucket, meta.s3Location),
        Call.Put(bucket, meta.s3Location, file),
        Call.Exists(bucket, meta.s3Location),
        Call.Get(bucket, meta.s3Location, tempDir)
      )
    )
  }

  @Test
  fun validate_existing_when_checksum_mismatch_fails(): Unit = runTest {
    // Given existing object with different checksum
    val bucket = "bucket"
    val file = tempFile("abc")
    val meta = S3UploadMetadata(
      filepath = file,
      s3Location = "key.bin",
      shouldBeArchivedToTarZst = false,
      failUploadIfAlreadyExistingInS3 = true,
    )

    storageDir.resolve(bucket).resolve(meta.s3Location)
      .apply { parent.createDirectories() }
      .createFile()
      .apply { writeText(text = "different") }

    val client = FakeFleetS3Client(storageDir)

    val ex = assertFailsWith<IllegalStateException> {
      uploadToS3(
        filesToUpload = listOf(meta),
        client = client,
        bucketName = bucket,
        temporaryDir = tempDir,
        dryRun = false,
        logger = NOPLogger.NOP_LOGGER,
        uploadSha = false,
      )
    }
    assertTrue(ex.message?.contains("checksum does not match")!!)
    assertEquals(
      expected = client.calls,
      actual = listOf(
        Call.Exists(bucket, meta.s3Location),
        Call.Get(bucket, meta.s3Location, tempDir)
      )
    )
  }

  @Test
  fun dry_run_never_calls_ops_and_logs_warning(): Unit = runTest {
    val bucket = "bucket"
    val file = tempFile("x")
    val meta = S3UploadMetadata(
      filepath = file,
      s3Location = "dry/key",
      shouldBeArchivedToTarZst = false,
      failUploadIfAlreadyExistingInS3 = true,
    )
    val logger = NOPLogger.NOP_LOGGER

    val client = FakeFleetS3Client(storageDir)

    uploadToS3(
      filesToUpload = listOf(meta),
      client = client,
      bucketName = bucket,
      temporaryDir = tempDir,
      dryRun = true,
      logger = logger,
      uploadSha = false,
    )

    assertEquals(emptyList(), client.calls)
  }

  @Test
  fun when_flag_false_uploads_without_existence_check(): Unit = runTest {
    val bucket = "bucket"
    val file = tempFile("abc")
    val meta = S3UploadMetadata(
      filepath = file,
      s3Location = "key",
      shouldBeArchivedToTarZst = false,
      failUploadIfAlreadyExistingInS3 = false, // do not check existence
    )
    val client = FakeFleetS3Client(storageDir)

    uploadToS3(
      filesToUpload = listOf(meta),
      client = client,
      bucketName = bucket,
      temporaryDir = tempDir,
      dryRun = false,
      logger = NOPLogger.NOP_LOGGER,
      uploadSha = false,
    )

    // Only put should be recorded; no existence/checksum calls
    assertEquals(listOf<Call>(Call.Put(bucket, meta.s3Location, file)), client.calls)
  }

  // Helpers
  private fun tempFile(text: String): Path {
    val f = Files.createTempFile(tempDir, "file", ".txt")
    f.writeText(text)
    return f
  }

  @Test
  fun archives_when_flag_true_and_uploads_archive_instead_of_source(): Unit = runTest {
    // Given
    val bucket = "tar-bucket"
    val source = tempFile("payload-to-archive")
    val s3Key = "artifacts/archive.tar.zst"
    val meta = S3UploadMetadata(
      filepath = source,
      s3Location = s3Key,
      shouldBeArchivedToTarZst = true,
      failUploadIfAlreadyExistingInS3 = true,
    )

    val client = FakeFleetS3Client(storageDir)

    // When
    uploadToS3(
      filesToUpload = listOf(meta),
      client = client,
      bucketName = bucket,
      temporaryDir = tempDir,
      dryRun = false,
      uploadSha = false,
      logger = NOPLogger.NOP_LOGGER,
    )

    // Then
    val put = client.calls.filterIsInstance<Call.Put>().single()
    // Archive should be created under temporaryDir with s3Key name
    val expectedArchive = tempDir.resolve(s3Key)
    assertEquals(expectedArchive, put.file, "Should upload the created tar.zst archive, not the original file")
    assertTrue(expectedArchive.exists(), "Archive should exist")
    assertNotEquals(source, put.file, "Original file path must not be uploaded when archiving is requested")
  }
}

private class FakeFleetS3Client(
  val storageDir: Path,
) : FleetS3Client {

  sealed class Call {
    data class Put(val bucket: String, val key: String, val file: Path) : Call()
    data class Exists(val bucket: String, val key: String) : Call()
    data class Get(val bucket: String, val key: String, val temporaryDir: Path) : Call()
  }

  val calls = mutableListOf<Call>()

  override suspend fun objectExists(bucket: String, key: String): Boolean {
    calls += Call.Exists(bucket, key)
    return storageDir.resolve(bucket).resolve(key).exists()
  }

  override suspend fun getObject(bucket: String, key: String, temporaryDir: Path): Path {
    calls += Call.Get(bucket, key, temporaryDir)
    val f = createTempFile(temporaryDir, "fake-s3-", "")
    storageDir.resolve(bucket).resolve(key).bufferedReader()
      .use { f.writeText(it.readText()) }
    return f
  }

  override suspend fun putObject(bucket: String, key: String, file: Path) {
    calls += Call.Put(bucket, key, file)
    val newFile = storageDir
      .resolve(bucket)
      .resolve(key)
      .createParentDirectories()
    file.copyTo(newFile)
  }
}