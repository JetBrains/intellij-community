// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.threadDumpParser.ThreadState
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.EmptyIcon
import java.util.Objects
import javax.swing.Icon

class DumpItemMergeTest : BasePlatformTestCase() {
  fun testMergingDoesNotBreakParents() {
    val a1 = TestDumpItem("A1", "axaaaaa", 0, null)
    val a2 = TestDumpItem("A2", "bbbbbb", 1, 0) // This item should not be merged with b2, their parents are different
    val a3 = TestDumpItem("A3", "bbbbbb", 2, 0) // This item should not be merged with b2, their parents are different
    val a4 = CompoundDumpItem(TestDumpItem("A4", "kkkkkkkk", 3, 1), 2)

    val b1 = TestDumpItem("C", "cccccc", 4, null)
    val b2 = TestDumpItem("B", "bbbbbb", 5, null)

    val items = listOf(b1, b2, a1, a2, a3, a4)
    val mergedItems = CompoundDumpItem.mergeThreadDumpItems(items)
    assertEquals("C\n" +
                            "B\n" +
                            "A1\n" +
                            "-A2\n" +
                            "--A4 [and 1 similar]\n" +
                            "-A3\n", printTree(items))
    assertEquals("C\n" +
                            "B\n" +
                            "A1\n" +
                            "-A2 [and 1 similar]\n" +
                            "--A4 [and 1 similar]\n", printTree(mergedItems))
  }

  fun testMergingJavaThreadDumpItems() {
    val threadState1 = ThreadState("A1", "aaaaaaa").also { it.uniqueId = 0; it.threadContainerUniqueId = null }
    val threadState2 = ThreadState("A2", "bbbbbb").also { it.uniqueId = 1; it.threadContainerUniqueId = 0 }
    val threadState3 = ThreadState("A3", "bbbbbb").also { it.uniqueId = 2; it.threadContainerUniqueId = 0 }
    val threadState4 = ThreadState("A4", "kkkkkkkk").also { it.uniqueId = 3; it.threadContainerUniqueId = 1; it.similarThreadsCount = 2 }

    val threadState5 = ThreadState("C", "cccccc").also { it.uniqueId = 4; it.threadContainerUniqueId = null; }
    val threadState6 = ThreadState("B", "bbbbbb").also { it.uniqueId = 5; it.threadContainerUniqueId = null; }

    val dumpItems = toDumpItems(listOf( threadState5, threadState6, threadState1, threadState2, threadState3, threadState4))
    val mergedItems = CompoundDumpItem.mergeThreadDumpItems(dumpItems)
    assertEquals("C\n" +
                 "B\n" +
                 "A1\n" +
                 "-A2\n" +
                 "--A4 [and 1 similar]\n" +
                 "-A3\n", printTree(dumpItems))
    assertEquals("C\n" +
                 "B\n" +
                 "A1\n" +
                 "-A2 [and 1 similar]\n" +
                 "--A4 [and 1 similar]\n", printTree(mergedItems))
  }

  private fun printTree(items: List<DumpItem>): String {
    val byParent: Map<Long?, List<DumpItem>> = items.groupBy { it.parentTreeId }
    val allIds = items.mapNotNullTo(HashSet()) { it.treeId }
    val roots = items.filter { item ->
      item.parentTreeId == null || item.parentTreeId !in allIds
    }

    fun StringBuilder.printNode(item: DumpItem, indent: Int) {
      appendLine("${"-".repeat(indent)}${item.name}")
      val children = item.treeId?.let { byParent[it] } ?: emptyList()
      for (child in children) {
        printNode(child, indent + 1)
      }
    }

    return buildString {
      for (root in roots) {
        printNode(root, 0)
      }
    }
  }
}

private class TestDumpItem(
  override val name: String,
  override val stackTrace: String,
  override val treeId: Long? = null,
  override val parentTreeId: Long? = null
) : MergeableDumpItem {

  override val mergeableToken: MergeableToken
    get() = TestMergeableToken()

  private inner class TestMergeableToken : MergeableToken {
    override val item get() = this@TestDumpItem

    override fun equals(other: Any?): Boolean {
      if (other !is TestMergeableToken) return false
      val otherItem = other.item
      if (stackTrace != otherItem.stackTrace) return false
      if (parentTreeId != otherItem.parentTreeId) return false
      return true
    }

    override fun hashCode(): Int {
      return Objects.hash(
        stackTrace,
        parentTreeId
      )
    }
  }

  override val stateDesc: String get() = ""
  override val interestLevel: Int get() = 0
  override val icon: Icon get() = EmptyIcon.ICON_16
  override val iconToolTip: String? get() = null
  override val attributes: SimpleTextAttributes get() = SimpleTextAttributes.REGULAR_ATTRIBUTES
  override val isDeadLocked: Boolean get() = false
  override val awaitingDumpItems: Set<DumpItem> get() = emptySet()
  override val isContainer: Boolean get() = false
  override val canBeHidden: Boolean get() = false
  override fun serialize(): String = buildString { append(name).append(treeId).append(parentTreeId).append(stackTrace) }
}
