package com.intellij.platform.ae.database.utils

object BooleanUtils {
  fun formatForDatabase(v: Boolean): Int {
    return if (v) { 1 } else { 0 }
  }
}