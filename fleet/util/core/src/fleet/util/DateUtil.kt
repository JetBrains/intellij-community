// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import java.time.*
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d")
private val DATE_WITH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val FULL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm MMM d, yyyy")

fun utcSeconds(timestamp: Long): LocalDateTime {
  return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp * 1000), ZoneOffset.UTC)
    .atZoneSameInstant(ZoneId.systemDefault())
    .toLocalDateTime()
}

fun LocalDateTime.formatTimestamp(shorten: Boolean): String {
  val ldt = this
  if (shorten) {
    val now = OffsetDateTime.now()
    if (ldt.year == now.year && ldt.dayOfYear == now.dayOfYear) {
      return TIME_FORMATTER.format(ldt)
    }
    val yesterday = now.minusDays(1)
    if (ldt.year == yesterday.year && ldt.dayOfYear == yesterday.dayOfYear) {
      return "Yesterday"
    }
    if (ldt.year == now.year) {
      return DATE_FORMATTER.format(ldt)
    }
    return DATE_WITH_YEAR_FORMATTER.format(ldt)
  }
  return FULL_FORMATTER.format(ldt)
}