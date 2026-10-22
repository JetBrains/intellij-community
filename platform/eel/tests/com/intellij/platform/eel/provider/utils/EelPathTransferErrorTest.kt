// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.sun.nio.file.ExtendedOpenOption
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile

@TestApplication
@Suppress("checkedExceptions")
internal class EelPathTransferErrorTest {
  @Test
  fun `missing source gives IOException`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    shouldThrow<IOException> {
      transfer(tempDir.resolve("missing"), tempDir.resolve("target"))
    }
  }

  /**
   * The source is a file and the target is a directory, so the transfer deletes the target first.
   * A failed delete must give [IOException], not [RuntimeException].
   */
  @Test
  fun `target that cannot be deleted gives IOException`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val source = tempDir.resolve("source").createFile()
    val target = tempDir.resolve("target").createDirectory()
    val child = target.resolve("child").createFile()
    preventDelete(target, child).use {
      shouldThrow<IOException> {
        transfer(source, target)
      }
    }
  }

  private suspend fun transfer(source: Path, target: Path) {
    EelPathTransfer.incrementalWalkingTransfer(source, target, EelFileTransferAttributesStrategy.Copy, null, null)
  }

  /**
   * Makes [child] impossible to delete until the result is closed.
   * Windows: an open handle without the delete share mode. POSIX: a read-only [dir].
   */
  private fun preventDelete(dir: Path, child: Path): AutoCloseable {
    if (SystemInfoRt.isWindows) {
      return FileChannel.open(child, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)
    }
    val permissions = Files.getPosixFilePermissions(dir)
    Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-x------"))
    val restore = AutoCloseable { Files.setPosixFilePermissions(dir, permissions) }
    if (Files.isWritable(dir)) {
      restore.close()
      assumeFalse(true, "the user can write to a read-only directory, probably root")
    }
    return restore
  }
}
