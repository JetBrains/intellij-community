// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.formatting.commandLine

import com.intellij.formatting.commandLine.FileSetFormatter
import java.io.File


class FileSetFormatterTest : FileSetCodeStyleProcessorTestBase() {

  override fun setUp() {
    super.setUp()
    processor = FileSetFormatter(codeStyleSettings!!, messageOutput!!, true).also {
      it.addFileMask("*.java")
    }
  }

  fun testFormat() {
    processor?.apply {
      val sourceDir = createSourceDir("original")
      addEntry(sourceDir.canonicalPath)
      processFiles()
      compareDirs(File(BASE_PATH).resolve("expected"), sourceDir)
    }
  }

}
