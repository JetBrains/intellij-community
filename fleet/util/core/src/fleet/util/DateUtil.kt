// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.time.toKotlinDuration

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


fun prettyDate(time: Long): String {
  val ldt = utcSeconds(time / 1000)
  val now = OffsetDateTime.now()

  val dif = Duration.between(ldt, now).toKotlinDuration()
  return when {
    dif.inWholeSeconds < 60 -> "Now"
    dif.inWholeMinutes < 60 -> "${dif.inWholeMinutes} min ago"
    dif.inWholeHours == 1L -> "1 hour ago"
    dif.inWholeHours < 10 -> "${dif.inWholeHours} hours ago"
    dif.inWholeDays == 0L && ldt.dayOfYear == now.dayOfYear -> "Today"
    dif.inWholeDays < 365 -> {
      val month = ldt.month.name.lowercase().replaceFirstChar { it.uppercase() }.substring(0, 3)
      val day = ldt.dayOfMonth.toString().padStart(2, '0')
      "${month} ${day}"
    }
    else -> {
      val years = now.year - ldt.year
      "$years year${if (years > 1) "s" else ""} ago"
    }
  }
}
