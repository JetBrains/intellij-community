// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.io.DigestUtil.md5
import com.intellij.util.io.DigestUtil.sha1
import com.intellij.util.io.DigestUtil.sha256
import com.intellij.util.io.DigestUtil.sha512
import com.intellij.util.io.bytesToHex
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.writeText

@ApiStatus.Internal
class Checksums(val path: Path, vararg algorithms: Algorithm = arrayOf(Algorithm.SHA1, Algorithm.SHA256)) {
  enum class Algorithm(
    private val createDigest: () -> MessageDigest,
  ) {
    SHA1(createDigest = { sha1() }),
    SHA256(createDigest = { sha256() }),
    SHA512(createDigest = { sha512() }),
    MD5(createDigest = { md5() }),
    ;

    val digestAlgorithm: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
      createDigest().algorithm
    }

    fun newDigest(): MessageDigest = createDigest()
  }

  private val results: Map<Algorithm, String>
  val sha1sum: String get() = results.getValue(Algorithm.SHA1)
  val sha256sum: String get() = results.getValue(Algorithm.SHA256)
  val sha512sum: String get() = results.getValue(Algorithm.SHA512)
  val md5sum: String get() = results.getValue(Algorithm.MD5)

  init {
    require(algorithms.any())
    val digests = algorithms.associateWith { it.newDigest() }
    val buffer = ByteArray(512 * 1024)
    Files.newInputStream(path).use {
      while (true) {
        val sz = it.read(buffer)
        if (sz <= 0) {
          break
        }
        for (digest in digests.values) {
          digest.update(buffer, 0, sz)
        }
      }
      results = digests.mapValues {
        bytesToHex(it.value.digest())
      }
    }
  }

  suspend fun verifyOrWriteChecksumFile(algorithm: Algorithm, withFileName: Boolean = true): Path {
    val extension = algorithm.name.lowercase()
    return spanBuilder("checksum").setAttribute("file", "$path").setAttribute("extension", extension).use {
      val checksum = getChecksumValue(algorithm)
      val checksumFile = path.resolveSibling("${path.name}.$extension")
      if (checksumFile.exists()) {
        verifyChecksumFile(checksumFile = checksumFile, expectedChecksum = checksum, withFileName = withFileName)
      }
      else {
        checksumFile.writeText(createChecksumFileContent(checksum = checksum, withFileName = withFileName))
      }
      checksumFile
    }
  }

  private fun getChecksumValue(algorithm: Algorithm): String {
    return results.get(algorithm) ?: throw ChecksumValueMissing("${algorithm.digestAlgorithm} value is not calculated")
  }

  private fun verifyChecksumFile(checksumFile: Path, expectedChecksum: String, withFileName: Boolean) {
    val parsedChecksumFile = readChecksumFile(checksumFile)
    if (parsedChecksumFile.checksum != expectedChecksum) {
      throw ChecksumMismatch("The supplied file $checksumFile content mismatch: '${parsedChecksumFile.checksum}' != '$expectedChecksum'")
    }
    if (withFileName && parsedChecksumFile.fileName != path.name) {
      throw ChecksumMismatch("The supplied file $checksumFile name mismatch: '${parsedChecksumFile.fileNameToken}' != '${path.name}'")
    }
  }

  private fun createChecksumFileContent(checksum: String, withFileName: Boolean): String {
    return if (withFileName) "$checksum *${path.name}" else checksum
  }

  private fun readChecksumFile(checksumFile: Path): ChecksumFile {
    val tokens = checksumFile.readLines()
      // sha256sum output is a checksum followed by a mode marker ('*' or ' ') and the supplied file name.
      .asSequence()
      .flatMap { it.splitToSequence(' ') }
      .filter { it.isNotEmpty() }
      .toList()
    return ChecksumFile(
      checksum = tokens.firstOrNull(),
      fileNameToken = tokens.drop(1).lastOrNull(),
    )
  }

  private data class ChecksumFile(
    val checksum: String?,
    val fileNameToken: String?,
  ) {
    val fileName: String?
      get() = fileNameToken?.trimStart('*')
  }
}

class ChecksumValueMissing(message: String) : RuntimeException(message)

@VisibleForTesting
class ChecksumMismatch(message: String) : RuntimeException(message)
