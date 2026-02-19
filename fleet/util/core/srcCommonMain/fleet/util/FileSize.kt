// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlin.math.log10
import kotlin.math.pow

fun formatFileSize(fileSize: Long): String {
  if (fileSize == 0L) return "0 B"
  val rank = ((log10(fileSize.toDouble()) + 0.0000021714778384307465) / 3).toInt()
  val value = fileSize / 1000.0.pow(rank.toDouble())
  val units = arrayOf("B", "kB", "MB", "GB", "TB", "PB", "EB")
  return value.formatMaxTwoDecimals() + units[rank]
}

private fun Double.formatMaxTwoDecimals(): String {
  val rounded = (this * 100).toLong() / 100.0
  return rounded.toString().removeSuffix(".0")
}