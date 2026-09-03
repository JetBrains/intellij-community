// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.util.io.DigestUtil.md5
import com.intellij.util.io.DigestUtil.sha1
import com.intellij.util.io.DigestUtil.sha256
import com.intellij.util.io.DigestUtil.sha512
import com.intellij.util.io.bytesToHex
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.blockingUse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.name
import kotlin.io.path.readLines

@Internal
class Checksums private constructor(
  val path: Path,
  private val results: Map<Algorithm, String>,
) {
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

  val sha1sum: String get() = results.getValue(Algorithm.SHA1)
  val sha256sum: String get() = results.getValue(Algorithm.SHA256)
  val sha512sum: String get() = results.getValue(Algorithm.SHA512)
  val md5sum: String get() = results.getValue(Algorithm.MD5)

  companion object {
    fun compute(file: Path, vararg algorithms: Algorithm = arrayOf(Algorithm.SHA1, Algorithm.SHA256)): Checksums {
      require(algorithms.any())
      val digests = algorithms.associateWith { it.newDigest() }
      val buffer = ByteArray(512 * 1024)
      Files.newInputStream(file).use { input ->
        while (true) {
          val sz = input.read(buffer)
          if (sz <= 0) {
            break
          }
          for (digest in digests.values) {
            digest.update(buffer, 0, sz)
          }
        }
      }
      return Checksums(
        path = file,
        results = digests.mapValues { entry ->
          bytesToHex(entry.value.digest())
        },
      )
    }
  }

  fun verifyOrWriteChecksumFile(algorithm: Algorithm, withFileName: Boolean = true): Path {
    val extension = algorithm.name.lowercase()
    return spanBuilder("checksum").setAttribute("file", "$path").setAttribute("extension", extension).blockingUse {
      val checksum = getChecksumValue(algorithm)
      val checksumFile = path.resolveSibling("${path.name}.$extension")
      if (Files.exists(checksumFile)) {
        verifyChecksumFile(file = path, checksumFile = checksumFile, expectedChecksum = checksum, withFileName = withFileName)
      }
      else {
        Files.writeString(checksumFile, createChecksumFileContent(file = path, checksum = checksum, withFileName = withFileName))
      }
      checksumFile
    }
  }

  private fun getChecksumValue(algorithm: Algorithm): String {
    return results.get(algorithm) ?: throw ChecksumValueMissing("${algorithm.digestAlgorithm} value is not calculated")
  }
}

private fun verifyChecksumFile(file: Path, checksumFile: Path, expectedChecksum: String, withFileName: Boolean) {
  val parsedChecksumFile = readChecksumFile(checksumFile)
  if (parsedChecksumFile.checksum != expectedChecksum) {
    throw ChecksumMismatch("The supplied file $checksumFile content mismatch: '${parsedChecksumFile.checksum}' != '$expectedChecksum'")
  }
  if (withFileName && parsedChecksumFile.fileName != file.name) {
    throw ChecksumMismatch("The supplied file $checksumFile name mismatch: '${parsedChecksumFile.fileNameToken}' != '${file.name}'")
  }
}

private fun createChecksumFileContent(file: Path, checksum: String, withFileName: Boolean): String {
  return if (withFileName) "$checksum *${file.name}" else checksum
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
  @JvmField val checksum: String?,
  @JvmField val fileNameToken: String?,
) {
  val fileName: String?
    get() = fileNameToken?.trimStart('*')
}

private class ChecksumValueMissing(message: String) : RuntimeException(message)

@VisibleForTesting
class ChecksumMismatch(message: String) : RuntimeException(message)
