// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.cleanLinesOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.deleteOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.insertOp
import com.intellij.openapi.editor.ex.DocumentOp.Companion.modStampOp
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.impl.DocumentSnapshotImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The rules that `DocumentSnapshotTest` states for a [DocumentSnapshot], checked on a snapshot and on a
 * [DocumentBranch] side by side.
 */
internal class DocBranchSnapshotRulesTest {

  @Test
  fun `a stamp op that changes nothing keeps the instance`() {
    for (value in values("abc")) {
      val sameStamp = modStampOp(value.modState().stamp(), false)
      assertTrue(value.isSameAs(value.applyOp(sameStamp)), value.kind)
    }
  }

  @Test
  fun `a stamp op carries the new stamp and keeps the text`() {
    for (value in values("abc")) {
      val originalStamp = value.modState().stamp()
      val updated = value.applyOp(modStampOp(42, true))
      assertFalse(value.isSameAs(updated), value.kind)
      assertEquals(42L, updated.modState().stamp(), value.kind)
      assertEquals(value.modState().sequence() + 1, updated.modState().sequence(), value.kind)
      assertSame(value.text().chars(), updated.text().chars(), value.kind)
      assertEquals(originalStamp, value.modState().stamp(), value.kind)
    }
  }

  @Test
  fun `a clear of an untouched text keeps the instance`() {
    for (value in values("a\nb\nc")) {
      val clear = cleanLinesOp(0, Int.MAX_VALUE, IntArray(0))
      assertTrue(value.isSameAs(value.applyOp(clear)), value.kind)
    }
  }

  @Test
  fun `an insert after a line query keeps the line count right`() {
    for (value in values("a\nb")) {
      // The query builds the line data before the insert.
      value.text().lineCount()
      val patched = inserted(value, "xy")
      assertEquals("xya\nb", patched.text().string(), value.kind)
      assertEquals(2, patched.text().lineCount(), value.kind)
    }
  }

  @Test
  fun `a replace adds 2 to the sequence`() {
    for (value in values("abc")) {
      val stamp = modStampOp(value.modState().stamp() + 1, false)
      val patched = value.applyOp(deleteOp(1, 1)).applyOp(insertOp(1, "xy")).applyOp(stamp)
      assertEquals(value.modState().sequence() + 2, patched.modState().sequence(), value.kind)
    }
  }

  @Test
  fun `fresh texts share no line state`() {
    val edited = values("z").map { value ->
      inserted(value, "y")
    }
    for ((index, unrelated) in values("a\nb\nc\nd\ne").withIndex()) {
      assertFalse(unrelated.modState().isLineModified(3), unrelated.kind)
      assertTrue(edited[index].modState().isLineModified(0), edited[index].kind)
    }
  }

  /**
   * A snapshot or a branch, as the rules read it.
   */
  private interface Value {
    val kind: String

    fun applyOp(op: DocumentOp): Value

    fun text(): DocumentText

    fun modState(): DocumentModState

    fun isSameAs(other: Value): Boolean
  }

  private class SnapshotValue(private val snapshot: DocumentSnapshot) : Value {
    override val kind: String = "snapshot"

    override fun applyOp(op: DocumentOp): Value {
      return SnapshotValue(snapshot.applyOp(op))
    }

    override fun text(): DocumentText {
      return snapshot.text()
    }

    override fun modState(): DocumentModState {
      return snapshot.modState()
    }

    override fun isSameAs(other: Value): Boolean {
      return other is SnapshotValue && other.snapshot === snapshot
    }
  }

  private class BranchValue(private val branch: DocumentBranch) : Value {
    override val kind: String = "branch"

    override fun applyOp(op: DocumentOp): Value {
      return BranchValue(branch.applyOp(op))
    }

    override fun text(): DocumentText {
      return branch.text()
    }

    override fun modState(): DocumentModState {
      return branch.modState()
    }

    override fun isSameAs(other: Value): Boolean {
      return other is BranchValue && other.branch === branch
    }
  }

  private companion object {
    /**
     * A snapshot and a branch with the text [text].
     */
    fun values(text: String): List<Value> {
      val snapshot = SnapshotValue(DocumentSnapshotImpl(DocumentText.createText(text)))
      val branch = BranchValue(DocumentBranch.createBranch(text, Agent.createAgent("user")))
      return listOf(snapshot, branch)
    }

    /**
     * [value] with [fragment] inserted at the start, as a patch of the mutator does it.
     */
    fun inserted(value: Value, fragment: String): Value {
      val stamp = modStampOp(value.modState().stamp() + 1, false)
      return value.applyOp(insertOp(0, fragment)).applyOp(stamp)
    }
  }
}
