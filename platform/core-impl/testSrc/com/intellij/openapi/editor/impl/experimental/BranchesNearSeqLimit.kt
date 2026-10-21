// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch

/**
 * The first seq that an [Int] cannot hold: `2^31`.
 */
internal const val INT_SEQ_LIMIT: Long = 1L shl 31

/**
 * The next seq of [NEAR_LIMIT_BRANCH], 8 units below [INT_SEQ_LIMIT].
 */
internal const val NEAR_LIMIT_SEQ: Long = INT_SEQ_LIMIT - 8

/**
 * The text "abc" under the agent "main", whose next seq is [NEAR_LIMIT_SEQ]. It holds no history. It
 * is slow to build, so the tests share one.
 */
internal val NEAR_LIMIT_BRANCH: DocumentBranch by lazy {
  branchWithNextSeq("abc", Agent.createAgent("main"), NEAR_LIMIT_SEQ)
}

/**
 * A branch with the text [text] under [agent], whose next seq is [nextSeq]. Each round inserts a long
 * fragment at the end and deletes it again, with a trim after each op. So the branch holds no
 * history. [nextSeq] must be even, because a round takes two units for each character.
 */
internal fun branchWithNextSeq(text: String, agent: Agent, nextSeq: Long): DocumentBranch {
  checkEvenSeq(nextSeq)
  val chunk = "x".repeat(CHUNK_LENGTH)
  var branch = DocumentBranch.createBranch(text, agent)
  var remaining = nextSeq
  while (remaining > 0) {
    val length = minOf(CHUNK_LENGTH.toLong(), remaining / 2).toInt()
    val inserted = branch.applyOp(DocumentOp.insertOp(text.length, chunk.subSequence(0, length)))
    branch = inserted.trimmedTo(inserted.version())
    val deleted = branch.applyOp(DocumentOp.deleteOp(text.length, length))
    branch = deleted.trimmedTo(deleted.version())
    remaining -= 2L * length
  }
  return branch
}

private fun checkEvenSeq(nextSeq: Long) {
  require(nextSeq >= 0 && nextSeq % 2 == 0L) {
    "The next seq $nextSeq is negative or odd"
  }
}

/**
 * The length of the fragment of one round.
 */
private const val CHUNK_LENGTH = 1 shl 24
