// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.formatting.commandLine

import com.intellij.formatting.commandLine.FileSetFormatValidator
import junit.framework.TestCase
import java.io.File


class FileSetFormatValidatorTest : FileSetCodeStyleProcessorTestBase() {

  fun testFormatDryRun_needsFormatting() {
    FileSetFormatValidator(messageOutput!!, true, defaultCodeStyle = codeStyleSettings).use {
      it.addFileMask(Regex(".*\\.java"))
      val sourceDir = createSourceDir("baseTest/original")
      it.addEntry(sourceDir.canonicalPath)
      it.processFiles()
      compareDirs(File(BASE_PATH).resolve("baseTest/original"), sourceDir) // No modifications expected
      assertNotSame(it.processed, it.succeeded)
    }
  }

  fun testFormatDryRun_wellFormatted() {
    FileSetFormatValidator(messageOutput!!, true, defaultCodeStyle = codeStyleSettings).use {
      it.addFileMask(Regex(".*\\.java"))
      val sourceDir = createSourceDir("baseTest/expected")
      it.addEntry(sourceDir.canonicalPath)
      it.processFiles()
      compareDirs(File(BASE_PATH).resolve("baseTest/expected"), sourceDir) // No modifications expected
      TestCase.assertEquals(it.processed, it.succeeded)
    }
  }

}
