// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

data class TransportStats(
  val sentBytes: Long = 0,
  val sentCompressedBytes: Long = 0,
  val receivedBytes: Long = 0,
  val receivedCompressedBytes: Long = 0,
)