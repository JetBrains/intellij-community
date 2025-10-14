package fleet.buildtool.fs

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.readText
import kotlin.io.path.setPosixFilePermissions

val Path.pathString: String get() = toString()

fun sha256(bytes: ByteArray): String {
  val md = MessageDigest.getInstance("SHA-256")
  val digest = md.digest(bytes)
  return digest.fold(StringBuilder()) { sb, it -> sb.append("%02x".format(it)) }.toString()
}

/**
 * Read content of file at [this] path and normalize it for reliable cross-platform SHA256 hashing
 *
 * Line endings will be normalized to '\n' to ensure the hash result produces identical result on Windows and UNIX.
 */
fun Path.readBytesForSha256(charset: Charset = Charsets.UTF_8): ByteArray = readText(charset).normaliseLineSeparators().toByteArray(charset)

private fun String.normaliseLineSeparators() = lineSequence().joinToString("\n")

/**
 * Set POSIX file permissions of this [Path] to ensure the output is the same on all platforms.
 *
 * After zip extraction, execute permissions are lost on Windows which creates a different directory output.
 *
 * @this [Path] an extracted path from a ZIP archive, which extraction is intended to be done on all platforms reproducibly.
 */
fun Path.makeReproducibleAfterCrossOSExtraction() {
  try {
    val originalPermissions = getPosixFilePermissions()
    val newPermissions = originalPermissions.minus(setOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE))
    setPosixFilePermissions(newPermissions)
  } catch (_: UnsupportedOperationException) {
    // do nothing
  }
}

/**
 * Inefficiently normalize '\r\n' and '\r' to '\n'.
 */
fun ByteBuffer.normaliseLineSeparators(): ByteBuffer =
  ByteBuffer.wrap(ByteArray(remaining()).also { get(it) }.normaliseLineSeparators())

private fun ByteArray.normaliseLineSeparators(): ByteArray {
  val result = ByteArrayOutputStream(size)
  var i = 0
  while (i < size) {
    when {
      i + 1 < size && this[i] == '\r'.code.toByte() && this[i + 1] == '\n'.code.toByte() -> {}
      this[i] == '\r'.code.toByte() -> result.write('\n'.code)
      else -> result.write(this[i].toInt())
    }
    i++
  }
  return result.toByteArray()
}
