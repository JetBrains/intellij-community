// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.Path

@TestApplication
internal class JcmdJsonThreadDumpParserTest {
  @Test
  fun `hierarchy is parsed correctly`() {
    val dumpItems = parseThreadDump("jcmdJsonNestedContainers.json")

    val dumpTree = dumpItemsTree(dumpItems)
    assertTrue(dumpTree.contains(
      "[ForkJoinPool-1/jdk.internal.vm.SharedThreadContainer@4455e221]\n" +
      " ForkJoinPool-1-worker-1  (RUNNABLE)\n" +
      " ForkJoinPool-1-worker-2  (WAITING)\n" +
      " ForkJoinPool-1-worker-3  (WAITING)")
    )
    assertTrue(dumpTree.contains(
      "main  (WAITING)\n" +
      " [Main Scope/jdk.internal.misc.ThreadFlock\$ThreadContainerImpl@272d6d25]\n" +
      "  myVT@1111  (WAITING)\n" +
      "   [Group-A/jdk.internal.misc.ThreadFlock\$ThreadContainerImpl@311ab950]\n" +
      "    myVT@A11  (RUNNABLE)\n" +
      "    myVT@A12  (RUNNABLE)\n" +
      "  myVT@2222  (WAITING)\n" +
      "   [Group-A/jdk.internal.misc.ThreadFlock\$ThreadContainerImpl@7fbd07ee]\n" +
      "    myVT@A21  (RUNNABLE)\n" +
      "    myVT@A22  (RUNNABLE)\n" +
      "  myVT@3333  (WAITING)\n" +
      "   [Group-B/jdk.internal.misc.ThreadFlock\$ThreadContainerImpl@33b50979]\n" +
      "    myVT@B  (RUNNABLE)\n" +
      "    myVT@B  (RUNNABLE)\n" +
      "    myVT@B  (RUNNABLE)")
    )
    assertTrue(dumpTree.contains("rootContainerThread  (RUNNABLE)"))
    assertTrue(dumpTree.contains("Reference Handler  (RUNNABLE)"))
    assertFalse(dumpTree.contains("Root Container")) // should not contain root container
  }

  @Test
  fun `virtual threads are found`() {
    val dumpItems = parseThreadDump("jcmdJsonNestedContainers.json")

    assertTrue(findDumpItem(dumpItems, "myVT@1111").isVirtual())
    assertTrue(findDumpItem(dumpItems, "myVT@A11").isVirtual())
    assertTrue(findDumpItem(dumpItems, "myVT@A21").isVirtual())
    assertTrue(findDumpItem(dumpItems, "myVT@B").isVirtual())
    assertFalse(findDumpItem(dumpItems, "ForkJoinPool-1-worker-1").isVirtual())
    assertFalse(findDumpItem(dumpItems, "main").isVirtual())
  }

  @Test
  fun `check stackTraces`() {
    val dumpItems = parseThreadDump("jcmdJsonNestedContainers.json")

    assertEquals("\"myVT@1111\" tid=40 virtual unmounted WAITING\n" +
                 "\tat java.base/java.lang.VirtualThread.park(VirtualThread.java:738)\n" +
                 "\tat java.base/java.lang.System\$1.parkVirtualThread(System.java:2284)\n" +
                 "\tat java.base/java.util.concurrent.locks.LockSupport.park(LockSupport.java:367)\n" +
                 "\tat java.base/jdk.internal.misc.ThreadFlock.awaitAll(ThreadFlock.java:305)\n" +
                 "\tat java.base/java.util.concurrent.StructuredTaskScopeImpl.join(StructuredTaskScopeImpl.java:243)\n" +
                 "\tat Main.processGroupImpl(Main.java:72)\n" +
                 "\tat Main.run(Main.java:56)\n" +
                 "\tat Main.lambda\$runNestedScopes\$1(Main.java:32)\n" +
                 "\tat java.base/java.util.concurrent.StructuredTaskScopeImpl\$SubtaskImpl.run(StructuredTaskScopeImpl.java:325)\n" +
                 "\tat java.base/java.lang.VirtualThread.run(VirtualThread.java:456)",
                 findDumpItem(dumpItems, "myVT@1111").stackTrace)

    assertEquals("\"myVT@A11\" tid=56 virtual carrierId=59 RUNNABLE\n" +
                 "\tat Main.lambda\$run\$0(Main.java:59)\n" +
                 "\tat java.base/java.util.concurrent.StructuredTaskScopeImpl\$SubtaskImpl.run(StructuredTaskScopeImpl.java:325)\n" +
                 "\tat java.base/java.lang.VirtualThread.run(VirtualThread.java:456)",
                 findDumpItem(dumpItems, "myVT@A11").stackTrace)

    assertEquals("\"main\" tid=3 WAITING\n" +
                 "\tat java.base/jdk.internal.misc.Unsafe.park(Native Method)\n" +
                 "\tat java.base/java.util.concurrent.locks.LockSupport.park(LockSupport.java:369)\n" +
                 "\tat java.base/jdk.internal.misc.ThreadFlock.awaitAll(ThreadFlock.java:305)\n" +
                 "\tat java.base/java.util.concurrent.StructuredTaskScopeImpl.join(StructuredTaskScopeImpl.java:243)\n" +
                 "\tat Main.runNestedScopes(Main.java:47)\n" +
                 "\tat Main.main(Main.java:11)",
                 findDumpItem(dumpItems, "main").stackTrace)
  }

  @Test
  fun `parser returns null for non-json input`() {
    assertNull(parseJcmdJsonThreadDump("\"main\" #1 prio=5 tid=0x1 nid=0x1 runnable"))
  }

  @Test
  fun `parser returns null for json without threadContainers`() {
    assertNull(parseJcmdJsonThreadDump("""{"foo": "bar"}"""))
  }

  @Test
  fun `parser returns null for malformed json`() {
    assertNull(parseJcmdJsonThreadDump("{invalid json"))
  }

  @Test
  fun `parser handles only root container`() {
    val json = """
      {
        "threadDump": {
          "threadContainers": [
            {
              "container": "<root>",
              "parent": null,
              "owner": null,
              "threads": [
                {
                  "tid": "1",
                  "name": "main",
                  "state": "RUNNABLE",
                  "stack": ["example.Main.main(Main.java:1)"]
                }
              ],
              "threadCount": "1"
            }
          ]
        }
      }
    """.trimIndent()

    val parsed = requireNotNull(parseJcmdJsonThreadDump(json))
    assertThat(parsed.threadStates).hasSize(1)
    assertEquals("main", parsed.threadStates[0].name)
    // Root is skipped, so no container descriptors
    assertThat(parsed.threadContainerDescriptors).isEmpty()
    // Thread in root has null container parent
    assertNull(parsed.threadStates[0].threadContainerUniqueId)
  }

  // ----------------------- LOCK TESTS ----------------------- //

  @Test
  fun `parser detects deadlock from json lock fields`() {
    val dumpText = loadThreadDump("jcmdJsonDeadlock.json")
    val parsed = requireNotNull(parseJcmdJsonThreadDump(dumpText))

    val thread1 = parsed.threadStates.first { it.name == "Thread-1" }
    val thread2 = parsed.threadStates.first { it.name == "Thread-2" }

    assertTrue(thread1.isDeadlocked)
    assertTrue(thread2.isDeadlocked)
    assertThat(thread1.deadlockedThreads).contains(thread2)
    assertThat(thread2.deadlockedThreads).contains(thread1)

    val mainThread = parsed.threadStates.first { it.name == "main" }
    assertFalse(mainThread.isDeadlocked)
  }

  @Test
  fun `multiple owned monitors`() {
    val dumpText = loadThreadDump("multipleOwnedMonitors.json")
    val parsed = requireNotNull(parseJcmdJsonThreadDump(dumpText))
    val threadStates = parsed.threadStates

    val thread = threadStates.first { it.name == "multi-lock-thread" }

    assertEquals(3, thread.ownedMonitors.size)
    assertEquals("java.lang.Object@79177b4a," +
                 "java.lang.Object@300ce97d," +
                 "java.lang.Object@4fd73c93", thread.ownedMonitors.joinToString(","))

    val main = threadStates.first { it.name == "main" }
    assertTrue(main.ownedMonitors.isEmpty())
  }

  @Test
  fun `contended monitors`() {
    val dumpText = loadThreadDump("jcmdJsonDeadlock.json")
    val parsed = requireNotNull(parseJcmdJsonThreadDump(dumpText))

    val main = parsed.threadStates.first { it.name == "main" }
    assertEquals("java.lang.Thread@6a90c472", main.contendedMonitor)

    val thread1 = parsed.threadStates.first { it.name == "Thread-1" }
    assertEquals("java.lang.Object@4a1a69c2", thread1.contendedMonitor)
  }

  @Test
  fun `stack trace enriched with locked monitors at correct depth`() {
    val dumpText = loadThreadDump("multipleOwnedMonitors.json")
    val parsed = requireNotNull(parseJcmdJsonThreadDump(dumpText))

    val thread = parsed.threadStates.first { it.name == "multi-lock-thread" }
    assertEquals(
      "\"multi-lock-thread\" tid=39 TIMED_WAITING\n" +
      "\tat java.base/java.lang.Thread.sleepNanos0(Native Method)\n" +
      "\tat java.base/java.lang.Thread.sleepNanos(Thread.java:509)\n" +
      "\tat java.base/java.lang.Thread.sleep(Thread.java:540)\n" +
      "\tat MultiLockExample.lambda\$main\$0(MultiLockExample.java:14)\n" +
      "\t  - locked java.lang.Object@79177b4a\n" +
      "\t  - locked java.lang.Object@300ce97d\n" +
      "\t  - locked java.lang.Object@4fd73c93\n" +
      "\tat java.base/java.lang.Thread.run(Thread.java:1474)\n",
      thread.stackTrace
    )
  }

  @Test
  fun `locked monitors at unknown depth are appended to the end of the stack trace`() {
    val dumpText = loadThreadDump("multilock-unknownDepth.json")
    val parsed = requireNotNull(parseJcmdJsonThreadDump(dumpText))

    val thread = parsed.threadStates.first { it.name == "multi-lock-thread" }
    assertEquals(
      "\"multi-lock-thread\" tid=39 TIMED_WAITING\n" +
      "\tat java.base/java.lang.Thread.sleepNanos0(Native Method)\n" +
      "\tat java.base/java.lang.Thread.sleepNanos(Thread.java:509)\n" +
      "\tat java.base/java.lang.Thread.sleep(Thread.java:540)\n" +
      "\tat MultiLockExample.lambda\$main\$0(MultiLockExample.java:14)\n" +
      "\tat java.base/java.lang.Thread.run(Thread.java:1474)\n" +
      "\t  - locked java.lang.Object@79177b4a\n" +
      "\t  - locked java.lang.Object@300ce97d\n" +
      "\t  - locked java.lang.Object@4fd73c93\n",
      thread.stackTrace
    )
  }

  @Test
  fun `monitor relinquished via Object wait is excluded from owned monitors`() {
    val dumpText = loadThreadDump("multipleOwnedMonitors.json")
    val parsed = requireNotNull(parseJcmdJsonThreadDump(dumpText))

    val main = parsed.threadStates.first { it.name == "main" }

    assertThat(main.ownedMonitors).doesNotContain("java.lang.Thread@48b647c3")
    assertThat(main.stackTrace).doesNotContain("- locked")
  }

  @Test
  fun `stack trace not modified when no lock info`() {
    val dumpText = loadThreadDump("multipleOwnedMonitors.json")
    val parsed = requireNotNull(parseJcmdJsonThreadDump(dumpText))

    val refHandler = parsed.threadStates.first { it.name == "Reference Handler" }

    assertEquals(
      "\"Reference Handler\" tid=15 RUNNABLE\n" +
      "\tat java.base/java.lang.ref.Reference.waitForReferencePendingList(Native Method)\n" +
      "\tat java.base/java.lang.ref.Reference.processPendingReferences(Reference.java:246)\n" +
      "\tat java.base/java.lang.ref.Reference\$ReferenceHandler.run(Reference.java:208)",
      refHandler.stackTrace
    )
  }

  @Test
  fun `deadlock stack traces contain locked monitors and cross-thread info`() {
    val dumpText = loadThreadDump("jcmdJsonDeadlock.json")
    val parsed = requireNotNull(parseJcmdJsonThreadDump(dumpText))

    val thread1 = parsed.threadStates.first { it.name == "Thread-1" }
    assertEquals(
      "\"Thread-1\" tid=35 BLOCKED\n" +
      "\t blocks Thread-2\n" +
      "\t waiting for Thread-2 to release lock on java.lang.Object@4a1a69c2\n" +
      "\tat DeadlockExample.lambda\$main\$0(DeadlockExample.java:11)\n" +
      "\t  - locked java.lang.Object@5327a936\n" +
      "\tat java.base/java.lang.Thread.run(Thread.java:1474)\n",
      thread1.stackTrace
    )

    val thread2 = parsed.threadStates.first { it.name == "Thread-2" }
    assertEquals(
      "\"Thread-2\" tid=36 BLOCKED\n" +
      "\t blocks Thread-1\n" +
      "\t waiting for Thread-1 to release lock on java.lang.Object@5327a936\n" +
      "\tat DeadlockExample.lambda\$main\$1(DeadlockExample.java:21)\n" +
      "\t  - locked java.lang.Object@4a1a69c2\n" +
      "\tat java.base/java.lang.Thread.run(Thread.java:1474)\n",
      thread2.stackTrace
    )
  }

  // ----------------------- ROUND-TRIP TEST ----------------------- //

  @Test
  fun `jcmd json to intellij format round trip preserves threads and hierarchy`() { // TODO: the hierarchy should be preserved after serialization
    val jcmdDumpItems = parseThreadDump("jcmdJsonNestedContainers.json")

    val serialized = serializeIntelliJThreadDump(jcmdDumpItems)
    val parsedSerializedDumpItems = requireNotNull(parseIntelliJThreadDump(serialized)).dumpItems()

    assertEquals(dumpItemsTree(jcmdDumpItems), dumpItemsTree(parsedSerializedDumpItems))
  }

  @Test
  fun `jcmd json to intellij format round trip preserves deadlock lock annotations`() {
    val dumpText = loadThreadDump("jcmdJsonDeadlock.json")
    val originalThreadDump = requireNotNull(parseJcmdJsonThreadDump(dumpText))

    val serialized = serializeIntelliJThreadDump(originalThreadDump.dumpItems())
    val parsedSerializedThreadDump = requireNotNull(parseIntelliJThreadDump(serialized))

    for (i in originalThreadDump.threadStates.indices) {
      val threadState1 = originalThreadDump.threadStates[i]
      val threadState2 = parsedSerializedThreadDump.threadStates[i]
      assertEquals(threadState1.stackTrace.trimEnd(), threadState2.stackTrace.trimEnd())
      assertEquals(threadState1.isDeadlocked, threadState2.isDeadlocked)
      assertEquals(threadState1.deadlockedThreads.map { it.name }, threadState2.deadlockedThreads.map { it.name })
      assertEquals(threadState1.awaitingThreads.map { it.name }, threadState2.awaitingThreads.map { it.name })
    }
  }

  private fun parseThreadDump(dumpFileName: String): List<MergeableDumpItem> {
    val dumpText = loadThreadDump(dumpFileName)

    val parsed = requireNotNull(parseJcmdJsonThreadDump(dumpText))
    return parsed.dumpItems()
  }

  private fun loadThreadDump(path: String): String {
    return Files.readString(Path(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/threadDump").resolve(path)).trim()
  }

  private fun dumpItemsTree(dumpItems: List<DumpItem>): String {
    val childrenByParent = dumpItems.groupBy { it.parentTreeId }
    val sb = StringBuilder()

    fun appendItem(item: DumpItem, indent: Int) {
      val prefix = " ".repeat(indent)
      val label = if (item.isContainer) "[${item.name}]" else item.name
      sb.append(prefix).append(label)
      val state = item.stateDesc
      if (state.isNotEmpty()) sb.append(" ").append(state)
      sb.append("\n")
      val children = childrenByParent[item.treeId] ?: return
      for (child in children) {
        appendItem(child, indent + 1)
      }
    }

    val roots = childrenByParent[null] ?: emptyList()
    for (root in roots) {
      appendItem(root, 0)
    }
    return sb.toString().trimEnd()
  }

  private fun findDumpItem(dumpItems: List<DumpItem>, name: String): DumpItem =
    dumpItems.firstOrNull { it.name == name }
      ?: error("Dump item not found: $name, available: ${dumpItems.map { it.name }}")

  private fun DumpItem.isVirtual() = stackTrace.contains("virtual")
}
