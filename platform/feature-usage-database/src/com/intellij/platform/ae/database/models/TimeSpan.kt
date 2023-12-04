package com.intellij.platform.ae.database.models

import com.intellij.platform.ae.database.activities.DatabaseBackedTimeSpanUserActivity
import java.time.Instant

data class TimeSpan(
  val activity: DatabaseBackedTimeSpanUserActivity,
  val id: String,
  val start: Instant,
  val end: Instant
)
