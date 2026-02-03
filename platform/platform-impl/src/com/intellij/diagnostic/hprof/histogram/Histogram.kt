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
package com.intellij.diagnostic.hprof.histogram

import com.intellij.diagnostic.hprof.analysis.AnalysisConfig
import com.intellij.diagnostic.hprof.classstore.ClassStore
import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toPaddedShortStringAsCount
import com.intellij.diagnostic.hprof.util.HeapReportUtils.toPaddedShortStringAsSize
import com.intellij.diagnostic.hprof.util.TruncatingPrintBuffer
import com.intellij.diagnostic.hprof.visitors.HistogramVisitor
import kotlin.math.min

class Histogram(val entries: List<HistogramEntry>, val instanceCount: Long) {

  private fun getTotals(): Pair<Long, Long> {
    var totalInstances = 0L
    var totalBytes = 0L
    entries.forEach {
      totalBytes += it.totalBytes
      totalInstances += it.totalInstances
    }
    return Pair(totalInstances, totalBytes)
  }

  val bytesCount: Long = getTotals().second

  fun prepareReport(name: String, topClassCount: Int): String {
    val result = StringBuilder()
    result.appendLine("Histogram. Top $topClassCount by instance count:")
    val appendToResult = { s: String -> result.appendLine(s); Unit }
    var counter = 1

    TruncatingPrintBuffer(topClassCount, 0, appendToResult).use { buffer ->
      entries.forEach { entry ->
        buffer.println(formatEntryLine(counter, entry))
        counter++
      }
    }
    result.appendLine(getSummaryLine(this, name))
    result.appendLine()
    result.appendLine("Top 10 by bytes count:")
    val entriesByBytes = entries.sortedByDescending { it.totalBytes }
    for (i in 0 until 10) {
      val entry = entriesByBytes[i]
      result.appendLine(formatEntryLine(i + 1, entry))
    }
    return result.toString()
  }

  companion object {
    fun create(parser: HProfEventBasedParser, classStore: ClassStore): Histogram {
      val histogramVisitor = HistogramVisitor(classStore)
      parser.accept(histogramVisitor, "histogram")
      return histogramVisitor.createHistogram()
    }

    fun prepareMergedHistogramReport(mainHistogram: Histogram, mainHistogramName: String,
                                     secondaryHistogram: Histogram, secondaryHistogramName: String,
                                     options: AnalysisConfig.HistogramOptions): String {
      val result = StringBuilder()
      val appendToResult = { s: String -> result.appendLine(s); Unit }

      val mapClassNameToEntrySecondary = HashMap<String, HistogramEntry>()
      secondaryHistogram.entries.forEach {
        mapClassNameToEntrySecondary[it.classDefinition.name] = it
      }

      val summary =
        "${getSummaryLine(mainHistogram, mainHistogramName)}\n${getSummaryLine(secondaryHistogram, secondaryHistogramName)}"

      if (options.includeByCount) {
        result.appendLine("Histogram. Top ${options.classByCountLimit} by instance count [All-objects] [Only-strong-ref]:")
        var counter = 1

        TruncatingPrintBuffer(options.classByCountLimit, 0, appendToResult).use { buffer ->
          mainHistogram.entries.forEach { entry ->
            val entry2 = mapClassNameToEntrySecondary[entry.classDefinition.name]
            buffer.println(formatEntryLineMerged(counter, entry, entry2))
            counter++
          }
        }
        result.appendLine(summary)
      }

      if (options.includeBySize && options.includeByCount) {
        result.appendLine()
      }

      if (options.includeBySize) {
        val classCountInByBytesSection = min(mainHistogram.entries.size, options.classBySizeLimit)
        result.appendLine("Top $classCountInByBytesSection by size:")
        val entriesByBytes = mainHistogram.entries.sortedByDescending { it.totalBytes }
        for (i in 0 until classCountInByBytesSection) {
          val entry = entriesByBytes[i]
          val entry2 = mapClassNameToEntrySecondary[entry.classDefinition.name]
          result.appendLine(formatEntryLineMerged(i + 1, entry, entry2))
        }
        if (!options.includeByCount) {
          result.appendLine(summary)
        }
      }
      return result.toString()
    }

    private fun getSummaryLine(histogram: Histogram,
                               histogramName: String): String {
      val (totalInstances, totalBytes) = histogram.getTotals()
      return String.format("Total - %10s: %s %s %d classes (Total instances: %d)",
                           histogramName,
                           toPaddedShortStringAsCount(totalInstances),
                           toPaddedShortStringAsSize(totalBytes),
                           histogram.entries.size,
                           histogram.instanceCount)
    }

    private fun formatEntryLineMerged(counter: Int, entry: HistogramEntry, entry2: HistogramEntry?): String {
      return String.format("%5d: [%s/%s] [%s/%s] %s",
                           counter,
                           toPaddedShortStringAsCount(entry.totalInstances),
                           toPaddedShortStringAsSize(entry.totalBytes),
                           toPaddedShortStringAsCount(entry2?.totalInstances ?: 0),
                           toPaddedShortStringAsSize(entry2?.totalBytes ?: 0),
                           entry.classDefinition.prettyName)
    }

    private fun formatEntryLine(counter: Int, entry: HistogramEntry): String {
      return String.format("%5d: [%s/%s] %s",
                           counter,
                           toPaddedShortStringAsCount(entry.totalInstances),
                           toPaddedShortStringAsSize(entry.totalBytes),
                           entry.classDefinition.prettyName)
    }

  }
}
