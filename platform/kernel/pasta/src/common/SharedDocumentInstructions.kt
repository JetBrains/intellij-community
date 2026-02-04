// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pasta.common

import andel.operation.Operation
import andel.operation.Sticky
import fleet.util.UID
import kotlinx.serialization.Serializable


@Serializable
data class SharedChangeDocument(
  val documentId: UID,
  val operationId: UID,
  val operation: Operation,
  val docToDb: Boolean,
  val seed: Long, 
)

@Serializable
internal data class SharedCreateSharedAnchor(
  val offset: Long,
  val sticky: Sticky,
  val anchorUID: UID,
  val documentUID: UID,
  val seed: Long, 
)

@Serializable
internal data class SharedRetractSharedAnchor(
  val anchorUID: UID,
  val documentUID: UID,
  val seed: Long, 
)

@Serializable
internal data class SharedRetractSharedRangeMarker(
  val rangeMarkerUID: UID,
  val documentUID: UID,
  val seed: Long, 
)

@Serializable
internal data class SharedCreateSharedRangeMarker(
  val from: Long,
  val to: Long,
  val closedLeft: Boolean,
  val closedRight: Boolean,
  val rangeMarkerUID: UID,
  val documentUID: UID,
  val seed: Long, 
)
