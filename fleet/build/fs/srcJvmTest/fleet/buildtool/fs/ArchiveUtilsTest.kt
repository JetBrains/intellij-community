package fleet.buildtool.fs

import fleet.buildtool.fs.ReproducibilityMode.None
import fleet.buildtool.fs.ReproducibilityMode.Reproducible
import fleet.buildtool.fs.ReproducibilityMode.Reproducible.PermissionOption.Override
import fleet.buildtool.fs.ReproducibilityMode.Reproducible.PermissionOption.Preserve
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.outputStream
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.readText
import kotlin.io.path.setLastModifiedTime
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveUtilsTest {

  private val logger: Logger = LoggerFactory.getLogger(ArchiveUtilsTest::class.java)

  private lateinit var tempDir: Path

  @BeforeTest
  fun setUp() {
    tempDir = Files.createTempDirectory("archive-utils-test")
  }

  @OptIn(ExperimentalPathApi::class)
  @AfterTest
  fun tearDown() {
    tempDir.deleteRecursively()
  }

  // ========== Zip Tests ==========

  @Test
  fun `should create zip archive from directory without top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file1.txt").writeText("content1")
    sourceDir.resolve("file2.txt").writeText("content2")
    val subDir = sourceDir.resolve("subdir").createDirectories()
    subDir.resolve("file3.txt").writeText("content3")

    val targetZip = tempDir.resolve("output.zip")

    // When
    zip(targetZip, sourceDir, withTopLevelFolder = false)

    // Then
    assertTrue(targetZip.exists())
    val entriesMap = readZipEntries(targetZip)
    assertEquals(3, entriesMap.size)
    assertEquals("content1", entriesMap["file1.txt"])
    assertEquals("content2", entriesMap["file2.txt"])
    assertEquals("content3", entriesMap["subdir/file3.txt"])
  }

  @Test
  fun `should create zip archive from directory with top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file1.txt").writeText("content1")
    val subDir = sourceDir.resolve("subdir").createDirectories()
    subDir.resolve("file2.txt").writeText("content2")

    val targetZip = tempDir.resolve("output.zip")

    // When
    zip(targetZip, sourceDir, withTopLevelFolder = true)

    // Then
    assertTrue(targetZip.exists())
    val entriesMap = readZipEntries(targetZip)
    assertEquals(2, entriesMap.size)
    assertEquals("content1", entriesMap["source/file1.txt"])
    assertEquals("content2", entriesMap["source/subdir/file2.txt"])
  }

  @Test
  fun `should create zip with sorted entries for reproducibility`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("zebra.txt").writeText("z")
    sourceDir.resolve("alpha.txt").writeText("a")
    sourceDir.resolve("beta.txt").writeText("b")

    val targetZip = tempDir.resolve("output.zip")

    // When
    zip(targetZip, sourceDir, withTopLevelFolder = false)

    // Then
    val entryNames = readZipEntryNames(targetZip)
    assertEquals(listOf("alpha.txt", "beta.txt", "zebra.txt"), entryNames)
  }

  @Test
  fun `should create zip with timestamp set to zero for reproducibility`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file.txt").writeText("content")
    val targetZip = tempDir.resolve("output.zip")

    // When
    zip(targetZip, sourceDir, withTopLevelFolder = false)

    // Then
    ZipInputStream(targetZip.inputStream()).use { zipIn ->
      val entry = zipIn.nextEntry
      assertEquals(0L, entry.time)
    }
  }

  @Test
  fun `should create zip from sequence of input streams`() {
    // Given
    val filesToZip = sequenceOf(
      "path/to/file1.txt" to ByteArrayInputStream("content1".toByteArray()),
      "path/to/file2.txt" to ByteArrayInputStream("content2".toByteArray()),
      "another/file3.txt" to ByteArrayInputStream("content3".toByteArray())
    )
    val targetZip = tempDir.resolve("output.zip")

    // When
    zip(targetZip, filesToZip)

    // Then
    val entriesMap = readZipEntries(targetZip)
    assertEquals(3, entriesMap.size)
    assertEquals("content1", entriesMap["path/to/file1.txt"])
    assertEquals("content2", entriesMap["path/to/file2.txt"])
    assertEquals("content3", entriesMap["another/file3.txt"])
  }

  @Test
  fun `should create empty zip when source directory is empty`() {
    // Given
    val sourceDir = tempDir.resolve("empty").createDirectories()
    val targetZip = tempDir.resolve("output.zip")

    // When
    zip(targetZip, sourceDir, withTopLevelFolder = false)

    // Then
    assertTrue(targetZip.exists())
    val entriesMap = readZipEntries(targetZip)
    assertEquals(0, entriesMap.size)
  }

  @Test
  fun `should create zip from a single file without top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source.txt").apply { writeText("content") }
    val tmpDir = tempDir.resolve("tmp")
    val outputFile = tempDir.resolve("output.zip")

    val extractDir = tempDir.resolve("extracted")
    val extractTmpDir = tempDir.resolve("tmp2")

    // When
    zip(sourceDir, outputFile, withTopLevelFolder = false, tmpDir, logger, None)

    extractZip(outputFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, extractTmpDir, logger)

    // Then
    assertTrue(extractDir.resolve("source.txt").exists())
    assertEquals(extractDir.resolve("source.txt").readText(), "content")
  }

  @Test
  fun `should use invariant path separators for reproducibility`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    val subDir = sourceDir.resolve("sub1").resolve("sub2").createDirectories()
    subDir.resolve("file.txt").writeText("content")
    val targetZip = tempDir.resolve("output.zip")

    // When
    zip(targetZip, sourceDir, withTopLevelFolder = false)

    // Then
    val entryNames = readZipEntryNames(targetZip)
    // Should use forward slashes regardless of platform
    assertTrue(entryNames.any { it.contains("sub1/sub2/file.txt") })
  }

  @Test
  fun `should handle mixed content in zip`() {
    assumePosixFileSystem()
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()

    // Regular files
    sourceDir.resolve("text.txt").writeText("text")
    sourceDir.resolve("binary.bin").writeBytes(ByteArray(100) { it.toByte() })

    // Directories
    val nestedDir = sourceDir.resolve("nested/dir").createDirectories()
    nestedDir.resolve("nested.txt").writeText("nested")

    // Symlinks (if supported)
    val target = sourceDir.resolve("target.txt").apply { writeText("target") }
    sourceDir.resolve("link.txt").createSymbolicLinkPointingTo(target)

    val outputFile = tempDir.resolve("output.zip")
    val tmpDir = tempDir.resolve("tmp")

    // When
    zip(sourceDir, outputFile, withTopLevelFolder = false, tmpDir, logger, None)

    // Then
    val extractDir = tempDir.resolve("extracted")
    val extractTmpDir = tempDir.resolve("tmp2")
    extractZip(outputFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, extractTmpDir, logger)

    assertTrue(extractDir.resolve("text.txt").exists())
    assertTrue(extractDir.resolve("binary.bin").exists())
    assertTrue(extractDir.resolve("nested/dir/nested.txt").exists())
    assertEquals("text", extractDir.resolve("text.txt").readText())
    assertEquals("nested", extractDir.resolve("nested/dir/nested.txt").readText())
    val symlink = extractDir.resolve("link.txt")
    assertTrue(symlink.exists())
    assertTrue(symlink.isSymbolicLink())
    assertEquals(symlink.readSymbolicLink(), target)
  }

  @Test
  fun `should extract manually created symlink from zip`() {
    val extractDir = tempDir.resolve("extracted")
    val zipFile = getFileFromResources("archive/single_symlink.zip")
    val tmpDir = tempDir.resolve("tmp")

    extractZip(zipFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, tmpDir, logger)

    val symlink = extractDir.resolve("link.txt")
    assertTrue(symlink.exists())
    assertTrue(symlink.isSymbolicLink())
  }

  @Test
  fun `should extract empty folder from assets zip`() {
    val extractDir = tempDir.resolve("extracted")
    val zipFile = getFileFromResources("archive/empty_folder.zip")
    val tmpDir = tempDir.resolve("tmp")

    extractZip(zipFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, tmpDir, logger)

    assertTrue(extractDir.resolve("archive").exists())
  }

  @Test
  fun `should handle mixed content in assets zip`() {
    // Given
    val archiveFile = getFileFromResources("archive/mixed.zip")
    val tmpDir = tempDir.resolve("tmp")

    // Then
    val extractDir = tempDir.resolve("extracted")
    extractZip(archiveFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, tmpDir, logger)

    assertTrue(extractDir.resolve("text.txt").exists())
    assertTrue(extractDir.resolve("binary.bin").exists())
    assertTrue(extractDir.resolve("nested/nested.txt").exists())
    assertEquals("abc", extractDir.resolve("text.txt").readText().trim())
    assertEquals("nested", extractDir.resolve("nested/nested.txt").readText().trim())
    val symlink = extractDir.resolve("link.txt")
    assertTrue(symlink.exists())
    assertTrue(symlink.isSymbolicLink())
  }

  // ========== Gzip Tests ==========

  @Test
  fun `should create gzip archive from single file`() {
    // Given
    val sourceFile = tempDir.resolve("source.txt").apply {
      writeText("This is test content for gzip compression")
    }
    val targetGz = tempDir.resolve("output.txt.gz")

    // When
    gz(targetGz, sourceFile)

    // Then
    assertTrue(targetGz.exists())
    assertTrue(targetGz.fileSize() > 0)
  }

  @Test
  fun `should compress and decompress file correctly`() {
    // Given
    val content = "Test content for round-trip compression"
    val sourceFile = tempDir.resolve("source.txt").apply { writeText(content) }
    val gzFile = tempDir.resolve("compressed.gz")
    val decompressedFile = tempDir.resolve("decompressed.txt")

    // When
    gz(gzFile, sourceFile)
    extractGz(gzFile, decompressedFile, logger)

    // Then
    assertEquals(content, decompressedFile.readText())
  }

  @Test
  fun `should handle empty file compression`() {
    // Given
    val sourceFile = tempDir.resolve("empty.txt").apply { writeText("") }
    val targetGz = tempDir.resolve("empty.gz")

    // When
    gz(targetGz, sourceFile)

    // Then
    assertTrue(targetGz.exists())
    assertTrue(targetGz.fileSize() > 0) // GZ header is still present
  }

  // ========== Extract Zip Tests ==========

  @Test
  fun `should extract zip archive to destination`() {
    // Given
    val zipFile = tempDir.resolve("archive.zip")
    createTestZip(zipFile, mapOf(
      "file1.txt" to "content1",
      "dir/file2.txt" to "content2"
    ))
    val destination = tempDir.resolve("extracted")

    // When
    extractZip(zipFile,
               destination,
               stripTopLevelFolder = false,
               cleanDestination = false,
               temporaryDir = tempDir.resolve("tmp"),
               logger = logger)

    // Then
    assertTrue(destination.resolve("file1.txt").exists())
    assertEquals("content1", destination.resolve("file1.txt").readText())
    assertTrue(destination.resolve("dir/file2.txt").exists())
    assertEquals("content2", destination.resolve("dir/file2.txt").readText())
  }

  @Test
  fun `should create destination directory if not exists`() {
    // Given
    val zipFile = tempDir.resolve("archive.zip")
    createTestZip(zipFile, mapOf("file.txt" to "content"))
    val destination = tempDir.resolve("non-existent/extracted")

    // When
    extractZip(zipFile,
               destination,
               stripTopLevelFolder = false,
               cleanDestination = false,
               temporaryDir = tempDir.resolve("tmp"),
               logger = logger)

    // Then
    assertTrue(destination.exists())
    assertTrue(destination.resolve("file.txt").exists())
  }

  @Test
  fun `should extract directories from zip`() {
    // Given
    val zipFile = tempDir.resolve("archive.zip")
    ZipOutputStream(zipFile.outputStream()).use { zipOut ->
      zipOut.putNextEntry(ZipEntry("emptydir/"))
      zipOut.closeEntry()
      zipOut.putNextEntry(ZipEntry("emptydir/file.txt"))
      zipOut.write("content".toByteArray())
      zipOut.closeEntry()
    }
    val destination = tempDir.resolve("extracted")

    // When
    extractZip(zipFile,
               destination,
               stripTopLevelFolder = false,
               cleanDestination = false,
               temporaryDir = tempDir.resolve("tmp"),
               logger = logger)

    // Then
    assertTrue(destination.resolve("emptydir").isDirectory())
    assertTrue(destination.resolve("emptydir/file.txt").exists())
  }

  @Test
  fun `should handle zip with nested directories`() {
    // Given
    val zipFile = tempDir.resolve("archive.zip")
    createTestZip(zipFile, mapOf(
      "level1/level2/level3/file.txt" to "deep content"
    ))
    val destination = tempDir.resolve("extracted")

    // When
    extractZip(zipFile,
               destination,
               stripTopLevelFolder = false,
               cleanDestination = false,
               temporaryDir = tempDir.resolve("tmp"),
               logger = logger)

    // Then
    assertTrue(destination.resolve("level1/level2/level3/file.txt").exists())
    assertEquals("deep content", destination.resolve("level1/level2/level3/file.txt").readText())
  }

  // ========== Extract Single File Zip Tests ==========

  @Test
  fun `should extract single file matching the predicate`() {
    // Given
    val zipFile = tempDir.resolve("archive.zip")
    createTestZip(zipFile, mapOf(
      "file1.txt" to "content1",
      "file2.txt" to "content2",
      "target.txt" to "target content"
    ))
    val outputFile = tempDir.resolve("extracted.txt")

    // When
    extractSingleFileZip(zipFile, outputFile, logger) { entry ->
      entry.name == "target.txt"
    }

    // Then
    assertTrue(outputFile.exists())
    assertEquals("target content", outputFile.readText())
  }

  @Test
  fun `should create parent directories if not exist`() {
    // Given
    val zipFile = tempDir.resolve("archive.zip")
    createTestZip(zipFile, mapOf("file.txt" to "content"))
    val outputFile = tempDir.resolve("non-existent/dir/output.txt")

    // When
    extractSingleFileZip(zipFile, outputFile, logger) { true }

    // Then
    assertTrue(outputFile.exists())
    assertEquals("content", outputFile.readText())
  }


  @Test
  fun `should clean destination zip when cleanDestination is true`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("new.txt").writeText("new")
    val tarGz = tempDir.resolve("archive.zip")
    val tmpDir = tempDir.resolve("tmp1")
    zip(sourceDir, tarGz, withTopLevelFolder = false, tmpDir, logger, reproducibilityMode = None)

    val destination = tempDir.resolve("extracted").createDirectories()
    destination.resolve("old.txt").writeText("old")

    val extractTmpDir = tempDir.resolve("tmp2")

    // When
    extractZip(tarGz, destination, stripTopLevelFolder = false, cleanDestination = true, extractTmpDir, logger)

    // Then
    assertTrue(destination.resolve("new.txt").exists())
    assertFalse(destination.resolve("old.txt").exists())
  }

  @Test
  fun `should throw error when singleZip is empty`() {
    // Given
    val zipFile = tempDir.resolve("empty.zip")
    ZipOutputStream(zipFile.outputStream()).use { }
    val outputFile = tempDir.resolve("output.txt")

    // When/Then
    val exception = assertFails {
      extractSingleFileZip(zipFile, outputFile, logger) { true }
    }
    assertContains(exception.message ?: "", "No entry found")
  }

  @Test
  fun `should throw error when singleZip contains directory`() {
    // Given
    val zipFile = tempDir.resolve("archive.zip")
    ZipOutputStream(zipFile.outputStream()).use { zipOut ->
      zipOut.putNextEntry(ZipEntry("dir/"))
      zipOut.closeEntry()
    }
    val outputFile = tempDir.resolve("output.txt")

    // When/Then
    val exception = assertFails {
      extractSingleFileZip(zipFile, outputFile, logger) { true }
    }
    assertContains(exception.message ?: "", "Directory found")
  }

  // ========== Extract Gzip Tests ==========

  @Test
  fun `should extract gzip archive`() {
    // Given
    val content = "Test content for extraction"
    val sourceFile = tempDir.resolve("source.txt").apply { writeText(content) }
    val gzFile = tempDir.resolve("archive.gz")
    gz(gzFile, sourceFile)
    val destination = tempDir.resolve("extracted.txt")

    // When
    extractGz(gzFile, destination, logger)

    // Then
    assertTrue(destination.exists())
    assertEquals(content, destination.readText())
  }

  @Test
  fun `should create parent directory if not exists for gzip extraction`() {
    // Given
    val sourceFile = tempDir.resolve("source.txt").apply { writeText("content") }
    val gzFile = tempDir.resolve("archive.gz")
    gz(gzFile, sourceFile)
    val destination = tempDir.resolve("non-existent/dir/extracted.txt")

    // When
    extractGz(gzFile, destination, logger)

    // Then
    assertTrue(destination.exists())
    assertEquals("content", destination.readText())
  }

  // ========== POSIX Permissions Tests ==========

  @Test
  fun `should convert all possible POSIX permissions`() {
    assumePosixFileSystem()

    // Given
    val mode = 0b111111111 // rwxrwxrwx (777)

    // When
    val permissions = mode.toPosixPermissions()

    // Then
    assertEquals(9, permissions.size)
    assertTrue(permissions.containsAll(PosixFilePermission.entries))
  }

  @Test
  fun `should convert no permissions`() {
    assumePosixFileSystem()

    // Given
    val mode = 0b000000000 // no permissions (000)

    // When
    val permissions = mode.toPosixPermissions()

    // Then
    assertEquals(0, permissions.size)
  }

  @Test
  fun `should convert POSIX permissions to int correctly`() {
    assumePosixFileSystem()

    // Given
    val permissions = setOf(
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_READ,
      PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_READ,
      PosixFilePermission.OTHERS_EXECUTE
    )

    // When
    val mode = permissions.toInt()

    // Then
    assertEquals(0b111101101, mode) // 755
  }

  // ========== TarGz Tests ==========

  @Test
  fun `should create tar gz from directory without top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file1.txt").writeText("content1")
    val subDir = sourceDir.resolve("subdir").createDirectories()
    subDir.resolve("file2.txt").writeText("content2")

    val outputFile = tempDir.resolve("output.tar.gz")
    val tmpDir = tempDir.resolve("tmp")

    // When
    tarGz(sourceDir, outputFile, withTopLevelFolder = false, tmpDir, logger, None)

    // Then
    assertTrue(outputFile.exists())
    assertTrue(outputFile.fileSize() > 0)
  }

  @Test
  fun `should create tar gz from directory with top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file1.txt").writeText("content1")

    val outputFile = tempDir.resolve("output.tar.gz")
    val tmpDir = tempDir.resolve("tmp")

    // When
    tarGz(sourceDir, outputFile, withTopLevelFolder = true, tmpDir, logger, None)

    // Then
    assertTrue(outputFile.exists())
  }

  @Test
  fun `should create tar gz from single file`() {
    // Given
    val sourceFile = tempDir.resolve("file.txt").apply { writeText("content") }
    val outputFile = tempDir.resolve("output.tar.gz")
    val tmpDir = tempDir.resolve("tmp")

    // When
    tarGz(sourceFile, outputFile, withTopLevelFolder = false, tmpDir, logger, None)

    // Then
    assertTrue(outputFile.exists())
  }

  @Test
  fun `should return output file path from tarGz`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file.txt").writeText("content")
    val outputFile = tempDir.resolve("output.tar.gz")
    val tmpDir = tempDir.resolve("tmp")

    // When
    val result = tarGz(sourceDir, outputFile, withTopLevelFolder = false, tmpDir, logger, reproducibilityMode = None)

    // Then
    assertEquals(outputFile, result)
  }


  @Test
  fun `should handle symlink with spaces in name`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    val targetFile = sourceDir.resolve("target file.txt").apply { writeText("content") }
    val symlink = sourceDir.resolve("link file.txt")

    symlink.createSymbolicLinkPointingTo(targetFile)

    val outputFile = tempDir.resolve("output.tar.gz")
    val tmpDir = tempDir.resolve("tmp")

    // When
    tarGz(sourceDir, outputFile, withTopLevelFolder = false, tmpDir, logger, reproducibilityMode = None)

    // Then
    val extractDir = tempDir.resolve("extracted")
    val extractTmpDir = tempDir.resolve("tmp2")
    extractTarGz(outputFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, tmpDir, logger)

    val extractedLink = extractDir.resolve("link file.txt")
    assertTrue(extractedLink.exists())
    assertTrue(extractedLink.isSymbolicLink())
  }

  // ========== TarZst Tests ==========

  @Test
  fun `should create tar zst from directory without top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file1.txt").writeText("content1")

    val outputFile = tempDir.resolve("output.tar.zst")
    val tmpDir = tempDir.resolve("tmp")

    // When
    tarZst(sourceDir, outputFile, withTopLevelFolder = false, tmpDir, logger, reproducibilityMode = None)

    // Then
    assertTrue(outputFile.exists())
    assertTrue(outputFile.fileSize() > 0)
  }

  @Test
  fun `should create tar zst from directory with top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file1.txt").writeText("content1")

    val outputFile = tempDir.resolve("output.tar.zst")
    val tmpDir = tempDir.resolve("tmp")

    // When
    tarZst(sourceDir, outputFile, withTopLevelFolder = true, tmpDir, logger, reproducibilityMode = None)

    // Then
    assertTrue(outputFile.exists())
  }

  @Test
  fun `should create tar zst from a single file without top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source.txt").apply { writeText("content") }
    val tmpDir = tempDir.resolve("tmp")
    val outputFile = tempDir.resolve("output.tar.zst")

    val extractDir = tempDir.resolve("extracted")
    val extractTmpDir = tempDir.resolve("tmp2")

    // When
    tarZst(sourceDir, outputFile, withTopLevelFolder = false, tmpDir, logger, reproducibilityMode = None)

    extractTarZst(outputFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, extractTmpDir, logger)

    // Then
    assertTrue(extractDir.resolve("source.txt").exists())
    assertEquals(extractDir.resolve("source.txt").readText(), "content")
  }

  // ========== ExtractTarGz Tests ==========

  @Test
  fun `should extract tar gz without stripping top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file.txt").writeText("content")
    val tarGz = tempDir.resolve("archive.tar.gz")
    val tmpDir = tempDir.resolve("tmp1")
    tarGz(sourceDir, tarGz, withTopLevelFolder = true, tmpDir, logger, reproducibilityMode = None)

    val destination = tempDir.resolve("extracted")
    val extractTmpDir = tempDir.resolve("tmp2")

    // When
    extractTarGz(tarGz, destination, stripTopLevelFolder = false, cleanDestination = false, extractTmpDir, logger)

    // Then
    assertTrue(destination.resolve("source/file.txt").exists())
    assertEquals("content", destination.resolve("source/file.txt").readText())
  }

  @Test
  fun `should extract tar gz with stripping top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file.txt").writeText("content")
    val tarGz = tempDir.resolve("archive.tar.gz")
    val tmpDir = tempDir.resolve("tmp1")
    tarGz(sourceDir, tarGz, withTopLevelFolder = true, tmpDir, logger, reproducibilityMode = None)

    val destination = tempDir.resolve("extracted")
    val extractTmpDir = tempDir.resolve("tmp2")

    // When
    extractTarGz(tarGz, destination, stripTopLevelFolder = true, cleanDestination = false, extractTmpDir, logger)

    // Then
    assertTrue(destination.resolve("file.txt").exists())
    assertEquals("content", destination.resolve("file.txt").readText())
  }

  @Test
  fun `should clean destination when cleanDestination is true`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("new.txt").writeText("new")
    val tarGz = tempDir.resolve("archive.tar.gz")
    val tmpDir = tempDir.resolve("tmp1")
    tarGz(sourceDir, tarGz, withTopLevelFolder = false, tmpDir, logger, reproducibilityMode = None)

    val destination = tempDir.resolve("extracted").createDirectories()
    destination.resolve("old.txt").writeText("old")

    val extractTmpDir = tempDir.resolve("tmp2")

    // When
    extractTarGz(tarGz, destination, stripTopLevelFolder = false, cleanDestination = true, extractTmpDir, logger)

    // Then
    assertTrue(destination.resolve("new.txt").exists())
    assertFalse(destination.resolve("old.txt").exists())
  }

  @Test
  fun `should preserve file structure with nested directories in tar gz`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    val nested = sourceDir.resolve("level1/level2").createDirectories()
    nested.resolve("deep.txt").writeText("deep content")
    val tarGz = tempDir.resolve("single_symlink.tar.gz")
    val tmpDir = tempDir.resolve("tmp1")
    tarGz(sourceDir, tarGz, withTopLevelFolder = false, tmpDir, logger, reproducibilityMode = None)

    val destination = tempDir.resolve("extracted")
    val extractTmpDir = tempDir.resolve("tmp2")

    // When
    extractTarGz(tarGz, destination, stripTopLevelFolder = false, cleanDestination = false, extractTmpDir, logger)

    // Then
    assertTrue(destination.resolve("level1/level2/deep.txt").exists())
    assertEquals("deep content", destination.resolve("level1/level2/deep.txt").readText())
  }

  @Test
  fun `should extract symlink from assets tar gz`() {
    val extractDir = tempDir.resolve("extracted")
    val zipFile = getFileFromResources("archive/single_symlink.tar.gz")
    val tmpDir = tempDir.resolve("tmp")

    extractTarGz(zipFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, tmpDir, logger)

    val symlink = extractDir.resolve("link.txt")
    assertTrue(symlink.exists())
    assertTrue(symlink.isSymbolicLink())
  }

  @Test
  fun `should extract empty folder from assets tar gz`() {
    val extractDir = tempDir.resolve("extracted")
    val zipFile = getFileFromResources("archive/empty_folder.tar.gz")
    val tmpDir = tempDir.resolve("tmp")

    extractTarGz(zipFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, tmpDir, logger)

    assertTrue(extractDir.resolve("archive").exists())
  }


  @Test
  fun `should handle mixed content in tar gz`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()

    // Regular files
    sourceDir.resolve("text.txt").writeText("text")
    sourceDir.resolve("binary.bin").writeBytes(ByteArray(100) { it.toByte() })

    // Directories
    val nestedDir = sourceDir.resolve("nested/dir").createDirectories()
    nestedDir.resolve("nested.txt").writeText("nested")

    val target = sourceDir.resolve("target.txt").apply { writeText("target") }
    sourceDir.resolve("link.txt").createSymbolicLinkPointingTo(target)

    val outputFile = tempDir.resolve("output.tar.gz")
    val tmpDir = tempDir.resolve("tmp")

    // When
    tarGz(sourceDir, outputFile, withTopLevelFolder = false, tmpDir, logger, reproducibilityMode = None)

    // Then
    val extractDir = tempDir.resolve("extracted")
    val extractTmpDir = tempDir.resolve("tmp2")
    extractTarGz(outputFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, extractTmpDir, logger)

    assertTrue(extractDir.resolve("text.txt").exists())
    assertTrue(extractDir.resolve("binary.bin").exists())
    assertTrue(extractDir.resolve("nested/dir/nested.txt").exists())
    assertEquals("text", extractDir.resolve("text.txt").readText())
    assertEquals("nested", extractDir.resolve("nested/dir/nested.txt").readText())
    val symlink = extractDir.resolve("link.txt")
    assertTrue(symlink.exists())
    assertTrue(symlink.isSymbolicLink())
  }

  @Test
  fun `should handle mixed content in assets tar gz`() {
    // Given
    val archiveFile = getFileFromResources("archive/mixed.tar.gz")
    val tmpDir = tempDir.resolve("tmp")

    // Then
    val extractDir = tempDir.resolve("extracted")
    extractTarGz(archiveFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, tmpDir, logger)

    assertTrue(extractDir.resolve("text.txt").exists())
    assertTrue(extractDir.resolve("binary.bin").exists())
    assertTrue(extractDir.resolve("nested/nested.txt").exists())
    assertEquals("abc", extractDir.resolve("text.txt").readText().trim())
    assertEquals("nested", extractDir.resolve("nested/nested.txt").readText().trim())
    val symlink = extractDir.resolve("link.txt")
    assertTrue(symlink.exists())
    assertTrue(symlink.isSymbolicLink())
  }
  // ========== ExtractTarZst Tests ==========

  @Test
  fun `should extract tar zst without stripping top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file.txt").writeText("zst content")
    val tarZst = tempDir.resolve("archive.tar.zst")
    val tmpDir = tempDir.resolve("tmp1")
    tarZst(sourceDir, tarZst, withTopLevelFolder = true, tmpDir, logger, reproducibilityMode = None)

    val destination = tempDir.resolve("extracted")
    val extractTmpDir = tempDir.resolve("tmp2")

    // When
    extractTarZst(tarZst, destination, stripTopLevelFolder = false, cleanDestination = false, extractTmpDir, logger)

    // Then
    assertTrue(destination.resolve("source/file.txt").exists())
    assertEquals("zst content", destination.resolve("source/file.txt").readText())
  }

  @Test
  fun `should extract tar zst with stripping top level folder`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file.txt").writeText("zst content")
    val tarZst = tempDir.resolve("archive.tar.zst")
    val tmpDir = tempDir.resolve("tmp1")
    tarZst(sourceDir, tarZst, withTopLevelFolder = true, tmpDir, logger, reproducibilityMode = None)

    val destination = tempDir.resolve("extracted")
    val extractTmpDir = tempDir.resolve("tmp2")

    // When
    extractTarZst(tarZst, destination, stripTopLevelFolder = true, cleanDestination = false, extractTmpDir, logger)

    // Then
    assertTrue(destination.resolve("file.txt").exists())
    assertEquals("zst content", destination.resolve("file.txt").readText())
  }

  @Test
  fun `GIVEN last modified time changed WHEN extract tar zst  THEN extraction is Reproducible`() {
    val sourceDir = tempDir.resolve("source").createDirectories()
    val file = sourceDir.resolve("file.txt").apply { writeText("zst content") }
    val archive1 = tempDir.resolve("archive1.tar.zst")
    val archive2 = tempDir.resolve("archive2.tar.zst")
    val tmpDir = tempDir.resolve("tmp1")

    file.setLastModifiedTime(FileTime.fromMillis(0L))
    val tarZst1 = tarZst(file, archive1, withTopLevelFolder = false, temporaryDir = tmpDir, logger, Reproducible(Preserve))
    val firstSha256 = sha256(tarZst1.readBytesForSha256())

    file.setLastModifiedTime(FileTime.fromMillis(System.currentTimeMillis()))
    val tarZst2 = tarZst(file, archive2, withTopLevelFolder = false, temporaryDir = tmpDir, logger, Reproducible(Preserve))
    val secondSha256 = sha256(tarZst2.readBytesForSha256())

    assertEquals(firstSha256, secondSha256, "tarZst compression should be Reproducible and not change no matter of system meta information")
  }

  @Test
  fun `GIVEN order is random WHEN extract tar zst  THEN order is alphabetical`() {
    val sourceDir = tempDir.resolve("source").createDirectories()
    val fileC = sourceDir.resolve("C.txt").apply { writeText("C") }
    val dirA = sourceDir.resolve("A").apply { createDirectories() }
    val dirAFileA = dirA.resolve("a.txt").apply { writeText("a") }
    val fileB = sourceDir.resolve("B.txt").apply { writeText("B") }
    val fileA = sourceDir.resolve("A.txt").apply { writeText("A") }
    val archiveFile = tempDir.resolve("archive.tar.zst")
    val tmpDir = tempDir.resolve("tmp1")
    val destination = tempDir.resolve("dest")

    val tarZstArchive =
      tarZst(sourceDir, archiveFile, withTopLevelFolder = false, temporaryDir = tmpDir, logger, Reproducible(permissionOption = Override()))
    extractTarZst(tarZstArchive, destination, stripTopLevelFolder = false, cleanDestination = false, tmpDir, logger)

    archiveFile.inputStream().buffered().use { bufferedInputStream ->
      ZstdCompressorInputStream(bufferedInputStream).use { zstdInputStream ->
        TarArchiveInputStream(zstdInputStream).use { tarInputStream ->
          var entry = tarInputStream.nextEntry
          val entries = mutableListOf<String>()
          while (entry != null) {
            entries.add(entry.name)
            entry = tarInputStream.nextEntry
          }

          assertEquals("./A.txt", entries[0])
          assertEquals("./A/a.txt", entries[1])
          assertEquals("./B.txt", entries[2])
          assertEquals("./C.txt", entries[3])
        }
      }
    }
  }

  @Test
  fun `GIVEN permissions changed WHEN extract tar zst  THEN extraction is Reproducible`() {
    assumePosixFileSystem()

    val sourceDir = tempDir.resolve("source").createDirectories()
    val file = sourceDir.resolve("file.txt").apply { writeText("zst content") }
    val archive1 = tempDir.resolve("archive1.tar.zst")
    val archive2 = tempDir.resolve("archive2.tar.zst")
    val tmpDir = tempDir.resolve("tmp1")
    archive1.deleteIfExists()
    archive2.deleteIfExists()


    val onlyOwnerReadPermissions = setOf(PosixFilePermission.OWNER_READ)
    val readPermissions = setOf(PosixFilePermission.OTHERS_READ, PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ)
    file.setPosixFilePermissions(readPermissions)
    val tarZst1 = tarZst(file, archive1, withTopLevelFolder = false, temporaryDir = tmpDir, logger, Reproducible(Override()))
    val firstSha256 = sha256(tarZst1.readBytesForSha256())

    file.setPosixFilePermissions(onlyOwnerReadPermissions)
    val tarZst2 = tarZst(file, archive2, withTopLevelFolder = false, temporaryDir = tmpDir, logger, Reproducible(Override()))
    val secondSha256 = sha256(tarZst2.readBytesForSha256())

    assertEquals(firstSha256, secondSha256, "tarZst compression should be Reproducible and not change no matter of system meta information")
  }

  // ========== Edge Cases and Error Handling ==========

  @Test
  fun `should handle large files`() {
    // Given
    val sourceFile = tempDir.resolve("large.txt")
    val largeContent = "x".repeat(1024 * 1024) // 1MB
    sourceFile.writeText(largeContent)

    val gzFile = tempDir.resolve("large.gz")

    // When
    gz(gzFile, sourceFile)

    // Then
    assertTrue(gzFile.exists())
    assertTrue(gzFile.fileSize() > 0)
    assertTrue(gzFile.fileSize() < largeContent.length) // Should be compressed
  }

  @Test
  fun `should overwrite existing archive`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    sourceDir.resolve("file1.txt").writeText("content1")
    val targetZip = tempDir.resolve("output.zip")

    // Create first archive
    zip(targetZip, sourceDir, withTopLevelFolder = false)
    val firstSize = targetZip.fileSize()

    // Modify source
    sourceDir.resolve("file2.txt").writeText("content2")

    // When
    zip(targetZip, sourceDir, withTopLevelFolder = false)

    // Then
    val secondSize = targetZip.fileSize()
    assertTrue(secondSize > firstSize)
    val entries = readZipEntryNames(targetZip)
    assertEquals(2, entries.size)
  }

  @Test
  fun `should handle deeply nested directory structures`() {
    // Given
    val sourceDir = tempDir.resolve("source").createDirectories()
    val deepDir = sourceDir.resolve("a/b/c/d/e/f/g/h").createDirectories()
    deepDir.resolve("deep.txt").writeText("deep")
    val targetZip = tempDir.resolve("output.zip")

    // When
    zip(targetZip, sourceDir, withTopLevelFolder = false)

    // Then
    val destination = tempDir.resolve("extracted")
    extractZip(targetZip,
               destination,
               stripTopLevelFolder = false,
               cleanDestination = false,
               temporaryDir = tempDir.resolve("tmp"),
               logger = logger)
    assertTrue(destination.resolve("a/b/c/d/e/f/g/h/deep.txt").exists())
    assertEquals("deep", destination.resolve("a/b/c/d/e/f/g/h/deep.txt").readText())
  }

  @Test
  fun `should handle empty sequence for zip`() {
    // Given
    val emptySequence = emptySequence<Pair<String, ByteArrayInputStream>>()
    val targetZip = tempDir.resolve("empty.zip")

    // When
    zip(targetZip, emptySequence)

    // Then
    assertTrue(targetZip.exists())
    val entries = readZipEntryNames(targetZip)
    assertEquals(0, entries.size)
  }

  @Test
  fun `tarGz and extractTarGz should preserve file permissions when ReproducibilityMode=NONE`() {
    assumePosixFileSystem()

    // Given
    val sourceDir = tempDir.resolve("src").createDirectories()
    val file = sourceDir.resolve("script.sh").apply { writeText("echo hi") }
    val expectedPerms = setOf(
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.GROUP_READ,
      PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_READ,
      PosixFilePermission.OTHERS_EXECUTE,
    )

    file.setPosixFilePermissions(expectedPerms)

    val archive = tempDir.resolve("perm.tar.gz")
    val tmp = tempDir.resolve("tmp")
    tarGz(sourceDir, archive, withTopLevelFolder = false, tmp, logger, None)

    val dest = tempDir.resolve("out-tar")
    extractTarGz(archive, dest, stripTopLevelFolder = false, cleanDestination = false, tmp, logger)

    // Then
    val extracted = dest.resolve("script.sh")
    assertTrue(extracted.exists())
    val actualPerms = extracted.getPosixFilePermissions()
    assertEquals(expectedPerms, actualPerms)
  }

  @Test
  fun `tarGz and extractTarGz should preserve file permissions when ReproducibilityMode=Reproducible with preserve`() {
    assumePosixFileSystem()

    // Given
    val sourceDir = tempDir.resolve("src").createDirectories()
    val file = sourceDir.resolve("script.sh").apply { writeText("echo hi") }
    val expectedPerms = setOf(
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.GROUP_READ,
      PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_READ,
      PosixFilePermission.OTHERS_EXECUTE,
    )

    file.setPosixFilePermissions(expectedPerms)

    val archive = tempDir.resolve("perm.tar.gz")
    val tmp = tempDir.resolve("tmp")
    tarGz(sourceDir, archive, withTopLevelFolder = false, tmp, logger, Reproducible(Preserve))

    val dest = tempDir.resolve("out-tar")
    extractTarGz(archive, dest, stripTopLevelFolder = false, cleanDestination = false, tmp, logger)

    // Then
    val extracted = dest.resolve("script.sh")
    assertTrue(extracted.exists())
    val actualPerms = extracted.getPosixFilePermissions()
    assertEquals(expectedPerms, actualPerms)
  }

  @Test
  fun `tarGz and extractTarGz should use 0755 file permissions when ReproducibilityMode=Reproducible with override`() {
    assumePosixFileSystem()

    // Given
    val sourceDir = tempDir.resolve("src").createDirectories()
    val file = sourceDir.resolve("script.sh").apply { writeText("echo hi") }
    val defaultPermissions = setOf(
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.OWNER_WRITE,
    )

    val expectedPermissions = setOf(
      PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE,
    )

    file.setPosixFilePermissions(defaultPermissions)

    val archive = tempDir.resolve("perm.tar.gz")
    val tmp = tempDir.resolve("tmp")
    tarGz(sourceDir, archive, withTopLevelFolder = false, tmp, logger, Reproducible(Override()))

    val dest = tempDir.resolve("out-tar")
    extractTarGz(archive, dest, stripTopLevelFolder = false, cleanDestination = false, tmp, logger)

    // Then
    val extracted = dest.resolve("script.sh")
    assertTrue(extracted.exists())
    val actualPerms = extracted.getPosixFilePermissions()
    assertEquals(expectedPermissions, actualPerms)
  }

  @Test
  fun `zip and extractZip should preserve file permissions`() {
    assumePosixFileSystem()

    // Given
    val sourceDir = tempDir.resolve("srcZip").createDirectories()
    val file = sourceDir.resolve("script.sh").apply { writeText("echo hi") }
    val expectedPerms = setOf(
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_READ,
      PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_READ,
      PosixFilePermission.OTHERS_EXECUTE,
    )

    file.setPosixFilePermissions(expectedPerms)

    val archive = tempDir.resolve("script.sh")
    val tmp = tempDir.resolve("tmpZip")
    zip(sourceDir, archive, withTopLevelFolder = false, tmp, logger, reproducibilityMode = None)

    val dest = tempDir.resolve("out-zip")
    extractZip(archive, dest, stripTopLevelFolder = false, cleanDestination = false, tmp, logger)

    // Then
    val extracted = dest.resolve("script.sh")
    assertTrue(extracted.exists())
    val actualPerms = extracted.getPosixFilePermissions()
    assertEquals(expectedPerms, actualPerms)
  }

  @Test
  fun `extractZip should preserve file permissions from assets`() {
    assumePosixFileSystem()

    val extractDir = tempDir.resolve("extracted")
    val zipFile = getFileFromResources("archive/readonly.zip")
    val tmpDir = tempDir.resolve("tmp")

    val expectedPermissions = setOf(
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.GROUP_READ,
      PosixFilePermission.OTHERS_READ,
    )

    extractZip(zipFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, tmpDir, logger)

    val readonlyFile = extractDir.resolve("readonly.txt")
    assertTrue(readonlyFile.exists())
    val actualPerms = readonlyFile.getPosixFilePermissions()
    assertEquals(expectedPermissions, actualPerms)
  }

  @Test
  fun `extractTarGz should preserve file permissions from assets`() {
    assumePosixFileSystem()

    val extractDir = tempDir.resolve("extracted")
    val zipFile = getFileFromResources("archive/readonly.tar.gz")
    val tmpDir = tempDir.resolve("tmp")

    val expectedPermissions = setOf(
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.GROUP_READ,
      PosixFilePermission.OTHERS_READ,
    )

    extractTarGz(zipFile, extractDir, stripTopLevelFolder = false, cleanDestination = false, tmpDir, logger)

    val readonlyFile = extractDir.resolve("readonly.txt")
    assertTrue(readonlyFile.exists())
    val actualPerms = readonlyFile.getPosixFilePermissions()
    assertEquals(expectedPermissions, actualPerms)
  }

  // ========== Helper Functions ==========

  private fun readZipEntries(zipFile: Path): Map<String, String> {
    val result = mutableMapOf<String, String>()
    ZipInputStream(zipFile.inputStream()).use { zipIn ->
      var entry = zipIn.nextEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          result[entry.name] = zipIn.readBytes().decodeToString()
        }
        zipIn.closeEntry()
        entry = zipIn.nextEntry
      }
    }
    return result
  }

  private fun readZipEntryNames(zipFile: Path): List<String> {
    val result = mutableListOf<String>()
    ZipInputStream(zipFile.inputStream()).use { zipIn ->
      var entry = zipIn.nextEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          result.add(entry.name)
        }
        zipIn.closeEntry()
        entry = zipIn.nextEntry
      }
    }
    return result
  }

  private fun createTestZip(zipFile: Path, files: Map<String, String>) {
    ZipOutputStream(zipFile.outputStream()).use { zipOut ->
      files.forEach { (name, content) ->
        zipOut.putNextEntry(ZipEntry(name))
        zipOut.write(content.toByteArray())
        zipOut.closeEntry()
      }
    }
  }

  private fun getFileFromResources(path: String): Path {
    return Paths.get(javaClass.classLoader.getResource(path)!!.toURI())
  }


  private fun assumePosixFileSystem() {
    assumeTrue(tempDir.isPosixSupported(), "Posix permissions are not supported on this system. Skipping test.")
  }

  private fun Path.isPosixSupported(): Boolean {
    return try {
      Files.getFileStore(this).supportsFileAttributeView("posix")
    }
    catch (_: IOException) {
      // Could not determine if a file system supports POSIX attributes, assume it does not
      false
    }
  }
}