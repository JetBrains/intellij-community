// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.formatting.commandLine

import com.intellij.formatting.commandLine.FileSetFormatter
import junit.framework.ComparisonFailure
import java.io.File
import java.nio.charset.Charset
import kotlin.text.Charsets.UTF_8


class FileSetFormatterTest : FileSetCodeStyleProcessorTestBase() {

  override fun setUp() {
    super.setUp()
    processor = FileSetFormatter(codeStyleSettings!!, messageOutput!!, true).also {
      it.addFileMask(Regex(".*\\.java"))
    }
  }

  fun testFormat() {
    processor?.apply {
      val sourceDir = createSourceDir("baseTest/original")
      addEntry(sourceDir.canonicalPath)
      processFiles()
      compareDirs(File(BASE_PATH).resolve("baseTest/expected"), sourceDir)
    }
  }


  private fun testCustomEncoding(charset: Charset) {
    FileSetFormatter(codeStyleSettings!!, messageOutput!!, true, charset).use {
      val sourceDir = createSourceDir("encoding/original")
      it.addEntry(sourceDir.canonicalPath)
      it.processFiles()
      val expectedBytes = File(BASE_PATH).resolve("encoding/expected").resolve("Test_ISO_8859_15.java").readBytes()
      val processedBytes = sourceDir.resolve("Test_ISO_8859_15.java").readBytes()

      assertEquals(expectedBytes.contentToString(), processedBytes.contentToString())
    }
  }

  fun testCustomEncodingFail() {
    try {
      testCustomEncoding(UTF_8)
      fail("Missing expected exception")
    }
    catch (e: ComparisonFailure) {
      // OK, expected exception
    }
  }

  fun testCustomEncodingOk() {
    testCustomEncoding(Charset.forName("ISO-8859-15"))
  }

}
