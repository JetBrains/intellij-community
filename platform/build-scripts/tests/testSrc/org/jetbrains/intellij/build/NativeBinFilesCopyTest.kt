// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.copyNativeBinDir
import org.jetbrains.intellij.build.impl.copyNativeBinFileToDir
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class NativeBinFilesCopyTest {
  @Test
  fun `a repeated native bin layout replaces files and reports current outputs`(@TempDir tempDir: Path) {
    val directSource = tempDir.resolve("download/restarter").also {
      it.parent.createDirectories()
      it.writeText("first restarter")
    }
    val treeSource = tempDir.resolve("checkout/bin").createDirectories()
    val treeFile = treeSource.resolve("fsnotifier").also { it.writeText("first watcher") }
    treeSource.resolve("excluded").writeText("excluded")
    val binDir = tempDir.resolve("dist/bin").createDirectories()

    copyNativeBinFileToDir(directSource, binDir)
    copyNativeBinDir(treeSource, binDir, fileFilter = { it.fileName.toString() != "excluded" })

    directSource.writeText("second restarter")
    treeFile.writeText("second watcher")
    val directTarget = copyNativeBinFileToDir(directSource, binDir)
    val treeTargets = copyNativeBinDir(treeSource, binDir, fileFilter = { it.fileName.toString() != "excluded" })

    assertThat(directTarget).isEqualTo(binDir.resolve("restarter"))
    assertThat(directTarget).hasContent("second restarter")
    assertThat(treeTargets).containsExactly(binDir.resolve("fsnotifier"))
    assertThat(binDir.resolve("fsnotifier")).hasContent("second watcher")
    assertThat(binDir.resolve("excluded")).doesNotExist()
  }
}
