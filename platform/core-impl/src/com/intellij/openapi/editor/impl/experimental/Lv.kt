// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

/**
 * A local version: the index of one single-character operation (a unit) in the event
 * graph, in the graph's own append order. The reference implementation calls this LV.
 *
 * An LV is local to one graph value and to the values that grow from it. An append and a merge into
 * the value keep every LV. The units that a merge brings in get new LVs, so the LVs of the other
 * graph never travel without a remap. The value -1 marks "no unit" where a sentinel is needed.
 *
 * An LV is not a seq. A seq is the durable half of an event id and travels between graphs
 * unchanged. That is why [AgentIndex.lvOfSeq] translates one into the other.
 *
 * An LV indexes only the units that one graph stores, so it fits an [Int]. A seq indexes every unit
 * that its agent authored. That includes the units that a trim dropped, so a seq is a [Long].
 */
internal typealias LV = Int

/**
 * No unit: the document start for a left origin, the document end for a right parent.
 */
internal const val NO_UNIT: LV = -1

/**
 * A version, which the paper calls the frontier: the LVs that have no child. The array is
 * sorted ascending, holds no duplicate, and is transitively reduced, so no entry is an
 * ancestor of another. [LvVersion] is the same thing as a real type. It enforces the order
 * and the uniqueness, but not the reduction: that needs the graph, and
 * [com.intellij.openapi.editor.ex.experimental.EventGraph.append] states it as a precondition.
 *
 * A frontier names a document state. Contrast [LvRanges], which names every unit of a region.
 */
internal typealias Frontier = IntArray
