// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.formatting.commandLine

import com.intellij.formatting.commandLine.CodeStyleProcessorBuildException.ArgumentsException
import com.intellij.formatting.commandLine.CodeStyleProcessorBuildException.ShowUsageException
import com.intellij.formatting.commandLine.FileSetFormatValidator
import com.intellij.formatting.commandLine.FileSetFormatter
import com.intellij.formatting.commandLine.createBuilder
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.File

@TestApplication
class FileSetFormatterStarterTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }

  private val project get() = projectFixture.get().project

  private inline fun <reified T : Exception> expectExceptionsOnArgs(vararg args: String) {
    try {
      createBuilder(args.toList()).build(project).let {
        fail("Missing expected exception ${T::class}")
      }
    }
    catch (e: Exception) {
      assertInstanceOf(T::class.java, e)
    }
  }

  @Test
  fun testHelp_noArgs0(): Unit = expectExceptionsOnArgs<ShowUsageException>()
  @Test
  fun testHelp_noArgs1(): Unit = expectExceptionsOnArgs<ShowUsageException>("format")
  @Test
  fun testHelp_explicit_only(): Unit = expectExceptionsOnArgs<ShowUsageException>("format", "-help")
  @Test
  fun testHelp_explicit_withOthers(): Unit = expectExceptionsOnArgs<ShowUsageException>("format", "-r", "src", "-h")

  @Test
  fun testUnknownArgumentFails(): Unit = expectExceptionsOnArgs<ArgumentsException>("format", "-r", "src", "-unknown_arg")
  @Test
  fun testMissingParamForMasks(): Unit = expectExceptionsOnArgs<ArgumentsException>("format", "-r", "src", "-m")
  @Test
  fun testMissingParamForSettings(): Unit = expectExceptionsOnArgs<ArgumentsException>("format", "-r", "src", "-s")
  @Test
  fun testMissingSettingsFile(): Unit = expectExceptionsOnArgs<ArgumentsException>("format", "-s",
                                                                             "really_hope_noone_adds_file_with_this_name_in_future", "src")

  @Test
  fun testDefaultArgs() {
    createBuilder(listOf("format", ".")).build(project).let { processor ->
      assertInstanceOf(FileSetFormatter::class.java, processor)
      assertFalse(processor.isRecursive)
      assertThat(processor.getFileMasks()).isEmpty()
      assertNull(processor.defaultCodeStyle)
      assertEquals(1, processor.getEntries().size)
      assertEquals(File(".").absolutePath, processor.getEntries()[0].absolutePath)
    }
  }

  @Test
  fun testNonDefaultArgs() {
    createBuilder(listOf("format", "-r", "-d", "-m", "*.java, ,*.kt,", ".", "..")).build(project).let { processor ->
      assertInstanceOf(FileSetFormatValidator::class.java, processor)
      assertTrue(processor.isRecursive)

      assertEquals(2, processor.getFileMasks().size)
      assertEquals(".*\\.java", processor.getFileMasks()[0].pattern)
      assertEquals(".*\\.kt", processor.getFileMasks()[1].pattern)

      assertEquals(2, processor.getEntries().size)
      assertEquals(File(".").absolutePath, processor.getEntries()[0].absolutePath)
      assertEquals(File("..").absolutePath, processor.getEntries()[1].absolutePath)
    }
  }

  @Test
  fun testNonDefaultArgs2() {
    createBuilder(listOf("format", "-d", "-allowDefaults", ".")).build(project).let { processor ->
      assertInstanceOf(FileSetFormatValidator::class.java, processor)
      assertEquals(CodeStyleSettingsManager.getInstance().createSettings(), processor.defaultCodeStyle)
    }
  }

}
