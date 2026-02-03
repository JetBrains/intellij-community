// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes

import org.jetbrains.annotations.NotNull
import kotlin.math.roundToInt

internal class FileExtensionCounter {
  private val fileExtensionCounter: MutableMap<@NotNull String, Int> = mutableMapOf()
  private var totalCount: Int = 0

  fun recordOriginalFileType(fileExtension: String) {
    val currentFileExtensionCount = fileExtensionCounter.getOrPut(fileExtension) { 0 }
    fileExtensionCounter[fileExtension] = currentFileExtensionCount + 1
    totalCount++
  }

  fun getFileExtensionsList(): List<String> = fileExtensionCounter.keys.toList()

  fun getFileExtensionCount(extension: String): Int = fileExtensionCounter.getOrDefault(extension, 0)

  fun getFileExtensionUsagePercentage(extension: String): Int = ((getFileExtensionCount(extension).toDouble() / totalCount) * 100).roundToInt()
}