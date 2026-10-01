// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.MessageEvent
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.testFramework.utils.io.createFile
import org.junit.jupiter.api.Test

class JavacOutputParserTest : CompilerOutputParserTestCase() {

  override fun createParser() = JavacOutputParser()

  @Test
  fun `test javac error navigates to line and column`() {
    val path = testRoot.createFile("Main.java")
    val message = "incompatible types: String cannot be converted to int"
    val description = """
      $path:4: error: $message
          int number = "text";
                       ^

    """.trimIndent()

    val event = parseSingleFileMessageEvent(description)

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, description)
    assertFilePosition(event, path, 3, 17)
  }

  @Test
  fun `test javac warning navigates to line and column`() {
    val path = testRoot.createFile("Main.java")
    val message = "[deprecation] foo() in Util has been deprecated"
    val description = """
      $path:7: warning: $message
          Util.foo();
              ^

    """.trimIndent()

    val event = parseSingleFileMessageEvent(description)

    assertMessageEvent(event, MessageEvent.Kind.WARNING, message, description)
    assertFilePosition(event, path, 6, 8)
  }

  @Test
  fun `test javac error is amended with the symbol details`() {
    val path = testRoot.createFile("Main.java")
    val message = "cannot find symbol class Strin"
    val description = """
      $path:5: error: cannot find symbol
          Strin s = null;
          ^
        symbol:   class Strin
        location: class Main

    """.trimIndent()

    val event = parseSingleFileMessageEvent(description)

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, description)
    assertFilePosition(event, path, 4, 4)
  }

  @Test
  fun `test javac note is amended with the following notes`() {
    val path = testRoot.createFile("Main.java")
    val message = "uses unchecked or unsafe operations."
    val output = """
      Note: $path $message
      Note: Recompile with -Xlint:unchecked for details.

    """.trimIndent()
    val description = """
      $path:
      $message
      Recompile with -Xlint:unchecked for details.
    """.trimIndent()

    val event = parseSingleFileMessageEvent(output)

    assertMessageEvent(event, MessageEvent.Kind.INFO, message, description)
    assertFilePosition(event, path, 0, 0)
  }

  @Test
  fun `test javac error without file location`() {
    val message = "warnings found and -Werror specified"
    val description = "error: $message"

    val event = parseSingleMessageEvent("$description\n")

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, description)
  }

  @Test
  fun `test javac warning without file location`() {
    val message = "[options] bootstrap class path not set in conjunction with -source 8"
    val description = "warning: $message"

    val event = parseSingleMessageEvent("$description\n")

    assertMessageEvent(event, MessageEvent.Kind.WARNING, message, description)
  }

  @Test
  fun `test javac failure is reported as an error`() {
    val message = "javac: invalid flag: --unknown"

    val event = parseSingleMessageEvent("$message\n")

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, message)
  }

  @Test
  fun `test exception cause is reported as an error`() {
    val message = "java.lang.NullPointerException"
    val description = "Caused by: $message"

    val event = parseSingleMessageEvent("$description\n")

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, description)
  }

  @Test
  fun `test out of memory is reported as an error`() {
    val message = "Out of memory."
    val description = "Exception in thread \"main\" java.lang.OutOfMemoryError"

    val event = parseSingleMessageEvent("$description\n")

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, description)
  }

  @Test
  fun `test error in a file with an unexpected extension is ignored`() {
    val path = testRoot.createFile("Main.kt")
    val output = """
      $path:4: error: incompatible types: String cannot be converted to int
          int number = "text";
                       ^

    """.trimIndent()

    val events = parseEvents(output)

    assertEmpty(events)
  }
}
