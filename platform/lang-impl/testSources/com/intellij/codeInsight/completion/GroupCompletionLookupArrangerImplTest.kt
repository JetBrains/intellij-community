// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.completion.group.CompletionGroup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class GroupCompletionLookupArrangerImplTest {

  // --- computeGroupSeparators: skip / fallback cases ---

  @Test
  fun `returns null when supportGroups is false`() {
    val model = listOf(plain("a"), grouped("b", G1))
    assertNull(computeGroupSeparators(model = model, supportGroups = false, hasGroups = true))
  }

  @Test
  fun `returns null when hasGroups is false`() {
    val model = listOf(plain("a"), grouped("b", G1))
    assertNull(computeGroupSeparators(model = model, supportGroups = true, hasGroups = false))
  }

  @Test
  fun `returns null on empty model`() {
    assertNull(compute(emptyList()))
  }

  @Test
  fun `returns null when last element is not grouped`() {
    assertNull(compute(listOf(grouped("a", G1), plain("b"))))
  }

  @Test
  fun `returns null when grouped element appears above ungrouped`() {
    // grouped(G1), plain, grouped(G1) — the bottom group "leaks" above an ungrouped row.
    assertNull(compute(listOf(grouped("a", G1), plain("b"), grouped("c", G1))))
  }

  @Test
  fun `returns null when group transitions repeat`() {
    // [g1, g2, g1, g2]: g1 is transitioned-to twice while scanning bottom-up.
    assertNull(compute(listOf(grouped("a", G1), grouped("b", G2), grouped("c", G1), grouped("d", G2))))
  }

  // --- computeGroupSeparators: positive cases ---

  @Test
  fun `single group whole model`() {
    val separators = compute(listOf(grouped("a", G1), grouped("b", G1)))
    requireNotNull(separators)
    assertEquals(1, separators.size)
    assertEquals(0, separators.first().insertionIndex)
    assertEquals(G1, separators.first().group)
  }

  @Test
  fun `single group as suffix`() {
    val separators = compute(listOf(plain("a"), plain("b"), grouped("c", G1), grouped("d", G1)))
    requireNotNull(separators)
    assertEquals(1, separators.size)
    assertEquals(2, separators.first().insertionIndex)
    assertEquals(G1, separators.first().group)
  }

  @Test
  fun `two adjacent groups in suffix`() {
    val separators = compute(listOf(plain("a"), grouped("b", G1), grouped("c", G2), grouped("d", G2)))
    requireNotNull(separators)
    assertEquals(2, separators.size)
    assertEquals(GroupSeparator(G1, 1), separators[0])
    assertEquals(GroupSeparator(G2, 2), separators[1])
  }

  @Test
  fun `three adjacent groups no ungrouped prefix`() {
    val separators = compute(listOf(grouped("a", G1), grouped("b", G2), grouped("c", G3)))
    requireNotNull(separators)
    assertEquals(listOf(GroupSeparator(G1, 0), GroupSeparator(G2, 1), GroupSeparator(G3, 2)), separators)
  }

  @Test
  fun `boundaries are returned top-down`() {
    val separators = compute(listOf(plain("a"), grouped("b", G1), grouped("c", G2), grouped("d", G3)))
    requireNotNull(separators)
    var prev = -1
    for ((_, insertionIndex) in separators) {
      assertTrue(insertionIndex >= prev, "indices must be non-decreasing top-down")
      prev = insertionIndex
    }
  }

  // --- insertGroupSeparators ---

  @Test
  fun `insert nothing when separators empty`() {
    val model = mutableListOf(plain("a"), plain("b"))
    insertGroupSeparators(model, emptyList()) { g -> error("factory must not be called for $g") }
    assertEquals(listOf("a", "b"), model.lookupStrings())
  }

  @Test
  fun `insert single separator`() {
    val model = mutableListOf(plain("a"), plain("b"), grouped("c", G1))
    insertGroupSeparators(model, listOf(GroupSeparator(G1, 2)), separatorFactory)
    assertEquals(listOf("a", "b", "SEP:G1", "c"), model.lookupStrings())
  }

  @Test
  fun `insert multiple separators accounts for shift`() {
    // Locks in the `+ i` offset: each previously inserted separator shifts later target indices by one.
    val model = mutableListOf(plain("a"), plain("b"), plain("c"), plain("d"))
    insertGroupSeparators(model, listOf(GroupSeparator(G1, 1), GroupSeparator(G2, 3)), separatorFactory)
    assertEquals(listOf("a", "SEP:G1", "b", "c", "SEP:G2", "d"), model.lookupStrings())
  }

  @Test
  fun `insert passes correct group to factory in order`() {
    val model = mutableListOf(plain("a"), plain("b"))
    val received = mutableListOf<CompletionGroup>()
    insertGroupSeparators(model, listOf(GroupSeparator(G1, 0), GroupSeparator(G2, 1))) { g ->
      received.add(g)
      LookupElementBuilder.create("sep")
    }
    assertEquals(listOf(G1, G2), received)
  }
}

private val G1 = CompletionGroup(1, "G1")
private val G2 = CompletionGroup(2, "G2")
private val G3 = CompletionGroup(3, "G3")

private val separatorFactory = { g: CompletionGroup ->
  LookupElementBuilder.create("SEP:${g.displayName()}")
}

private fun plain(name: String): LookupElement = LookupElementBuilder.create(name)

private fun grouped(name: String, group: CompletionGroup): LookupElement =
  LookupElementBuilder.create(name).also { group.installTo(it) }

private fun compute(model: List<LookupElement>): List<GroupSeparator>? =
  computeGroupSeparators(model = model, supportGroups = true, hasGroups = true)

private fun List<LookupElement>.lookupStrings(): List<String> = map { it.lookupString }
