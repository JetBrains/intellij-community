// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.formatting.commandLine

import com.intellij.JavaTestUtil
import com.intellij.application.options.CodeStyle
import com.intellij.formatting.commandLine.FileSetCodeStyleProcessor
import com.intellij.formatting.commandLine.MessageOutput
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore.loadText
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase.*
import java.io.File
import java.io.PrintWriter


val BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/formatter/commandLine"

abstract class FileSetCodeStyleProcessorTestBase : LightPlatformTestCase() {

  lateinit var codeStyleSettings: CodeStyleSettings

  var messageOutput: MessageOutput? = null

  override fun setUp() {
    super.setUp()
    codeStyleSettings = CodeStyle.createTestSettings().apply {
      getCommonSettings(JavaLanguage.INSTANCE).apply {
        indentOptions!!.INDENT_SIZE = 2
        CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
        IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
      }
    }
    messageOutput = MessageOutput(PrintWriter(System.out), PrintWriter(System.err))
  }

}

fun compareDirs(expectedDir: File, resultDir: File) {
  assertTrue(expectedDir.isDirectory && resultDir.isDirectory)

  expectedDir.listFiles()?.forEach { expected ->
    val actual = File(resultDir.canonicalPath + File.separator + expected.name)
    assertTrue("Cannot find expected " + actual.path, actual.exists())
    if (expected.isDirectory) {
      compareDirs(expected, actual)
    }
    else {
      assertContentEquals(expected, actual)
    }
  }
}

fun assertContentEquals(expectedFile: File, actualFile: File) {
  val expectedVFile = VfsUtil.findFileByIoFile(expectedFile, true)
  assertNotNull(expectedVFile)

  val actualVFile = VfsUtil.findFileByIoFile(actualFile, true)
  assertNotNull(actualVFile)

  assertEquals(loadText(expectedVFile!!), loadText(actualVFile!!))
}

fun createSourceDir(subDir: String) =
  FileUtil
    .createTempDirectory("unitTest", "_format")
    .also { sourceDir ->
      FileUtil.copyDir(File(BASE_PATH).resolve(subDir), sourceDir)
    }
