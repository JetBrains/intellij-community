// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.formatting.commandLine

import com.intellij.formatting.commandLine.FileSetCodeStyleProcessor
import com.intellij.formatting.commandLine.FileSetFormatValidator
import com.intellij.formatting.commandLine.FileSetFormatter
import com.intellij.formatting.commandLine.FormatterStarter
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.LightPlatformTestCase
import java.io.File
import java.security.Permission


private class ExitTrappedException(val status: Int) : SecurityException("System.exit has been fired with status $status")

private object SystemExitTrapper : SecurityManager() {
  override fun checkPermission(permission: Permission?) {
    permission
      ?.name
      ?.takeIf { it.startsWith("exitVM.") }
      ?.removePrefix("exitVM.")
      ?.toIntOrNull()
      ?.let {
        throw ExitTrappedException(it)
      }
  }
}


class FileSetFormatterStarterTest : LightPlatformTestCase() {

  private fun expectSystemExit(expectedStatus: Int, body: () -> Unit) {
    val oldSecurityManager = System.getSecurityManager()
    System.setSecurityManager(SystemExitTrapper)
    try {
      body()
      fail("Missing expected System.exit($expectedStatus)")
    }
    catch (e: ExitTrappedException) {
      assertEquals("System.exit() has been called with an unexpected status", expectedStatus, e.status)
    }
    finally {
      System.setSecurityManager(oldSecurityManager)
    }
  }

  private fun expectSystemExitOnArgs(expectedStatus: Int, vararg args: String) = expectSystemExit(expectedStatus) {
    FormatterStarter().createFormatter(args.toList().toTypedArray())
  }

  fun testHelp_noArgs0() = expectSystemExitOnArgs(0)
  fun testHelp_noArgs1() = expectSystemExitOnArgs(0, "format")
  fun testHelp_explicit_only() = expectSystemExitOnArgs(0, "format", "-help")
  fun testHelp_explicit_withOthers() = expectSystemExitOnArgs(0, "format", "-r", "src", "-h")

  fun testUnknownArgumentFails() = expectSystemExitOnArgs(1, "format", "-r", "src", "-unknown_arg")

  fun testMissingParamForMasks() = expectSystemExitOnArgs(1, "format", "-r", "src", "-m")
  fun testMissingParamForSettings() = expectSystemExitOnArgs(1, "format", "-r", "src", "-s")
  fun testMissingSettingsFile() = expectSystemExitOnArgs(1, "format", "-s", "really_hope_noone_adds_file_with_this_name_in_future", "src")

  fun testDefaultArgs() {
    val processor: FileSetCodeStyleProcessor = FormatterStarter().createFormatter(arrayOf("format", "."))
    assertInstanceOf(processor, FileSetFormatter::class.java)
    assertFalse(processor.isRecursive)
    assertEmpty(processor.getFileMasks())
    assertEquals(CodeStyleSettingsManager.getInstance().createSettings(), processor.codeStyleSettings)
    assertEquals(1, processor.getEntries().size)
    assertEquals(File(".").absolutePath, processor.getEntries()[0].absolutePath)
  }

  fun testNonDefaultArgs() {
    val processor: FileSetCodeStyleProcessor = FormatterStarter().createFormatter(arrayOf("format", "-r", "-d", "-m", "*.java, ,*.kt,", ".", ".."))
    assertInstanceOf(processor, FileSetFormatValidator::class.java)
    assertTrue(processor.isRecursive)

    assertEquals(2, processor.getFileMasks().size)
    assertEquals(".*\\.java", processor.getFileMasks()[0].pattern)
    assertEquals(".*\\.kt", processor.getFileMasks()[1].pattern)

    assertEquals(2, processor.getEntries().size)
    assertEquals(File(".").absolutePath, processor.getEntries()[0].absolutePath)
    assertEquals(File("..").absolutePath, processor.getEntries()[1].absolutePath)
  }

}
