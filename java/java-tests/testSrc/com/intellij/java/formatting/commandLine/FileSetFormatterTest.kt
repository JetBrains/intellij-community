// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.formatting.commandLine

import com.intellij.formatting.commandLine.FileSetFormatter
import com.intellij.formatting.commandLine.readSettings
import com.intellij.psi.codeStyle.CodeStyleSettings
import junit.framework.ComparisonFailure
import java.io.File
import java.nio.charset.Charset
import kotlin.text.Charsets.UTF_8


class FileSetFormatterTest : FileSetCodeStyleProcessorTestBase() {

  private fun simpleTest(settings: CodeStyleSettings? = null) {
    FileSetFormatter(messageOutput!!, true, primaryCodeStyle = settings, defaultCodeStyle = codeStyleSettings).use {
      it.addFileMask(Regex(".*\\.java"))
      val sourceDir = createSourceDir("${getTestName(true)}/original")
      it.addEntry(sourceDir.canonicalPath)
      it.processFiles()
      compareDirs(File(BASE_PATH).resolve("${getTestName(true)}/expected"), sourceDir)
    }
  }

  fun testBaseTest() = simpleTest()
  fun testProject() = simpleTest()
  fun testTwoProjects() = simpleTest()
  fun testUnwrappedProject() = simpleTest()
  fun testUnwrappedProjectWithInner() = simpleTest()
  fun testMixture() = simpleTest()

  fun testEnforceFour() {
    val settingsFile = File(BASE_PATH).resolve(getTestName(true)).resolve("code-style.xml")
    simpleTest(readSettings(settingsFile))
  }

  private fun testCustomEncoding(charset: Charset) {
    FileSetFormatter(messageOutput!!, true, charset, codeStyleSettings).use {
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
