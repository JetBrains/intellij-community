package fleet.buildtool.fs

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
  fun `sha256 should not be modified as it will break S3 upload and bundle hashing`() {
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