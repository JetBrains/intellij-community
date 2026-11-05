// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class CheckoutExecutableBitsTest {
  @Test
  fun `a copy from the checkout keeps the read and write bits and drops the executable bits`(@TempDir root: Path) {
    assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
    val script = file(root, "bin/compile.sh", "rwxr-xr-x")
    val readOnlyScript = file(root, "bin/run.sh", "r-xr-x---")
    val data = file(root, "data.txt", "rw-r--r--")
    val directory = root.resolve("lib").createDirectories()

    val dropped = dropCheckoutExecutableBits(listOf(script, readOnlyScript, data, directory))

    assertThat(dropped).containsExactly(script, readOnlyScript)
    assertThat(mode(script)).isEqualTo("rw-r--r--")
    assertThat(mode(readOnlyScript)).isEqualTo("r--r-----")
    assertThat(mode(data)).isEqualTo("rw-r--r--")
  }
}

private fun file(root: Path, relativePath: String, mode: String): Path {
  val file = root.resolve(relativePath)
  file.parent.createDirectories()
  file.writeText(relativePath)
  Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(mode))
  return file
}

private fun mode(file: Path): String = PosixFilePermissions.toString(Files.getPosixFilePermissions(file))
