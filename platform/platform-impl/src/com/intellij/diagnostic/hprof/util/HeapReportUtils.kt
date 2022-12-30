/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.util

import org.jetbrains.annotations.NonNls
import java.util.Locale

object HeapReportUtils {
  private val SI_PREFIXES = charArrayOf('K', 'M', 'G', 'T', 'P', 'E', 'Z', 'Y') // Kilo, Mega, Giga, Peta, etc.
  const val STRING_PADDING_FOR_COUNT = 5
  const val STRING_PADDING_FOR_SIZE = STRING_PADDING_FOR_COUNT + 1
  private const val SECTION_HEADER_SIZE = 50

  fun toShortStringAsCount(count: Long): String {
    return toShortString(count)
  }

  fun toPaddedShortStringAsCount(count: Long): String {
    return toShortString(count).padStart(STRING_PADDING_FOR_COUNT)
  }

  fun toShortStringAsSize(size: Long): String {
    return toShortString(size) + "B"
  }

  fun toPaddedShortStringAsSize(size: Long): String {
    return toShortStringAsSize(size).padStart(STRING_PADDING_FOR_SIZE)
  }

  fun sectionHeader(@NonNls name: String): String {
    val uppercaseName = name.toUpperCase(Locale.US)
    return if (uppercaseName.length >= SECTION_HEADER_SIZE - 2) {
      uppercaseName
    }
    else {
      StringBuilder().apply {
        append("=".repeat((SECTION_HEADER_SIZE - uppercaseName.length) / 2))
        append(" $uppercaseName ")
        append("=".repeat((SECTION_HEADER_SIZE - uppercaseName.length - 1) / 2))
      }.toString()
    }
  }

  /**
   * @return String representing {@code num} with 3 most significant digits using SI prefixes.
   * <p>
   * Examples: 12 returns "12", 12345 returns "12.3K", 123456789 returns "123M"
   */
  private fun toShortString(num: Long): String {
    var shiftCount = 0
    var localNum = num
    while (localNum >= 1000) {
      shiftCount++
      localNum /= 10
    }
    if (shiftCount > 0) {
      val siPrefixIndex = (shiftCount - 1) / 3
      assert(siPrefixIndex < SI_PREFIXES.size)

      val suffix = SI_PREFIXES[siPrefixIndex]
      val value = when (shiftCount % 3) {
        0 -> localNum.toString()
        1 -> (localNum.toDouble() / 100).toString()
        2 -> (localNum.toDouble() / 10).toString()
        else -> {
          assert(false); "????"
        }
      }
      return value + suffix
    }
    return localNum.toString()
  }
}