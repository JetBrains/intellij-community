package fleet.buildtool.fs

import java.io.File
import java.nio.ByteBuffer
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UtilsTest {
  @Test
  fun `sha256 should not be modified as it will break dockApiVersion generation`() {
    assertEquals(
      "3202d841030f0606d93ea9e89aa06261a784615c961c94bfd92286d30538e8c8",
      sha256(resourceAsFile("/example-abi-lf.txt").toPath().readBytesForSha256())
    )
  }

  @Test
  fun `sha256 should be agnostic of line endings`() {
    val expectedHash = "3202d841030f0606d93ea9e89aa06261a784615c961c94bfd92286d30538e8c8"
    assertEquals(expectedHash, sha256(resourceAsFile("/example-abi-lf.txt").toPath().readBytesForSha256()), "LF line ending file must produce correct hash")
    assertEquals(expectedHash, sha256(resourceAsFile("/example-abi-crlf.txt").toPath().readBytesForSha256()), "CRLF line ending file must produce correct hash")
    assertEquals(expectedHash, sha256(resourceAsFile("/example-abi-cr.txt").toPath().readBytesForSha256()), "CR line ending file must produce correct hash")
  }

  @Test
  fun `should have no action on LF`() {
    val lfFile = resourceAsFile("/example-abi-lf.txt").toPath().readBytes()
    assertContentEquals(lfFile, ByteBuffer.wrap(lfFile).normaliseLineSeparators().array(), "should not have modified the content")
  }

  @Test
  fun `should normalize CRLF`() {
    val lfFile = resourceAsFile("/example-abi-lf.txt").toPath().readBytes()
    assertContentEquals(lfFile, ByteBuffer.wrap(resourceAsFile("/example-abi-crlf.txt").toPath().readBytes()).normaliseLineSeparators().array(), "should not contain LF")
  }

  @Test
  fun `should normalize CR `() {
    val lfFile = resourceAsFile("/example-abi-lf.txt").toPath().readBytes()
    assertContentEquals(lfFile, ByteBuffer.wrap(resourceAsFile("/example-abi-cr.txt").toPath().readBytes()).normaliseLineSeparators().array(), "should not contain CR")
  }
}

private fun resourceAsFile(filename: String) = object {}.javaClass.getResource(filename)?.let { File(it.toURI()) } ?: error(
  "could not find test resource $filename"
)