// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.MessageEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.io.createFile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class KotlincOutputParserTest : CompilerOutputParserTestCase() {

  override fun createParser() = KotlincOutputParser()

  @Test
  fun `test kotlin error message without file location`() {
    val message = "some.BuildException: error text"
    val description = """
      $message
        at some.App.method(App.kt:5)
        at some.Util.method(Util.kt:10)

    """.trimIndent()

    val event = parseSingleMessageEvent("e: $description")

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, description)
  }

  @Test
  fun `test kotlin error message with uri file location`() {
    val message = "file://C:/A.kt: (7, 5): Unresolved reference: bbb"

    val event = parseSingleMessageEvent("e: $message\n")

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, message)
  }

  @Test
  fun `test kotlin error message with file location`() {
    val message = "C:\\A.kt: (7, 5): Unresolved reference: bbb"

    val event = parseSingleMessageEvent("e: $message\n")

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, message)
  }

  @Test
  fun `test kotlin warning message after successful build`() {
    val path = testRoot.createFile("build.gradle.kts")
    val message = "The expression is unused"
    val description = "w: file://$path:23:1: $message"
    val output = """
      > Configure project :
      $description

      BUILD SUCCESSFUL in 35s

    """.trimIndent()

    val event = parseSingleFileMessageEvent(output)

    assertMessageEvent(event, MessageEvent.Kind.WARNING, message, description)
    assertFilePosition(event, path, 22, 0)
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "e: file:///C:/JB/tasks/KTIJ-22428/untitled/src/main/kotlin/A.kt:7:5 Unresolved reference: bbb\n",
    "e: C:\\A.kt: (7, 5): Unresolved reference: bbb\n",
    "e: file:////wsl$/Ubuntu/home/A.kt:7:5 Unresolved reference: bbb\n",
    "e: \\\\wsl$\\Ubuntu\\home\\A.kt: (7, 5): Unresolved reference: bbb\n",
  ])
  fun `test different file paths are parsed`(line: String) {
    val path = KotlincOutputParser.extractPath(line)
    assertNotNull(path) {
      "Failed to extract a path from [$line]"
    }
    assertTrue(path in line) {
      "Extracted path [$path] isn't a part of [$line]"
    }
  }

  @Test
  fun `test KSP AutoMigration error is parsed as expected`() {
    val message = "[ksp] project-main/app/src/main/java/com/google/apps/AutoMigrationRoom/MainActivity.kt:39:"
    val description = """
      $message
                  AutoMigration Failure: Please declare an interface extending 'AutoMigrationSpec',
                  and annotate with the @RenameColumn or @RemoveColumn annotation to specify the
                  change to be performed:
                  1) RENAME:
                      @RenameColumn(
                              tableName = "users",
                              fromColumnName = "eyeColor",
                              toColumnName = <NEW_COLUMN_NAME>
                      )
                  2) DELETE:
                      @DeleteColumn=(
                              tableName = "users",
                              columnName = "eyeColor"
                              )

    """.trimIndent()

    val event = parseSingleMessageEvent("e: $description")

    assertMessageEvent(event, MessageEvent.Kind.ERROR, message, description)
  }
}
