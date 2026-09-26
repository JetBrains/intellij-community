package fleet.buildtool.fs

import java.nio.charset.Charset
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.inputStream
import kotlin.io.path.readText

val Path.pathString: String get() = toString()

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

/**
 * SHA-256 of the raw content of the file at [this] path.
 *
 * The file is read as a stream, so the memory use does not depend on the file size.
 */
fun Path.sha256(): String {
  val md = MessageDigest.getInstance("SHA-256")
  inputStream().use { input ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    generateSequence { input.read(buffer).takeIf { it >= 0 } }.forEach { md.update(buffer, 0, it) }
  }
  return md.digest().toHex()
}

private fun ByteArray.toHex(): String = fold(StringBuilder()) { sb, it -> sb.append("%02x".format(it)) }.toString()

/**
 * Read content of file at [this] path and normalize it for reliable cross-platform SHA256 hashing
 *
 * Line endings will be normalized to '\n' to ensure the hash result produces identical result on Windows and UNIX.
 */
fun Path.readBytesForSha256(charset: Charset = Charsets.UTF_8): ByteArray = readText(charset).normaliseLineSeparators().toByteArray(charset)

private fun String.normaliseLineSeparators() = lineSequence().joinToString("\n")
