// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView

internal fun deleteDir(startDir: Path) {
  Files.walkFileTree(startDir, object : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      deleteFile(file)
      return FileVisitResult.CONTINUE
    }

    override fun postVisitDirectory(dir: Path, exception: IOException?): FileVisitResult {
      if (exception != null) {
        throw exception
      }

      Files.deleteIfExists(dir)
      return FileVisitResult.CONTINUE
    }
  })
}

private fun deleteFile(file: Path) {
  // repeated delete is required for bad OS like Windows
  val maxAttemptCount = 10
  var attemptCount = 0
  while (true) {
    try {
      Files.deleteIfExists(file)
      return
    }
    catch (e: IOException) {
      if (++attemptCount == maxAttemptCount) {
        throw e
      }

      if (e is AccessDeniedException && System.getProperty("os.name").startsWith("windows", ignoreCase = true)) {
        val view = Files.getFileAttributeView(file, DosFileAttributeView::class.java)
        if (view != null && view.readAttributes().isReadOnly) {
          view.setReadOnly(false)
        }
      }

      try {
        Thread.sleep(10)
      }
      catch (ignored: InterruptedException) {
        throw e
      }
    }
  }
}