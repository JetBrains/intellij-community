/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.intellij.diagnostic.hprof.analysis

import com.intellij.diagnostic.hprof.histogram.Histogram
import com.intellij.diagnostic.hprof.histogram.HistogramEntry

class ClassNomination(private val histogram: Histogram,
                      private val classLimitPerCategory: Int) {
  fun nominateClasses(): List<HistogramEntry> {
    val resultClasses = HashSet<HistogramEntry>()

    val entries = histogram.entries
    val histogramByInstances = entries.sortedByDescending { it.totalInstances }
    val interestingClasses = histogramByInstances.filter { isInterestingClass(it.classDefinition.name) }

    histogramByInstances.take(classLimitPerCategory).forEach { resultClasses.add(it) }
    interestingClasses.take(classLimitPerCategory).forEach { resultClasses.add(it) }

    val histogramByBytes = entries.sortedByDescending { it.totalBytes }
    val interestingClassesByBytes = interestingClasses.sortedByDescending { it.totalBytes }

    histogramByBytes.take(classLimitPerCategory).forEach { resultClasses.add(it) }
    interestingClassesByBytes.take(classLimitPerCategory).forEach { resultClasses.add(it) }

    // Always include DirectByteBuffers
    entries.find { it.classDefinition.name == "java.nio.DirectByteBuffer" }?.let { resultClasses.add(it) }

    return ArrayList(resultClasses).sortedByDescending { it.totalBytes }
  }

  private fun isInterestingClass(name: String): Boolean {
    var localName = name

    // Flatten the type of multi-dimensional array
    val lastBracketIndex = localName.lastIndexOf('[')
    if (lastBracketIndex >= 1) {
      // Keep one bracket
      localName = localName.substring(lastBracketIndex)
    }

    // Filter out arrays of primitives
    if (localName.length == 2 && localName[0] == '[') {
      return false
    }

    // Get inner type of object arrays
    if (localName.startsWith("[L")) {
      assert(localName.last() == ';')
      localName = localName.substring(2, localName.length - 1)
    }

    return !localName.startsWith("java.") &&
           !localName.startsWith("com.google.common.") &&
           !localName.startsWith("kotlin.") &&
           !localName.startsWith("com.intellij.util.")
  }
}