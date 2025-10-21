// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.diff.util

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.containers.Interner
import com.intellij.util.diff.Diff
import com.intellij.util.diff.FilesTooBigForDiffException
import com.intellij.util.diff.UniqueLCS
import junit.framework.TestCase
import java.util.*

class DiffPerformanceTest : TestCase() {
  companion object {
    private var needWarmUp = true
  }

  private val interner: Interner<String> = Interner.createStringInterner()

  val data = generateData(2000000)
  private val arr_200000 = data.take(200000).toTypedArray()
  private val arr_50000 = data.take(50000).toTypedArray()
  private val arr_20000 = data.take(20000).toTypedArray()
  private val arr_5000 = data.take(5000).toTypedArray()
  private val arr_2000 = arr_20000.take(2000).toTypedArray()
  private val arr_1000 = arr_20000.take(1000).toTypedArray()
  private val arr_100 = arr_20000.take(100).toTypedArray()

  private val shuffled_2000 = shuffle(arr_2000)
  private val shuffled_1000 = shuffled_2000.take(1000).toTypedArray()
  private val shuffled_100 = shuffled_2000.take(100).toTypedArray()

  private val altered_200000 = alter(arr_200000)
  private val altered_50000 = alter(arr_50000)
  private val altered_20000 = alter(arr_20000)
  private val altered_2000 = alter(arr_2000)
  private val altered_1000 = alter(arr_1000)
  private val altered_100 = alter(arr_100)

  private val heavy_altered_200000 = heavy_alter(arr_200000)
  private val heavy_altered_50000 = heavy_alter(arr_50000)
  private val heavy_altered_20000 = heavy_alter(arr_20000)
  private val heavy_altered_2000 = heavy_alter(arr_2000)
  private val heavy_altered_1000 = heavy_alter(arr_1000)
  private val heavy_altered_100 = heavy_alter(arr_100)

  private val reversed_200000 = arr_200000.reversedArray()
  private val reversed_50000 = arr_50000.reversedArray()
  private val reversed_5000 = arr_5000.reversedArray()
  private val reversed_2000 = arr_2000.reversedArray()
  private val reversed_1000 = arr_1000.reversedArray()
  private val reversed_100 = arr_100.reversedArray()

  private val very_heavy_altered_200000 = very_heavy_alter(arr_200000)

  override fun setUp() {
    if (needWarmUp) {
      needWarmUp = false
      warmUp()
    }
    super.setUp()
  }

  private fun warmUp() {
    for (i in 0..40) {
      Diff.buildChanges(arr_20000, heavy_altered_20000)
    }
  }

  fun `test altered 200000`() {
    testCpu(3) {
      Diff.buildChanges(arr_200000, altered_200000)
    }
  }

  fun `test heavy altered 200000`() {
    testCpu(1) {
      Diff.buildChanges(arr_200000, heavy_altered_200000)
    }
  }

  fun `test reversed 50000 failure`() {
    testCpu(1) {
      try {
        Diff.buildChanges(arr_50000, reversed_50000)
      }
      catch (e: FilesTooBigForDiffException) {
        return@testCpu
      }
      fail("FilesTooBigForDiffException expected")
    }
  }

  fun `test reversed 5000`() {
    testCpu(1) {
      Diff.buildChanges(arr_5000, reversed_5000)
    }
  }

  fun `test altered 50000`() {
    testCpu(20) {
      Diff.buildChanges(arr_50000, altered_50000)
    }
  }

  fun `test heavy altered 50000`() {
    testCpu(3) {
      Diff.buildChanges(arr_50000, heavy_altered_50000)
    }
  }

  fun `test altered 20000`() {
    testCpu(20) {
      Diff.buildChanges(arr_20000, altered_20000)
    }
  }

  fun `test heavy altered 20000`() {
    testCpu(15) {
      Diff.buildChanges(arr_20000, heavy_altered_20000)
    }
  }

  fun `test altered 2000`() {
    testCpu(400) {
      Diff.buildChanges(arr_2000, altered_2000)
    }
  }

  fun `test heavy altered 2000`() {
    testCpu(400) {
      Diff.buildChanges(arr_2000, heavy_altered_2000)
    }
  }

  fun `test shuffled 2000`() {
    testCpu(1) {
      Diff.buildChanges(arr_2000, shuffled_2000)
    }
  }

  fun `test reversed 2000`() {
    testCpu(1) {
      Diff.buildChanges(arr_2000, reversed_2000)
    }
  }

  fun `test altered 1000`() {
    testCpu(700) {
      Diff.buildChanges(arr_1000, altered_1000)
    }
  }

  fun `test heavy altered 1000`() {
    testCpu(700) {
      Diff.buildChanges(arr_1000, heavy_altered_1000)
    }
  }

  fun `test shuffled 1000`() {
    testCpu(10) {
      Diff.buildChanges(arr_1000, shuffled_1000)
    }
  }

  fun `test reversed 1000`() {
    testCpu(10) {
      Diff.buildChanges(arr_1000, reversed_1000)
    }
  }

  fun `test altered 100`() {
    testCpu(10000) {
      Diff.buildChanges(arr_100, altered_100)
    }
  }

  fun `test heavy altered 100`() {
    testCpu(10000) {
      Diff.buildChanges(arr_100, heavy_altered_100)
    }
  }

  fun `test shuffled 100`() {
    testCpu(2000) {
      Diff.buildChanges(arr_100, shuffled_100)
    }
  }

  fun `test reversed 100`() {
    testCpu(1000) {
      Diff.buildChanges(arr_100, reversed_100)
    }
  }


  fun `test altered 200000 unique`() {
    testCpu(30) {
      buildChangesUnique(arr_200000, altered_200000)
    }
  }

  fun `test heavy altered unique 200000`() {
    testCpu(10) {
      buildChangesUnique(arr_200000, heavy_altered_200000)
    }
  }

  fun `test very heavy altered unique 200000`() {
    testCpu(10) {
      buildChangesUnique(arr_200000, very_heavy_altered_200000)
    }
  }

  fun `test reversed 50000 unique`() {
    testCpu(10) {
      buildChangesUnique(arr_50000, reversed_50000)
    }
  }

  fun `test reversed 200000 unique`() {
    testCpu(3) {
      buildChangesUnique(arr_200000, reversed_200000)
    }
  }

  fun `test altered 50000 unique`() {
    testCpu(200) {
      buildChangesUnique(arr_50000, altered_50000)
    }
  }

  fun `test heavy altered unique 50000`() {
    testCpu(30) {
      buildChangesUnique(arr_50000, heavy_altered_50000)
    }
  }

  fun `test altered 20000 unique`() {
    testCpu(200) {
      buildChangesUnique(arr_20000, altered_20000)
    }
  }

  fun `test heavy altered unique 20000`() {
    testCpu(150) {
      buildChangesUnique(arr_20000, heavy_altered_20000)
    }
  }

  private fun buildChangesUnique(array1: Array<String>, array2: Array<String>) {
    val enumerator = Enumerator<String>(array1.size + array2.size)
    val intArray1 = enumerator.enumerate(array1)
    val intArray2 = enumerator.enumerate(array2)
    UniqueLCS(intArray1, intArray2).execute()
  }

  private fun generateData(size: Int): List<String> {
    return (1..size).map { interner.intern("${it % 200}") }
  }

  private fun alter(arr: Array<String>): Array<String> {
    val altered = arr.copyOf()
    altered[0] = "===" // avoid "common prefix/suffix" optimisation
    altered[altered.size / 2] = "???"
    altered[altered.lastIndex] = "==="
    return altered
  }

  private fun heavy_alter(arr: Array<String>): Array<String> {
    val altered = arr.copyOf()
    for (i in 1..altered.lastIndex step 20) {
      altered[i] = interner.intern("${i % 200}")
    }
    altered[0] = "===" // avoid "common prefix/suffix" optimisation
    altered[altered.lastIndex] = "==="
    return altered
  }

  private fun very_heavy_alter(arr: Array<String>): Array<String> {
    val altered = arr.copyOf()
    for (i in 1..altered.lastIndex step 5) {
      altered[i] = interner.intern("${i % 20}")
    }
    altered[0] = "===" // avoid "common prefix/suffix" optimisation
    altered[altered.lastIndex] = "==="
    return altered
  }

  private fun shuffle(arr: Array<String>): Array<String> {
    val list = arr.toMutableList()
    Collections.shuffle(list, Random(0))
    return list.toTypedArray()
  }


  private inline fun testCpu(iterations: Int, crossinline test: () -> Unit) {
    Benchmark.newBenchmark(PlatformTestUtil.getTestName(name, true)) {
      for (i in 0..iterations) {
        test()
      }
    }.start()
  }
}
