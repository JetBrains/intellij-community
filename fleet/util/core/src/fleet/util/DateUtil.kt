// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlinx.datetime.*
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char

private val TIME_FORMATTER = LocalTime.Format {
  hour(); char(':'); minute()
}

private val DATE_FORMATTER = LocalDate.Format {
  // TODO localized months (not available on multiplatform library)
  monthName(MonthNames.ENGLISH_ABBREVIATED)
  char(' ')
  dayOfMonth(Padding.NONE)
}

private val DATE_WITH_YEAR_FORMATTER = LocalDate.Format {
  date(DATE_FORMATTER)
  chars(", ")
  year()
}

private val FULL_FORMATTER = LocalDateTime.Format {
  time(TIME_FORMATTER)
  char(' ')
  date(DATE_WITH_YEAR_FORMATTER)
}

fun utcSeconds(timestamp: Long): LocalDateTime {
  return Instant.fromEpochSeconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
}

fun LocalDateTime.formatTimestamp(shorten: Boolean): String {
  val ldt = this
  if (shorten) {
    val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    if (ldt.year == currentDate.year && ldt.dayOfYear == currentDate.dayOfYear) {
      return ldt.time.format(TIME_FORMATTER)
    }
    val yesterday = currentDate.minus(DatePeriod(days = 1))
    if (ldt.year == yesterday.year && ldt.dayOfYear == yesterday.dayOfYear) {
      return "Yesterday"
    }
    if (ldt.year == currentDate.year) {
      return ldt.date.format(DATE_FORMATTER)
    }
    return ldt.date.format(DATE_WITH_YEAR_FORMATTER)
  }
  return ldt.format(FULL_FORMATTER)
}


fun prettyDate(time: Long): String {
  val ldt = utcSeconds(time / 1000)
  val now = Clock.System.now()

  val timeZone = TimeZone.currentSystemDefault()
  val dif = now - ldt.toInstant(timeZone)
  return when {
    dif.inWholeSeconds < 60 -> "Now"
    dif.inWholeMinutes < 60 -> "${dif.inWholeMinutes} min ago"
    dif.inWholeHours == 1L -> "1 hour ago"
    dif.inWholeHours < 10 -> "${dif.inWholeHours} hours ago"
    dif.inWholeDays == 0L && ldt.dayOfYear == now.toLocalDateTime(timeZone).dayOfYear -> "Today"
    dif.inWholeDays < 365 -> {
      val month = ldt.month.name.lowercase().replaceFirstChar { it.uppercase() }.substring(0, 3)
      val day = ldt.dayOfMonth.toString().padStart(2, '0')
      "${month} ${day}"
    }
    else -> {
      val years = now.toLocalDateTime(timeZone).year - ldt.year
      "$years year${if (years > 1) "s" else ""} ago"
    }
  }
}
