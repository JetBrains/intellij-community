// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.io.DigestUtil.sha1
import com.intellij.util.io.DigestUtil.sha256
import com.intellij.util.io.bytesToHex
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

@ApiStatus.Internal
class Checksums(val path: Path, vararg algorithms: MessageDigest = arrayOf(sha1(), sha256())) {
  private val results: Map<String, String>
  val sha1sum: String get() = results.getValue("SHA-1")
  val sha256sum: String get() = results.getValue("SHA-256")
  val sha512sum: String get() = results.getValue("SHA-512")
  val md5sum: String get() = results.getValue("MD5")

  init {
    require(algorithms.any())
    val buffer = ByteArray(512 * 1024)
    Files.newInputStream(path).use {
      while (true) {
        val sz = it.read(buffer)
        if (sz <= 0) {
          break
        }
        for (algorithm in algorithms) {
          algorithm.update(buffer, 0, sz)
        }
      }
      results = algorithms.associate {
        it.algorithm to bytesToHex(it.digest())
      }
    }
  }
}
