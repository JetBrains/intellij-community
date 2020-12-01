// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dto

data class JsonPercentages(val percentages: Double) {
  fun presentablePercentages(): String =
    if (percentages < 0.01) {
      "< 1%"
    }
    else {
      "${String.format("%.1f", percentages * 100)}%"
    }
}