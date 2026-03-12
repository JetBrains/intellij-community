// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.TestDataPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.Path

@TestDataPath($$"$CONTENT_ROOT/testData/threadDump")
internal class IntelliJThreadDumpImportTest {
  @Test
  fun `parser restores hierarchy and containers`() {
    val dumpText = loadThreadDump("nestedContainers.txt")

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "main@101", 101L, null, false)
    assertDumpItem(parsedThreadDump, "worker-1@201", 201L, 300L, false)
    assertDumpItem(parsedThreadDump, "Scope A", 300L, 400L, true)
    assertDumpItem(parsedThreadDump, "Scope Root", 400L, null, true)
  }

  @Test
  fun `parser ignores leading comment lines`() {
    val dumpText = loadThreadDump("leadingComments.txt")

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "main@101", 101L, null, false)
    assertDumpItem(parsedThreadDump, "worker-1@201", 201L, 300L, false)
    assertDumpItem(parsedThreadDump, "Scope A", 300L, null, true)
  }

  @Test
  fun `plain text dump falls back to legacy parser when metadata footer is missing`() {
    val dumpText = loadThreadDump("plainWithComments.txt")

    assertNull(parseIntelliJThreadDump(dumpText))
  }

  @Test
  fun `parser drops unknown parent links`() {
    val dumpText = loadThreadDump("unknownParent.txt")

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "worker-1@201", 201L, null, false)
    assertDumpItem(parsedThreadDump, "Scope A", 300L, null, true)
  }

  @Test
  fun `new import keeps thread presentation data`() {
    val dumpText = loadThreadDump("commonIntelliJFormat.txt")

    val importedDumpItems = requireNotNull(parseIntelliJThreadDump(dumpText)).dumpItems()
    val mainThread = findDumpItem(importedDumpItems, "main@101")
    val workerThread = findDumpItem(importedDumpItems, "worker-1@201")

    assertThat(mainThread.stackTrace).contains("\"main@101\"")
    assertThat(mainThread.stackTrace).contains("example.main.run(main.java:1)")
    assertThat(workerThread.stackTrace).contains("\"worker-1@201\"")
    assertThat(workerThread.stackTrace).contains("example.worker-1.run(worker-1.java:2)")
    assertEquals(" (runnable)", mainThread.stateDesc)
    assertEquals(" (virtual runnable)", workerThread.stateDesc)
    assertTrue(workerThread.canBeHidden)
  }

  @Test
  fun `round trip from intellij style dump preserves hierarchy and keeps ids in dump item names`() {
    val importedDumpItems = requireNotNull(parseIntelliJThreadDump(loadThreadDump("commonIntelliJFormat.txt"))).dumpItems()

    assertThat(importedDumpItems.filter { !it.isContainer }.map { it.name }).containsExactly("main@101", "worker-1@201")

    val serializedDump = serializeIntelliJThreadDump(importedDumpItems, listOf("Full thread dump"))
    assertThat(serializedDump).contains("\"main@101\"")
    assertThat(serializedDump).contains("\"worker-1@201\"")

    val reparsedDump = requireNotNull(parseIntelliJThreadDump(serializedDump))
    assertDumpItem(reparsedDump, "main@101", 101L, null, false)
    assertDumpItem(reparsedDump, "worker-1@201", 201L, 300L, false)
    assertDumpItem(reparsedDump, "Scope A", 300L, null, true)
  }

  @Test
  fun `import export import does not duplicate serialized thread ids`() {
    val firstImportedDump = requireNotNull(parseIntelliJThreadDump(loadThreadDump("commonIntelliJFormat.txt")))
    val serializedOnce = serializeIntelliJThreadDump(firstImportedDump.dumpItems(), listOf("Full thread dump"))

    val secondImportedDump = requireNotNull(parseIntelliJThreadDump(serializedOnce))
    assertThat(secondImportedDump.dumpItems().filter { !it.isContainer }.map { it.name }).containsExactly("main@101", "worker-1@201")

    val serializedTwice = serializeIntelliJThreadDump(secondImportedDump.dumpItems(), listOf("Full thread dump"))
    assertThat(serializedTwice).doesNotContain("@101@101")
    assertThat(serializedTwice).doesNotContain("@201@201")
    assertThat(serializedTwice).isEqualTo(serializedOnce)
  }

  @Test
  fun `parser returns null without footer marker`() {
    val plainDumpText = loadThreadDump("plainWithoutFooterMarker.txt")
    val dumpText = loadThreadDump("commonIntelliJFormat.txt")

    assertNull(parseIntelliJThreadDump(plainDumpText))
    assertNotNull(parseIntelliJThreadDump(dumpText))
  }

  @Test
  fun `parser returns null when footer marker is not on a separate line`() {
    val dumpText = """
      "main@101" #1 prio=5 tid=0x1 nid=0x1 runnable
         java.lang.Thread.State: RUNNABLE
      	at example.main.run(main.java:1)${IntelliJThreadDumpMetadata.META_DATA_MARKER}
      {
          "version": 1
      }
    """.trimIndent()

    assertNull(parseIntelliJThreadDump(dumpText))
  }

  @Test
  fun `parser keeps threads visible with invalid metadata`() {
    val dumpText = loadThreadDump("invalidMetadata.txt")

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertThat(parsedThreadDump.dumpItems().filter { !it.isContainer }.map { it.name }).containsExactly("main@101", "worker-1@201")
    assertTrue(parsedThreadDump.dumpItems().none { it.isContainer })
  }

  @Test
  fun `parser extracts unique id from explicitly unnamed thread`() {
    val dumpText = """
      "{unnamed}@555" #1 tid=0x1 nid=0x1 runnable
         java.lang.Thread.State: RUNNABLE
      	at example.main.run(main.java:1)

      ${IntelliJThreadDumpMetadata.META_DATA_MARKER}
      {
          "version": 1
      }
    """.trimIndent()

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "{unnamed}@555", 555L, null, false)
  }

  @Test
  fun `parser leaves zero id threads detached from containers`() {
    val dumpText = """
      "main" #1 prio=5 tid=0x1 nid=0x1 runnable
         java.lang.Thread.State: RUNNABLE
      	at example.main.run(main.java:1)

      ${IntelliJThreadDumpMetadata.META_DATA_MARKER}
      {
          "version": 1,
          "tree_links": [
              {
                  "tree_id": 101,
                  "parent_tree_id": 300
              }
          ],
          "containers": [
              {
                  "name": "Scope A",
                  "tree_id": 300
              }
          ]
      }
    """.trimIndent()

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "main", 0L, null, false)
    assertDumpItem(parsedThreadDump, "Scope A", 300L, null, true)
  }

  @Test
  fun `thread may be a parent to a container`() {
    val dumpText = """
      "main@101" #1 prio=5 tid=0x1 nid=0x1 runnable
         java.lang.Thread.State: RUNNABLE
      	at example.main.run(main.java:1)

      ${IntelliJThreadDumpMetadata.META_DATA_MARKER}
      {
          "version": 1,
          "tree_links": [
              {
                  "tree_id": 300,
                  "parent_tree_id": 101
              }
          ],
          "containers": [
              {
                  "name": "Scope A",
                  "tree_id": 300
              }
          ]
      }
    """.trimIndent()

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "Scope A", 300L, 101L, true)
  }

  private fun loadThreadDump(path: String): String {
    return Files.readString(Path(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/threadDump").resolve(path)).trim()
  }

  private fun assertDumpItem(
    threadDumpState: ThreadDumpState,
    name: String,
    treeId: Long?,
    parentTreeId: Long?,
    isContainer: Boolean,
  ) {
    assertDumpItem(threadDumpState.dumpItems(), name, treeId, parentTreeId, isContainer)
  }

  private fun assertDumpItem(
    dumpItems: List<DumpItem>,
    name: String,
    treeId: Long?,
    parentTreeId: Long?,
    isContainer: Boolean,
  ) {
    val dumpItem = findDumpItem(dumpItems, name)
    assertEquals(treeId, dumpItem.treeId)
    assertEquals(parentTreeId, dumpItem.parentTreeId)
    assertEquals(isContainer, dumpItem.isContainer)
  }

  private fun findDumpItem(dumpItems: List<DumpItem>, name: String): DumpItem {
    for (dumpItem in dumpItems) {
      if (name == dumpItem.name) {
        return dumpItem
      }
    }
    error("Dump item not found: $name")
  }
}
