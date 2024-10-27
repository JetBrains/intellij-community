// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import it.unimi.dsi.fastutil.ints.IntList

/**
 * Represents [Entity] id.
 * */
typealias EID = Int
/**
 * Fourth element of the [Datom].
 * It is an opaque [Long] value, representing some information about the transaction, when this [Datom] was added to the [DB].
 * */
typealias TX = Long
/**
 * Every [EID] has a partition, encoded in it.
 * Different partitions may be separated and combined efficiently to form different [DB]s.
 * There is one dedicated partition for schema, it's value is 0
 * */
typealias Part = Int

/**
 * [DB] may have no more than 5 partitions, including schema.
 * */
internal const val MAX_PART = 4

/** [0 1 2 3 4] */
val AllParts: IntList = IntList.of(*(0..MAX_PART).toList().toIntArray())

const val SchemaPart: Part = 0 // schema

/**
 * Extrancts the partition part from the [EID]
 * */
fun partition(e: EID): Part = e.ushr(28)

/**
 * Sets the partition part of the [EID]
 * */
fun withPart(e: EID, part: Part): EID = e.or(part.shl(28))
