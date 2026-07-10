package fleet.buildtool.fs

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UtilsTest {
  private lateinit var tempDir: Path

  @BeforeTest
  fun setUp() {
    tempDir = Files.createTempDirectory("utils-test")
  }

  @OptIn(ExperimentalPathApi::class)
  @AfterTest
  fun tearDown() {
    tempDir.deleteRecursively()
  }

  @Test
  fun `sha256 should not be modified as it will break dockApiVersion generation`() {
    assertEquals(
      "3202d841030f0606d93ea9e89aa06261a784615c961c94bfd92286d30538e8c8",
      sha256(resourceAsFile("example-abi-lf.txt").readBytesForSha256())
    )
  }

  @Test
  fun `sha256 should be agnostic of line endings`() {
    val expectedHash = "3202d841030f0606d93ea9e89aa06261a784615c961c94bfd92286d30538e8c8"
    assertEquals(expectedHash, sha256(resourceAsFile("example-abi-lf.txt").readBytesForSha256()), "LF line ending file must produce correct hash")
    assertEquals(expectedHash, sha256(resourceAsFile("example-abi-crlf.txt").readBytesForSha256()), "CRLF line ending file must produce correct hash")
    assertEquals(expectedHash, sha256(resourceAsFile("example-abi-cr.txt").readBytesForSha256()), "CR line ending file must produce correct hash")
  }

  @Test
  fun `should have no action on LF`() {
    val lfFile = resourceAsFile("example-abi-lf.txt").readBytes()
    assertContentEquals(lfFile, ByteBuffer.wrap(lfFile).normaliseLineSeparators().array(), "should not have modified the content")
  }

  @Test
  fun `should normalize CRLF`() {
    val lfFile = resourceAsFile("example-abi-lf.txt").readBytes()
    assertContentEquals(lfFile, ByteBuffer.wrap(resourceAsFile("example-abi-crlf.txt").readBytes()).normaliseLineSeparators().array(), "should not contain LF")
  }

  @Test
  fun `should normalize CR `() {
    val lfFile = resourceAsFile("example-abi-lf.txt").readBytes()
    assertContentEquals(lfFile, ByteBuffer.wrap(resourceAsFile("example-abi-cr.txt").readBytes()).normaliseLineSeparators().array(), "should not contain CR")
  }

  private fun resourceAsFile(filename: String): Path {
    val target = tempDir.resolve(filename)
    target.parent?.createDirectories()
    object {}.javaClass.classLoader.getResourceAsStream(filename).let {
      input -> checkNotNull(input) { "Resource not found: $filename" }
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
    }
    return target
  }
}