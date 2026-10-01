// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.MessageEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.io.createFile
import org.junit.jupiter.api.Test

@TestApplication
class GroovycOutputParserTest : CompilerOutputParserTestCase() {

  override fun createParser() = GroovycOutputParser()

  @Test
  fun `test groovyc error navigates to line and column`() {
    val path = testRoot.createFile("JlinkTask.groovy")
    val message = "unable to resolve class Provider <? extends java.lang.Iterable <String>>"
    val description = """
      $path: 96: $message
       @ line 96, column 28.
    """.trimIndent()
    val output = """
      > Task :compileGroovy FAILED
      startup failed:
      $path: 96: $message
       @ line 96, column 28.

    """.trimIndent()

    val event = parseSingleFileMessageEvent(output)

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, description)
    assertFilePosition(event, path, 95, 27)
  }

  @Test
  fun `test groovyc error with location on the same line`() {
    val path = testRoot.createFile("Script.groovy")
    val message = "unexpected token: static"
    val description = "$path: 7: $message @ line 7, column 1."
    val output = """
      > Task :compileGroovy FAILED
      startup failed:
      $description

    """.trimIndent()

    val event = parseSingleFileMessageEvent(output)

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, description)
    assertFilePosition(event, path, 6, 0)
  }
}
