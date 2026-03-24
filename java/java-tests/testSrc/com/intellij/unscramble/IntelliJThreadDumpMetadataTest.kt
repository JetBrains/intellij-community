// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class IntelliJThreadDumpMetadataTest {
  @Test
  fun `serializer includes structural and additional metadata`() {
    val serialized = serializeThreadDumpItem(
      itemHeader = "\"scope:1@300\" virtual tid=0x0 nid=NA suspended",
      stackTraceBody = "",
      id = 300L,
      parentId = 400L,
      type = "coroutine",
      additionalMetadata = mutableMapOf(
        "dispatcher" to "Dispatchers.Default",
      ),
    )

    assertThat(serialized).startsWith("\"scope:1@300\" virtual tid=0x0 nid=NA suspended [")
    val parsedThread = requireNotNull(parseIntelliJThreadDump(serialized)).threadStates.single()
    assertThat(parsedThread.type).isEqualTo("coroutine")
    assertThat(parsedThread.uniqueId).isEqualTo(300L)
    assertThat(parsedThread.threadContainerUniqueId).isEqualTo(400L)
    assertThat(parsedThread.metadata).containsEntry("dispatcher", "Dispatchers.Default")
  }

  @Test
  fun `serializer escapes commas quotes and backslashes in metadata values`() {
    val serialized = serializeThreadDumpItem(
      itemHeader = "\"worker@101\" tid=0x1 nid=NA runnable",
      stackTraceBody = "",
      id = 101L,
      parentId = null,
      additionalMetadata = mutableMapOf(
        "note" to "say \"hi\", then use \\",
      ),
    )

    assertThat(serialized).startsWith("\"worker@101\" tid=0x1 nid=NA runnable [")
    val parsedThread = requireNotNull(parseIntelliJThreadDump(serialized)).threadStates.single()
    assertThat(parsedThread.uniqueId).isEqualTo(101L)
    assertThat(parsedThread.metadata).containsEntry("note", "say \"hi\", then use \\")
  }

  @Test
  fun `parser handles thread names with commas before inline metadata`() {
    val parsedDump = requireNotNull(
      parseIntelliJThreadDump(
        """
        "worker,1@101" #1 tid=0x1 nid=NA runnable ["id":101]
           java.lang.Thread.State: RUNNABLE
         	at example.Worker.run(Worker.java:1)
        """.trimIndent()
      )
    )

    val dumpItem = parsedDump.dumpItems().single { !it.isContainer }
    assertThat(dumpItem.name).isEqualTo("worker,1@101")
    assertThat(dumpItem.treeId).isEqualTo(101L)
    assertThat(dumpItem.stackTrace.lineSequence().first()).doesNotContain("[\"id\":101]")
  }
}
