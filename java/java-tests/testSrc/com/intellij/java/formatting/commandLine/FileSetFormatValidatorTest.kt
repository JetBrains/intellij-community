// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.formatting.commandLine

import com.intellij.formatting.commandLine.FileSetFormatValidator
import junit.framework.TestCase
import java.io.File


class FileSetFormatValidatorTest : FileSetCodeStyleProcessorTestBase() {

  override fun setUp() {
    super.setUp()
    processor = FileSetFormatValidator(codeStyleSettings!!, messageOutput!!, true).also {
      it.addFileMask("*.java")
    }
  }

  fun testFormatDryRun_needsFormatting() {
    processor?.apply {
      val sourceDir = createSourceDir("original")
      addEntry(sourceDir.canonicalPath)
      processFiles()
      compareDirs(File(BASE_PATH).resolve("original"), sourceDir) // No modifications expected
      assertNotSame(processed, succeeded)
    }
  }

  fun testFormatDryRun_wellFormatted() {
    processor?.apply {
      val sourceDir = createSourceDir("expected")
      addEntry(sourceDir.canonicalPath)
      processFiles()
      compareDirs(File(BASE_PATH).resolve("expected"), sourceDir) // No modifications expected
      TestCase.assertEquals(processed, succeeded)
    }
  }

}
