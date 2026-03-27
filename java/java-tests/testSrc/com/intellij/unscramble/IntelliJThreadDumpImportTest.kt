// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.Path

@TestApplication
@TestDataPath($$"$CONTENT_ROOT/testData/threadDump")
internal class IntelliJThreadDumpImportTest {
  @Test
  fun `parser restores hierarchy and containers`() {
    val dumpText = loadThreadDump("nestedContainers.txt")

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "main@101", 101L, null, false, false)
    assertDumpItem(parsedThreadDump, "worker-1@201", 201L, 300L, false, true)
    assertDumpItem(parsedThreadDump, "Scope A", 300L, 400L, true, false)
    assertDumpItem(parsedThreadDump, "Scope Root", 400L, null, true, false)
  }

  @Test
  fun `parser ignores leading comment lines`() {
    val dumpText = loadThreadDump("leadingComments.txt")

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "main@101", 101L, null, false, false)
    assertDumpItem(parsedThreadDump, "worker-1@201", 201L, 300L, false, true)
    assertDumpItem(parsedThreadDump, "Scope A", 300L, null, true, false)
  }

  @Test
  fun `plain text dump with comments is parsed without hierarchy metadata`() {
    val dumpText = loadThreadDump("plainWithComments.txt")

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "main@101", null, null, false, false)
    assertDumpItem(parsedThreadDump, "worker-1@201", null, null, false, true)
  }

  @Test
  fun `parser keeps inline parent ids even when parent item is missing`() {
    val dumpText = loadThreadDump("unknownParent.txt")

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "worker-1@201", 201L, 999L, false, true)
    assertDumpItem(parsedThreadDump, "Scope A", 300L, null, true, false)
  }

  @Test
  fun `new import keeps thread presentation data`() {
    val dumpText = loadThreadDump("commonIntelliJFormat.txt")

    val importedDumpItems = requireNotNull(parseIntelliJThreadDump(dumpText)).dumpItems()
    val mainThread = findDumpItem(importedDumpItems, "main@101")
    val workerThread1 = findDumpItem(importedDumpItems, "worker-1@201")
    val workerThread2 = findDumpItem(importedDumpItems, "worker-2@202")

    assertThat(mainThread.stackTrace).contains("\"main@101\"")
    assertThat(mainThread.stackTrace).doesNotContain("[\"id\":101]")
    assertThat(mainThread.stackTrace).contains("example.main.run(main.java:1)")

    assertThat(workerThread1.stackTrace).contains("\"worker-1@201\"")
    assertThat(workerThread1.stackTrace).contains("carrierId=0x34")
    assertThat(workerThread1.stackTrace).doesNotContain("[\"id\":201,\"parentId\":300]")
    assertThat(workerThread1.stackTrace).contains("example.worker-1.run(worker-1.java:2)")
    assertEquals(" (runnable)", mainThread.stateDesc)
    assertEquals(" (runnable)", workerThread1.stateDesc)
    assertTrue(workerThread1.canBeHidden)

    assertThat(workerThread2.stackTrace).contains("\"worker-2@202\"")
    assertThat(workerThread2.stackTrace).contains("unmounted")
    assertThat(workerThread2.stackTrace).doesNotContain("[\"id\":202,\"parentId\":300]")
    assertThat(workerThread2.stackTrace).contains("at java.lang.Object.wait()")
    assertEquals(" (waiting)", workerThread2.stateDesc)
    assertTrue(workerThread2.canBeHidden)
  }

  @Test
  fun `round trip from intellij style dump preserves hierarchy and keeps ids in dump item names`() {
    val importedDumpItems = requireNotNull(parseIntelliJThreadDump(loadThreadDump("commonIntelliJFormat.txt"))).dumpItems()

    assertThat(importedDumpItems.filter { !it.isContainer }.map { it.name }).containsExactly("main@101", "worker-1@201", "worker-2@202")

    val serializedDump = serializeIntelliJThreadDump(importedDumpItems, listOf("Full thread dump"))
    assertThat(serializedDump).contains("[\"id\":101]")
    assertThat(serializedDump).contains("[\"id\":201,\"parentId\":300]")
    assertThat(serializedDump).contains("[\"type\":\"container\",\"id\":300]")

    val reparsedDump = requireNotNull(parseIntelliJThreadDump(serializedDump))
    assertDumpItem(reparsedDump, "main@101", 101L, null, false, false)
    assertDumpItem(reparsedDump, "worker-1@201", 201L, 300L, false, true)
    assertDumpItem(reparsedDump, "Scope A", 300L, null, true, false)
  }

  @Test
  fun `import export import does not duplicate serialized thread ids`() {
    val firstImportedDump = requireNotNull(parseIntelliJThreadDump(loadThreadDump("commonIntelliJFormat.txt")))
    val serializedOnce = serializeIntelliJThreadDump(firstImportedDump.dumpItems(), listOf("Full thread dump"))

    val secondImportedDump = requireNotNull(parseIntelliJThreadDump(serializedOnce))
    assertThat(secondImportedDump.dumpItems().filter { !it.isContainer }.map { it.name }).containsExactly("main@101", "worker-1@201", "worker-2@202")

    val serializedTwice = serializeIntelliJThreadDump(secondImportedDump.dumpItems(), listOf("Full thread dump"))
    assertThat(serializedTwice).doesNotContain("@101@101")
    assertThat(serializedTwice).doesNotContain("@201@201")
    assertThat(serializedTwice).isEqualTo(serializedOnce)
  }

  @Test
  fun `plain text dump without comments is parsed without hierarchy metadata`() {
    val plainDumpText = loadThreadDump("plainWithoutFooterMarker.txt")
    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(plainDumpText))

    assertDumpItem(parsedThreadDump, "main@101", null, null, false, false)
  }

  @Test
  fun `parser keeps threads visible with invalid inline metadata`() {
    val dumpText = loadThreadDump("invalidMetadata.txt")

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))
    val dumpItems = parsedThreadDump.dumpItems().filter { !it.isContainer }

    assertThat(dumpItems.map { it.name }).containsExactly("main@101", "worker-1@201")
    assertThat(dumpItems.map { it.treeId }).containsOnlyNulls()
  }

  @Test
  fun `parser extracts unique id from explicitly unnamed thread`() {
    val dumpText = """
      "@555" #1 tid=0x1 nid=0x1 runnable ["id":555]
         java.lang.Thread.State: RUNNABLE
      	at example.main.run(main.java:1)
    """.trimIndent()

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "{unnamed}@555", 555L, null, false, false)
  }

  @Test
  fun `thread may be a parent to a container`() {
    val dumpText = """
      "main@101" #1 prio=5 tid=0x1 nid=0x1 runnable ["id":101]
         java.lang.Thread.State: RUNNABLE
      	at example.main.run(main.java:1)

      "Scope A" tid=0x0 nid=NA container ["type":"container","id":300,"parentId":101]
    """.trimIndent()

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))

    assertDumpItem(parsedThreadDump, "Scope A", 300L, 101L, true, false)
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
    isVirtual: Boolean
  ) {
    assertDumpItem(threadDumpState.dumpItems(), name, treeId, parentTreeId, isContainer, isVirtual)
  }

  private fun assertDumpItem(
    dumpItems: List<DumpItem>,
    name: String,
    treeId: Long?,
    parentTreeId: Long?,
    isContainer: Boolean,
    isVirtual: Boolean
  ) {
    val dumpItem = findDumpItem(dumpItems, name)
    assertEquals(treeId, dumpItem.treeId)
    assertEquals(parentTreeId, dumpItem.parentTreeId)
    assertEquals(isContainer, dumpItem.isContainer)
    assertEquals(isVirtual, dumpItem.stackTrace.contains("virtual"))
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
