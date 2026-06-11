// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.Document
import org.jetbrains.annotations.ApiStatus

/**
 * This interface is responsible for the document write path.
 *
 * All document content changes go through this interface.
 * It keeps mutation-specific work outside [com.intellij.openapi.editor.impl.DocumentImpl]:
 * checking whether a change is allowed, producing document events, updating text snapshots,
 * modification metadata, and line flags.
 *
 * #### Threading
 * **Important**. There is no intention to make the document totally thread-safe.
 * The goal is to make its behavior more predictable and consistent.
 *
 * All methods are "atomic" meaning they capture the current snapshot, transform it, and publish it via CAS.
 * Mental model for concurrent updates: **there is always a winning thread installing a consistent snapshot;
 * other losers, depending on a particular operation, 1) may affect the final snapshot, 2) be ignored, 3) or get an exception**.
 *
 * There is no CRDT or similar under the hood. Concurrent text mutations are not merged but just override each other.
 * Example:
 * ```
 * initial text = "abc"
 * t1: insertString(3, 'd') // target 'abcd'
 * t2: deleteString(2, 3) // target 'ab'
 * possible result:
 * 1) text = 'abcd' // t2 ignored
 * 2) text = 'ab'   // t1 ignored
 * 3) text = 'abd'  // t1 then t2
 * 4) text = 'ab'   // t1 gets out of bounds exception
 * 5) text = 'abcd' // t2 gets nested modification exception
 * 6) text = 'ab'   // t1 gets nested modification exception
 * ```
 * cases (5) and (6) are T_O_D_O to be fixed
 */
@ApiStatus.Internal
interface DocumentMutator {

  /**
   * Atomically sets modStamp and increments modSequence with AtomicInt semantics.
   *
   * Safe to perform concurrently.
   * @param incrementModSequence whether the modSequence should be incremented
   * @see DocumentSnapshot.withModStamp
   */
  fun setModStamp(newModStamp: Long, incrementModSequence: Boolean)

  /**
   * Atomically cleans modification line flags.
   *
   * It is unsafe to perform concurrently with text mutations because line numbers may become outdated causing an exception
   *
   * @see DocumentSnapshot.withClearedLineFlags
   */
  fun clearLineFlags(startLine: Int, endLine: Int, exceptLines: IntArray)

  /**
   * Atomically changes document snapshot with semantics from `Threading` section.
   *
   * @param hostDocument communication with the outside world: document listeners, EPs, CommandProcessor, etc.
   *
   * @see DocumentEx.insertString
   */
  fun insertString(hostDocument: Document, insertOffset: Int, insertString: CharSequence)

  /**
   * Atomically changes document snapshot with semantics from `Threading` section.
   *
   * @param hostDocument communication with the outside world: document listeners, EPs, CommandProcessor, etc.
   *
   * @see DocumentEx.deleteString
   */
  fun deleteString(hostDocument: Document, startOffset: Int, endOffset: Int)

  /**
   * Atomically changes document snapshot with semantics from `Threading` section.
   *
   * @param hostDocument communication with the outside world: document listeners, EPs, CommandProcessor, etc.
   *
   * @see DocumentEx.replaceText
   */
  fun replaceText(hostDocument: Document, newWholeText: CharSequence, newModStamp: Long)

  /**
   * Atomically changes document snapshot with semantics from `Threading` section.
   *
   * @param hostDocument communication with the outside world: document listeners, EPs, CommandProcessor, etc.
   *
   * @see DocumentEx.setText
   */
  fun setText(hostDocument: Document, newWholeText: CharSequence)

  /**
   * Atomically changes document snapshot with semantics from `Threading` section.
   *
   * @param hostDocument communication with the outside world: document listeners, EPs, CommandProcessor, etc.
   *
   * @see DocumentEx.moveText
   */
  fun moveText(hostDocument: Document, srcStartOffset: Int, srcEndOffset: Int, dstOffset: Int)

  /**
   * Atomically changes document snapshot with semantics from `Threading` section.
   * Eventually, all text change methods delegate to this one
   *
   * @param hostDocument communication with the outside world: document listeners, EPs, CommandProcessor, etc.
   */
  fun replaceString(
    hostDocument: Document,
    startOffset: Int,
    endOffset: Int,
    moveOffset: Int,
    replaceString: CharSequence,
    newModStamp: Long,
    wholeTextReplaced: Boolean,
  )

  /**
   * Does not change any content of the document.
   * From the design side it would be more correct to place this method in [DocumentEventDispatcher],
   * but due to some implementation details, it is convenient to keep it here.
   */
  fun setBulkModeStatus(hostDocument: Document, status: Boolean)
}
