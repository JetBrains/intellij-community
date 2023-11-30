// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FusHistogramBuilder(private val bucketValues: LongArray) {
  val buckets: IntArray = IntArray(bucketValues.size)

  fun addValue(value: Long) {
    var bucketIndex = bucketValues.indexOfFirst { it > value }
    if (bucketIndex != -1) {
      // We found a bucket that needs to be incremented
      if (bucketIndex > 0) {
        // The value falls into the previous bucket
        bucketIndex--
      }
    } else {
      // The value is greater than all buckets, it falls into the last one
      bucketIndex = bucketValues.size - 1
    }
    buckets[bucketIndex]++
  }

  fun build(): FusHistogram {
    return FusHistogram(buckets)
  }
}

@Internal
class FusHistogram(
  val buckets: IntArray
)