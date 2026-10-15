// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import org.jetbrains.annotations.ApiStatus

/**
 * A value together with a hint whether it is the last one in a sequence.
 *
 * [isLast] is an optional optimization: a consumer may finish as soon as it sees `isLast == true` instead of asking for
 * one more item only to discover the sequence has ended. Consumers that do not care may simply ignore it.
 */
@ApiStatus.Experimental
interface SequenceItem<out T> {
  val value: T
  val isLast: Boolean
}

/**
 * Creates a [SequenceItem] holding [value] and flagged with [isLast].
 */
@ApiStatus.Experimental
fun <T> SequenceItem(value: T, isLast: Boolean): SequenceItem<T> = SequenceItemImpl(value, isLast)

private data class SequenceItemImpl<out T>(override val value: T, override val isLast: Boolean) : SequenceItem<T>

/**
 * A part of an incrementally computed list: a [SequenceItem] whose value is one chunk of a larger list.
 */
typealias ListPart<T> = SequenceItem<List<T>>
