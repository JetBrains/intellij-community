// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.SimpleTextAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import javax.swing.Icon
import kotlin.io.path.Path

@TestApplication
@TestDataPath($$"$CONTENT_ROOT/testData/threadDump")
internal class IntelliJThreadDumpExportTest {
  @Test
  fun `serializer writes documented format and includes info items`() {
    val expectedDumpText = loadGoldenThreadDump("serializedFull")

    val actualDumpText = serializeIntelliJThreadDump(createDumpItemsForExport(), listOf("Full thread dump"))

    assertThat(actualDumpText).isEqualTo(expectedDumpText)
  }

  @Test
  fun `serializer marks truncated thread dump in second comment line`() {
    val expectedDumpText = loadGoldenThreadDump("serializedTruncated")

    val actualDumpText = serializeIntelliJThreadDump(createDumpItemsForExport(), listOf("Truncated thread dump"))

    assertThat(actualDumpText).isEqualTo(expectedDumpText)
  }

  @Test
  fun `serializer writes manually assembled dump items to golden file`() {
    val expectedDumpText = loadGoldenThreadDump("serializedManual")

    val actualDumpText = serializeIntelliJThreadDump(
      listOf(
        dumpItem(
          name = "Scope Root",
          stackTrace = "",
          serializedStackTrace = serializeThreadDumpItem(
            itemHeader = "\"Scope Root\" tid=0x0 nid=NA container",
            stackTraceBody = "   Carrying virtual thread #0",
            id = 400L,
            parentId = null,
            type = IntelliJThreadDumpMetadata.CONTAINER_TYPE,
          ),
          treeId = 400L,
          parentTreeId = null,
          isContainer = true,
        ),
        dumpItem(
          name = "Scope A",
          stackTrace = "",
          serializedStackTrace = serializeThreadDumpItem(
            itemHeader = "\"Scope A\" tid=0x0 nid=NA container",
            stackTraceBody = "   Carrying virtual thread #0",
            id = 300L,
            parentId = 400L,
            type = IntelliJThreadDumpMetadata.CONTAINER_TYPE,
          ),
          treeId = 300L,
          parentTreeId = 400L,
          isContainer = true,
        ),
        dumpItem(
          name = "Scope B",
          stackTrace = "",
          serializedStackTrace = serializeThreadDumpItem(
            itemHeader = "\"Scope B\" tid=0x0 nid=NA container",
            stackTraceBody = "   Carrying virtual thread #0",
            id = 500L,
            parentId = 400L,
            type = IntelliJThreadDumpMetadata.CONTAINER_TYPE,
          ),
          treeId = 500L,
          parentTreeId = 400L,
          isContainer = true,
        ),
        dumpItem(
          name = "main",
          stackTrace = "\"main@101\" #1 prio=5 tid=0x1 nid=0x1 runnable\n   java.lang.Thread.State: RUNNABLE\n\tat example.main.run(main.java:1)",
          serializedStackTrace = serializeThreadDumpItem(
            itemHeader = "\"main@101\" #1 prio=5 tid=0x1 nid=0x1 runnable",
            stackTraceBody = "   java.lang.Thread.State: RUNNABLE\n\tat example.main.run(main.java:1)",
            id = 101L,
            parentId = 300L,
          ),
          treeId = 101L,
          parentTreeId = 300L,
          isContainer = false,
        ),
        dumpItem(
          name = "worker-1",
          stackTrace = "\"worker-1@201\" #2 tid=0x2 nid=0x2 virtual runnable\n   java.lang.Thread.State: RUNNABLE\n\tat example.worker-1.run(worker-1.java:2)",
          serializedStackTrace = serializeThreadDumpItem(
            itemHeader = "\"worker-1@201\" #2 tid=0x2 nid=0x2 virtual runnable",
            stackTraceBody = "   java.lang.Thread.State: RUNNABLE\n\tat example.worker-1.run(worker-1.java:2)",
            id = 201L,
            parentId = 500L,
          ),
          treeId = 201L,
          parentTreeId = 500L,
          isContainer = false,
        ),
        InfoDumpItem("Thread dump unavailable", "Collection of extended dump was disabled."),
      ),
      listOf("Full thread dump"),
    )

    assertThat(actualDumpText).isEqualTo(expectedDumpText)
  }

  @Test
  fun `serializer prefers exported stack trace over ui stack trace`() {
    val actualDumpText = serializeIntelliJThreadDump(
      listOf(
        dumpItem(
          name = "scope:1",
          stackTrace = "\"scope:1\" SUSPENDED [Dispatchers.Default]",
          serializedStackTrace = "\"scope:1@300\" virtual tid=0x0 nid=NA suspended [\"type\":\"coroutine\",\"id\":300,\"dispatcher\":\"Dispatchers.Default\"]",
          treeId = 300L,
          parentTreeId = null,
          isContainer = false,
        ),
      ),
    )

    assertThat(actualDumpText).contains("\"scope:1@300\" virtual tid=0x0 nid=NA suspended [\"type\":\"coroutine\",\"id\":300,\"dispatcher\":\"Dispatchers.Default\"]")
    assertThat(actualDumpText).doesNotContain("\"scope:1\" SUSPENDED [Dispatchers.Default]")
  }

  private fun createDumpItemsForExport(): List<DumpItem> {
    val dumpText = loadThreadDump("commonIntelliJFormat.txt")
    return buildList {
      addAll(requireNotNull(parseIntelliJThreadDump(dumpText)).dumpItems())
      add(InfoDumpItem("Thread dump unavailable", "Collection of extended dump was disabled."))
    }
  }

  private fun loadThreadDump(path: String): String {
    return Files.readString(Path(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/threadDump").resolve(path)).trim()
  }

  private fun loadGoldenThreadDump(path: String): String = loadThreadDump("$path.golden.txt")

  private fun dumpItem(
    name: String,
    stackTrace: String,
    serializedStackTrace: String = stackTrace,
    treeId: Long?,
    parentTreeId: Long?,
    isContainer: Boolean,
  ): DumpItem {
    return object : DumpItem {
      override val name: String = name
      override val stateDesc: String = ""
      override val stackTrace: String = stackTrace
      override val interestLevel: Int = 0
      override val icon: Icon = AllIcons.Actions.Resume
      override val iconToolTip: String? = null
      override val attributes: SimpleTextAttributes = DumpItem.RUNNING_ATTRIBUTES
      override val isDeadLocked: Boolean = false
      override val awaitingDumpItems: Set<DumpItem> = emptySet()
      override val treeId: Long? = treeId
      override val parentTreeId: Long? = parentTreeId
      override val isContainer: Boolean = isContainer
      override val canBeHidden: Boolean = false
      override fun serialize(): String = serializedStackTrace
    }
  }
}
