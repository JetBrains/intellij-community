// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.tree.events.impl

import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.pom.PomModelAspect
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.IdentityHashMap
import kotlin.random.Random

/**
 * Compares the changes of [TreeChangeImpl] with the changes of [expectedChanges], a copy of the full walk that
 * [TreeChangeImpl] used before IJPL-262558.
 */
@TestApplication
class TreeChangeImplTest {
  /** A composite with its own PSI, because [TreeChangeImpl] asserts that its parent has a PSI. */
  private class TestElement : CompositePsiElement(TYPE)

  /** Builds a parent with children, records the old state, and compares the changes after the edits. */
  private class Scenario(childLengths: List<Int>) {
    val parent: CompositeElement = TestElement()
    private val names = IdentityHashMap<ASTNode, String>()
    private val oldChildren: List<ASTNode>
    private val oldLengths = IdentityHashMap<ASTNode, Int>()
    private val oldParentLength: Int
    private val contentChanged = HashSet<ASTNode>()
    private val deltasBeforeSnapshot = ArrayList<Pair<ASTNode, Int>>()
    private var change: TreeChangeImpl? = null
    private var nextName = 0

    init {
      for (length in childLengths) {
        parent.rawAddChildren(newChild(length))
      }
      oldChildren = children()
      for (child in oldChildren) {
        oldLengths[child] = child.textLength
      }
      oldParentLength = parent.textLength
    }

    fun children(): List<ASTNode> = generateSequence(parent.firstChildNode) { it.treeNext }.toList()

    fun newChild(length: Int): CompositeElement {
      val child = TestElement()
      child.rawAddChildren(ASTFactory.leaf(TYPE, "x".repeat(length)))
      names[child] = "c${nextName++}"
      return child
    }

    /** Changes the text inside [child] before the snapshot, as a change of a descendant does. */
    fun changeContentBeforeSnapshot(child: ASTNode, newLength: Int) {
      check(change == null)
      deltasBeforeSnapshot.add(child to replaceContent(child, newLength))
    }

    fun snapshot(random: Random? = null): TreeChangeImpl {
      val result = TreeChangeImpl(parent)
      for ((child, delta) in deltasBeforeSnapshot) {
        if (random != null && random.nextBoolean()) {
          val part = random.nextInt(-3, 4)
          result.markChildChanged(child, part)
          result.markChildChanged(child, delta - part)
        }
        else {
          result.markChildChanged(child, delta)
        }
      }
      change = result
      return result
    }

    /** Changes the text inside [child] after the snapshot, as a change merged into an existing change does. */
    fun changeContentAfterSnapshot(child: ASTNode, newLength: Int) {
      replaceContent(child, newLength)
      change!!.markChildChanged(child, 0)
    }

    /** Takes the only change of [event] as the change under test. The change must be the change of [parent]. */
    fun takeChange(event: TreeChangeEventImpl) {
      assertEquals(listOf(parent), event.changedElements.toList())
      change = event.getChangesByElement(parent) as TreeChangeImpl
    }

    /** Changes the text inside [child] and returns the length change. The caller reports the change. */
    fun replaceContent(child: ASTNode, newLength: Int): Int {
      val oldLength = child.textLength
      modify { (child.firstChildNode as TreeElement).rawReplaceWithList(ASTFactory.leaf(TYPE, "y".repeat(newLength))) }
      contentChanged.add(child)
      return newLength - oldLength
    }

    fun insertBefore(anchor: ASTNode?, length: Int): ASTNode {
      val child = newChild(length)
      if (anchor == null) parent.rawAddChildren(child) else (anchor as TreeElement).rawInsertBeforeMe(child)
      return child
    }

    fun remove(child: ASTNode) {
      modify { (child as TreeElement).rawRemove() }
    }

    fun replace(child: ASTNode, length: Int): ASTNode {
      val newChild = newChild(length)
      modify { (child as TreeElement).rawReplaceWithList(newChild) }
      return newChild
    }

    /** Runs a raw edit that invalidates the removed nodes, as a PSI change does. */
    private fun modify(edit: () -> Unit) {
      DebugUtil.performPsiModification<RuntimeException>("TreeChangeImplTest", edit)
    }

    fun actual(): List<String> = actualChanges().map { describe(it) }

    private fun actualChanges(): List<ExpectedChange> {
      val change = change!!
      return change.affectedChildren.map { child ->
        val info = change.getChangeByChild(child)
        ExpectedChange(info.oldChildNode, info.newChild, info.offsetInParent, info.oldLength, info.newLength)
      }
    }

    fun expected(): List<String> = expectedChanges(oldChildren, oldLengths, contentChanged, children()).map { describe(it) }

    fun assertChanges() {
      assertEquals(expected(), actual())
      assertEquals(parent.textLength - oldParentLength, actualChanges().sumOf { it.newLength - it.oldLength })
    }

    fun describe(info: ExpectedChange): String =
      "${name(info.oldChild)}->${name(info.newChild)} at ${info.offset}, old ${info.oldLength}, new ${info.newLength}"

    private fun name(node: ASTNode?): String = if (node == null) "null" else names.getValue(node)
  }

  /** A change of a child, as the public getters of [ChangeInfoImpl] describe it. */
  private data class ExpectedChange(val oldChild: ASTNode?, val newChild: ASTNode?, val offset: Int, val oldLength: Int, val newLength: Int) {
    val affectedChild: ASTNode get() = newChild ?: oldChild!!
  }

  private companion object {
    val TYPE: IElementType = IElementType("TREE_CHANGE_IMPL_TEST", Language.ANY)

    fun newEvent(): TreeChangeEventImpl = TreeChangeEventImpl(PomModelAspect { }, FileElement(TYPE, ""))

    /** The full walk of the old children and the current children, as [TreeChangeImpl] had it before IJPL-262558. */
    fun expectedChanges(
      oldChildren: List<ASTNode>,
      oldLengths: Map<ASTNode, Int>,
      contentChanged: Set<ASTNode>,
      currentChildren: List<ASTNode>,
    ): Collection<ExpectedChange> {
      val oldSet = oldChildren.toCollection(HashSet())
      val currentSet = currentChildren.toCollection(HashSet())
      val result = LinkedHashMap<ASTNode, ExpectedChange>()
      var oldIndex = 0
      var newIndex = 0
      var oldOffset = 0

      fun addChange(oldChild: ASTNode?, newChild: ASTNode?, oldLength: Int) {
        val change = ExpectedChange(oldChild, newChild, oldOffset, oldLength, newChild?.textLength ?: 0)
        result[change.affectedChild] = change
        oldOffset += change.newLength - change.oldLength
      }

      while (oldIndex < oldChildren.size || newIndex < currentChildren.size) {
        val oldChild = oldChildren.getOrNull(oldIndex)
        val newChild = currentChildren.getOrNull(newIndex)
        if (oldChild === newChild) {
          val oldLength = oldLengths.getValue(oldChild!!)
          if (oldChild in contentChanged) {
            addChange(oldChild, oldChild, oldLength)
          }
          oldOffset += oldLength
          oldIndex++
          newIndex++
        }
        else {
          val oldDisappeared = oldChild != null && oldChild !in currentSet
          val newAppeared = newChild != null && newChild !in oldSet
          val oldLength = if (oldDisappeared) oldLengths.getValue(oldChild) else 0
          addChange(if (oldDisappeared) oldChild else null, if (newAppeared) newChild else null, oldLength)
          if (oldDisappeared) {
            oldOffset += oldLength
            oldIndex++
          }
          if (newAppeared) {
            newIndex++
          }
        }
      }
      return result.values
    }
  }

  @Test
  fun `no change gives no changes`() {
    val scenario = Scenario(listOf(1, 2, 3))
    scenario.snapshot()
    assertEquals(emptyList<String>(), scenario.actual())
    scenario.assertChanges()
  }

  @Test
  fun `an empty parent gets children`() {
    val scenario = Scenario(emptyList())
    scenario.snapshot()
    scenario.insertBefore(null, 2)
    scenario.insertBefore(null, 3)
    assertEquals(listOf("null->c0 at 0, old 0, new 2", "null->c1 at 2, old 0, new 3"), scenario.actual())
    scenario.assertChanges()
  }

  @Test
  fun `all children are removed`() {
    val scenario = Scenario(listOf(1, 2))
    scenario.snapshot()
    scenario.children().forEach(scenario::remove)
    assertEquals(listOf("c0->null at 0, old 1, new 0", "c1->null at 0, old 2, new 0"), scenario.actual())
    scenario.assertChanges()
  }

  @Test
  fun `a change at the end has only a common prefix`() {
    val scenario = Scenario(listOf(1, 2, 3))
    scenario.snapshot()
    scenario.insertBefore(null, 4)
    assertEquals(listOf("null->c3 at 6, old 0, new 4"), scenario.actual())
    scenario.assertChanges()
  }

  @Test
  fun `a change at the start has only a common suffix`() {
    val scenario = Scenario(listOf(1, 2, 3))
    scenario.snapshot()
    scenario.remove(scenario.children().first())
    assertEquals(listOf("c0->null at 0, old 1, new 0"), scenario.actual())
    scenario.assertChanges()
  }

  @Test
  fun `a replacement in the middle`() {
    val scenario = Scenario(listOf(1, 2, 3))
    scenario.snapshot()
    scenario.replace(scenario.children()[1], 5)
    assertEquals(listOf("c1->c3 at 1, old 2, new 5"), scenario.actual())
    scenario.assertChanges()
  }

  @Test
  fun `content changes in the prefix and in the suffix keep the old lengths`() {
    val scenario = Scenario(listOf(1, 2, 3, 4))
    val (first, second, third, fourth) = scenario.children()
    scenario.changeContentBeforeSnapshot(first, 5)
    scenario.changeContentBeforeSnapshot(fourth, 1)
    scenario.snapshot()
    scenario.changeContentAfterSnapshot(third, 6)
    scenario.remove(second)
    assertEquals(listOf("c0->c0 at 0, old 1, new 5", "c1->null at 5, old 2, new 0", "c2->c2 at 5, old 3, new 6", "c3->c3 at 11, old 4, new 1"),
                 scenario.actual())
    scenario.assertChanges()
  }

  @Test
  fun `random edits give the changes of the full walk`() {
    repeat(2000) { seed ->
      val random = Random(seed)
      val scenario = Scenario(List(random.nextInt(0, 8)) { random.nextInt(1, 4) })
      for (child in scenario.children()) {
        if (random.nextInt(4) == 0) scenario.changeContentBeforeSnapshot(child, random.nextInt(1, 4))
      }
      scenario.snapshot(random)
      repeat(random.nextInt(0, 5)) {
        val children = scenario.children()
        val child = if (children.isEmpty()) null else children[random.nextInt(children.size)]
        when {
          child == null || random.nextInt(4) == 0 -> {
            scenario.insertBefore(children.getOrNull(random.nextInt(children.size + 1)), random.nextInt(1, 4))
          }
          random.nextBoolean() -> scenario.remove(child)
          random.nextBoolean() -> scenario.replace(child, random.nextInt(1, 4))
          else -> scenario.changeContentAfterSnapshot(child, random.nextInt(1, 4))
        }
      }
      try {
        scenario.assertChanges()
      }
      catch (e: AssertionError) {
        throw AssertionError("seed $seed", e)
      }
    }
  }

  @Test
  fun `the initial children are the old children and are read-only`() {
    val scenario = Scenario(listOf(1, 2, 3))
    val oldChildren = scenario.children()
    val change = scenario.snapshot()
    scenario.remove(oldChildren.first())
    scenario.insertBefore(null, 4)
    val initial = change.initialChildren
    assertEquals(oldChildren, initial)
    assertThrows<UnsupportedOperationException> { (initial as MutableList<ASTNode>).add(oldChildren.first()) }
  }

  @Test
  fun `a large parent gives the changes of the full walk`() {
    val scenario = Scenario(List(60_000) { it % 3 + 1 })
    val children = scenario.children()
    scenario.changeContentBeforeSnapshot(children[5], 7)
    scenario.snapshot()
    scenario.changeContentAfterSnapshot(children[59_995], 8)
    scenario.remove(children[30_000])
    scenario.insertBefore(children[30_010], 5)
    scenario.replace(children[59_990], 6)
    assertEquals(5, scenario.actual().size)
    scenario.assertChanges()
  }

  @Test
  fun `a nested change before the change of the parent is merged into it`() {
    val scenario = Scenario(listOf(1, 2, 3, 4))
    val (_, second, third, _) = scenario.children()
    val event = newEvent()
    event.addElementaryChange(third)
    scenario.replaceContent(third, 6)
    event.addElementaryChange(scenario.parent)
    scenario.remove(second)
    scenario.takeChange(event)
    assertEquals(listOf("c1->null at 1, old 2, new 0", "c2->c2 at 1, old 3, new 6"), scenario.actual())
    scenario.assertChanges()
  }

  @Test
  fun `a nested change after the change of the parent is integrated into it`() {
    val scenario = Scenario(listOf(1, 2, 3, 4))
    val (_, second, third, _) = scenario.children()
    val event = newEvent()
    event.addElementaryChange(scenario.parent)
    scenario.remove(second)
    event.addElementaryChange(third)
    scenario.replaceContent(third, 6)
    event.addElementaryChange(third)
    scenario.replaceContent(third, 5)
    scenario.takeChange(event)
    assertEquals(listOf("c1->null at 1, old 2, new 0", "c2->c2 at 1, old 3, new 5"), scenario.actual())
    scenario.assertChanges()
  }

  @Test
  fun `a merged event adds its content changes to the change of the same parent`() {
    val scenario = Scenario(listOf(1, 2, 3, 4))
    val (_, second, third, fourth) = scenario.children()
    val event = newEvent()
    event.addElementaryChange(scenario.parent)
    scenario.remove(second)
    val next = newEvent()
    next.addElementaryChange(third)
    scenario.replaceContent(third, 6)
    next.addElementaryChange(scenario.parent)
    scenario.remove(fourth)
    event.merge(next)
    scenario.takeChange(event)
    assertEquals(listOf("c1->null at 1, old 2, new 0", "c2->c2 at 1, old 3, new 6", "c3->null at 7, old 4, new 0"),
                 scenario.actual())
    scenario.assertChanges()
  }
}