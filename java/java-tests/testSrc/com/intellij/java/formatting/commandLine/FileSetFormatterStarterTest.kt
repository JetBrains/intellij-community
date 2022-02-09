// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.formatting.commandLine

import com.intellij.formatting.commandLine.CodeStyleProcessorBuildException.ArgumentsException
import com.intellij.formatting.commandLine.CodeStyleProcessorBuildException.ShowUsageException
import com.intellij.formatting.commandLine.FileSetFormatValidator
import com.intellij.formatting.commandLine.FileSetFormatter
import com.intellij.formatting.commandLine.createFormatter
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.LightPlatformTestCase
import java.io.File


class FileSetFormatterStarterTest : LightPlatformTestCase() {

  private inline fun <reified T : Exception> expectExceptionsOnArgs(vararg args: String) {
    try {
      createFormatter(args.toList().toTypedArray()).use {
        fail("Missing expected exception ${T::class}")
      }
    }
    catch (e: Exception) {
      assertInstanceOf(e, T::class.java)
    }
  }

  fun testHelp_noArgs0() = expectExceptionsOnArgs<ShowUsageException>()
  fun testHelp_noArgs1() = expectExceptionsOnArgs<ShowUsageException>("format")
  fun testHelp_explicit_only() = expectExceptionsOnArgs<ShowUsageException>("format", "-help")
  fun testHelp_explicit_withOthers() = expectExceptionsOnArgs<ShowUsageException>("format", "-r", "src", "-h")

  fun testUnknownArgumentFails() = expectExceptionsOnArgs<ArgumentsException>("format", "-r", "src", "-unknown_arg")
  fun testMissingParamForMasks() = expectExceptionsOnArgs<ArgumentsException>("format", "-r", "src", "-m")
  fun testMissingParamForSettings() = expectExceptionsOnArgs<ArgumentsException>("format", "-r", "src", "-s")
  fun testMissingSettingsFile() = expectExceptionsOnArgs<ArgumentsException>("format", "-s",
                                                                             "really_hope_noone_adds_file_with_this_name_in_future", "src")

  fun testDefaultArgs() {
    createFormatter(arrayOf("format", ".")).use { processor ->
      assertInstanceOf(processor, FileSetFormatter::class.java)
      assertFalse(processor.isRecursive)
      assertEmpty(processor.getFileMasks())
      assertNull(processor.defaultCodeStyle)
      assertEquals(1, processor.getEntries().size)
      assertEquals(File(".").absolutePath, processor.getEntries()[0].absolutePath)
    }
  }

  fun testNonDefaultArgs() {
    createFormatter(arrayOf("format", "-r", "-d", "-m", "*.java, ,*.kt,", ".", "..")).use { processor ->
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

  fun testNonDefaultArgs2() {
    createFormatter(arrayOf("format", "-d", "-allowDefaults", ".")).use { processor ->
      assertInstanceOf(processor, FileSetFormatValidator::class.java)
      assertEquals(CodeStyleSettingsManager.getInstance().createSettings(), processor.defaultCodeStyle)
    }
  }

}
