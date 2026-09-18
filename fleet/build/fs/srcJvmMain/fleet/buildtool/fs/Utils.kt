package fleet.buildtool.fs

import java.nio.charset.Charset
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readText

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
