// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.testFramework.PlatformTestUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class KotlincOutputParserTest {

  @Test
  fun `test kotlin error message without file location`() {
    val event = parseSingleMessageEvent(
      """
          e: some.BuildException: error text
            at some.App.method(App.kt:5)
            at some.Util.method(Util.kt:10)
          
      """.trimIndent()
    )
    assertMessageEvent(event, MessageEvent.Kind.ERROR, "some.BuildException: error text", """
      some.BuildException: error text
        at some.App.method(App.kt:5)
        at some.Util.method(Util.kt:10)

    """.trimIndent())
  }

  @Test
  fun `test kotlin error message with uri file location`() {
    val event = parseSingleMessageEvent(
      """
          e: file://C:/A.kt: (7, 5): Unresolved reference: bbb
                      
      """.trimIndent()
    )
    assertMessageEvent(
      event,
      MessageEvent.Kind.ERROR,
      "file://C:/A.kt: (7, 5): Unresolved reference: bbb",
      "file://C:/A.kt: (7, 5): Unresolved reference: bbb"
    )
  }

  @Test
  fun `test kotlin error message with file location`() {
    val event = parseSingleMessageEvent(
      """
          e: C:\A.kt: (7, 5): Unresolved reference: bbb
            
      """.trimIndent()
    )
    assertMessageEvent(
      event,
      MessageEvent.Kind.ERROR,
      "C:\\A.kt: (7, 5): Unresolved reference: bbb",
      "C:\\A.kt: (7, 5): Unresolved reference: bbb"
    )
  }

  @Test
  fun `test kotlin warning message after successful build`() {
    val buildGradleKtsWithWarning = File(
      PlatformTestUtil.getCommunityPath(),
      "platform/lang-impl/testData/build/output/warning/build.gradle.kts"
    )
    val pathToBuildGradleKtsWithWarning = buildGradleKtsWithWarning.path.replace(File.separatorChar, '/')

    val event = parseSingleMessageEvent(
      """    
      > Configure project :
      w: file://$pathToBuildGradleKtsWithWarning:23:1: The expression is unused
      
      BUILD SUCCESSFUL in 35s

      """.trimIndent()
    )
    assertMessageEvent(
      event,
      MessageEvent.Kind.WARNING,
      "The expression is unused",
      "w: file://$pathToBuildGradleKtsWithWarning:23:1: The expression is unused"
    )

    val fileMessageEvent = event.asFileMessageEvent()
    assertEquals(buildGradleKtsWithWarning.toPath(), fileMessageEvent.filePosition.path)
    assertEquals(22, fileMessageEvent.filePosition.startLine)
    assertEquals(0, fileMessageEvent.filePosition.startColumn)
  }

  @Test
  fun `test different file paths are parsed`() {
    val paths = listOf("e: file:///C:/JB/tasks/KTIJ-22428/untitled/src/main/kotlin/A.kt:7:5 Unresolved reference: bbb\n",
                       "e: C:\\A.kt: (7, 5): Unresolved reference: bbb\n",
                       "e: file:////wsl$/Ubuntu/home/A.kt:7:5 Unresolved reference: bbb\n",
                       "e: \\\\wsl" + "$" + "\\Ubuntu\\home\\A.kt: (7, 5): Unresolved reference: bbb\n"
    )
    for (line in paths) {
      assertTrue(line.contains(KotlincOutputParser.extractPath(line) ?: "path not found"), "Failed to find path in [$line]")
    }
  }

  @Test
  fun `test KSP AutoMigration error is parsed as expected`() {
    val errorMessage = """
    e: [ksp] project-main/app/src/main/java/com/google/apps/AutoMigrationRoom/MainActivity.kt:39:
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
    val event = parseSingleMessageEvent(errorMessage)
    assertMessageEvent(
      event,
      MessageEvent.Kind.ERROR,
      "[ksp] project-main/app/src/main/java/com/google/apps/AutoMigrationRoom/MainActivity.kt:39:",
      errorMessage.substring(3)
    )
  }

  private fun parseSingleMessageEvent(output: String): MessageEvent {
    val events = parseEvents(output)
    assertEquals(1, events.size)
    return events.single().asMessageEvent()
  }

  private fun parseEvents(output: String): List<BuildEvent> {
    val events = mutableListOf<BuildEvent>()
    val buildId = Any()
    BuildOutputInstantReaderImpl(
      buildId,
      buildId,
      BuildProgressListener { _, event -> events += event },
      listOf(KotlincOutputParser())
    ).append(output).closeAndGetFuture().get()
    return events
  }

  private fun BuildEvent.asMessageEvent(): MessageEvent {
    assertTrue(this is MessageEvent, "Expected MessageEvent, got ${javaClass.name}")
    return this as MessageEvent
  }

  private fun MessageEvent.asFileMessageEvent(): FileMessageEvent {
    assertTrue(this is FileMessageEvent, "Expected FileMessageEvent, got ${javaClass.name}")
    return this as FileMessageEvent
  }

  private fun assertMessageEvent(event: MessageEvent, kind: MessageEvent.Kind, message: String, description: String) {
    assertEquals(kind, event.kind)
    assertEquals(message, event.message)
    assertEquals(description.trimEnd(), event.description?.trimEnd())
    assertEquals(description.trimEnd(), event.result.details?.trimEnd())
  }
}
