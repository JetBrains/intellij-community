// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.TestDataPath
import com.intellij.ui.SimpleTextAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Files
import javax.swing.Icon
import kotlin.io.path.Path

@TestDataPath($$"$CONTENT_ROOT/testData/threadDump")
internal class IntelliJThreadDumpExportTest {
  @Test
  fun `serializer writes documented format and skips info items`() {
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
          treeId = 400L,
          parentTreeId = null,
          isContainer = true,
        ),
        dumpItem(
          name = "Scope A",
          stackTrace = "",
          treeId = 300L,
          parentTreeId = 400L,
          isContainer = true,
        ),
        dumpItem(
          name = "Scope B",
          stackTrace = "",
          treeId = 500L,
          parentTreeId = 400L,
          isContainer = true,
        ),
        dumpItem(
          name = "main",
          stackTrace = """
            "main@101" #1 prio=5 tid=0x1 nid=0x1 runnable
               java.lang.Thread.State: RUNNABLE
            	at example.main.run(main.java:1)
          """.trimIndent(),
          treeId = 101L,
          parentTreeId = 300L,
          isContainer = false,
        ),
        dumpItem(
          name = "worker-1",
          stackTrace = """
            "worker-1@201" #2 tid=0x2 nid=0x2 virtual runnable
               java.lang.Thread.State: RUNNABLE
            	at example.worker-1.run(worker-1.java:2)
          """.trimIndent(),
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
  fun `serializer writes metadata without body and skips containers without ids`() {
    val actualDumpText = serializeIntelliJThreadDump(
      listOf(
        dumpItem(
          name = "No Id",
          stackTrace = "",
          treeId = null,
          parentTreeId = null,
          isContainer = true,
        ),
        dumpItem(
          name = "Zero Id",
          stackTrace = "",
          treeId = 0L,
          parentTreeId = 0L,
          isContainer = true,
        ),
        InfoDumpItem("Thread dump unavailable", "Collection of extended dump was disabled."),
      ),
    )

    assertThat(actualDumpText).isEqualTo(
      """
      # This dump may be opened in IntelliJ IDEA using Analyze Stack Trace or Thread Dump...
      
      ${IntelliJThreadDumpMetadata.META_DATA_MARKER}
      {
          "version": 1,
          "tree_links": [
              {
                  "tree_id": 0,
                  "parent_tree_id": 0
              }
          ],
          "containers": [
              {
                  "name": "Zero Id",
                  "tree_id": 0
              }
          ]
      }
      """.trimIndent(),
    )
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
    }
  }
}
