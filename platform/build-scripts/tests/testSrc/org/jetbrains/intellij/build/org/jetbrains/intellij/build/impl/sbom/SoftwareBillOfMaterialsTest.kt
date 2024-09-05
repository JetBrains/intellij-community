// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.org.jetbrains.intellij.build.impl.sbom

import com.intellij.util.io.write
import org.jetbrains.intellij.build.impl.sbom.SoftwareBillOfMaterialsImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SoftwareBillOfMaterialsTest {
  @Test
  fun checksums(@TempDir tempDir: Path) {
    val file = tempDir.resolve("file.txt")
    file.write("test")
    val checksums = SoftwareBillOfMaterialsImpl.Checksums(file)
    assert(checksums.sha1sum == "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3") {
      "Unexpected SHA1 checksum '${checksums.sha1sum}'"
    }
    assert(checksums.sha256sum == "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08") {
      "Unexpected SHA256 checksum '${checksums.sha256sum}'"
    }
  }
}