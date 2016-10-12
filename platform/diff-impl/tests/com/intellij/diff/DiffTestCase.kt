/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff

import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.containers.HashMap
import com.intellij.util.text.CharSequenceSubSequence
import junit.framework.ComparisonFailure
import java.util.*
import java.util.concurrent.atomic.AtomicLong

abstract class DiffTestCase : UsefulTestCase() {
  companion object {
    private val DEFAULT_CHAR_COUNT = 12
    private val DEFAULT_CHAR_TABLE: Map<Int, Char> = {
      val map = HashMap<Int, Char>()
      listOf('\n', '\n', '\t', ' ', ' ', '.', '<', '!').forEachIndexed { i, c -> map.put(i, c) }
      map
    }()
  }

  val RNG: Random = Random()
  private var gotSeedException = false

  val INDICATOR: ProgressIndicator = DumbProgressIndicator.INSTANCE
  val MANAGER: ComparisonManagerImpl = ComparisonManagerImpl()


  override fun setUp() {
    super.setUp()
    DiffIterableUtil.setVerifyEnabled(true)
  }

  override fun tearDown() {
    DiffIterableUtil.setVerifyEnabled(Registry.`is`("diff.verify.iterable"))
    super.tearDown()
  }

  //
  // Assertions
  //

  fun assertTrue(actual: Boolean, message: String = "") {
    assertTrue(message, actual)
  }

  fun assertEquals(expected: Any?, actual: Any?, message: String = "") {
    assertEquals(message, expected, actual)
  }

  fun assertEquals(expected: CharSequence?, actual: CharSequence?, message: String = "") {
    if (!StringUtil.equals(expected, actual)) throw ComparisonFailure(message, expected?.toString(), actual?.toString())
  }

  fun assertEqualsCharSequences(chunk1: CharSequence, chunk2: CharSequence, ignoreSpaces: Boolean, skipLastNewline: Boolean) {
    if (ignoreSpaces) {
      assertTrue(StringUtil.equalsIgnoreWhitespaces(chunk1, chunk2))
    } else {
      if (skipLastNewline) {
        if (StringUtil.equals(chunk1, chunk2)) return
        if (StringUtil.equals(stripNewline(chunk1), chunk2)) return
        if (StringUtil.equals(chunk1, stripNewline(chunk2))) return
        assertTrue(false)
      } else {
        assertTrue(StringUtil.equals(chunk1, chunk2))
      }
    }
  }

  //
  // Parsing
  //

  fun textToReadableFormat(text: CharSequence?): String {
    if (text == null) return "null"
    return "'" + text.toString().replace('\n', '*').replace('\t', '+') + "'"
  }

  fun parseSource(string: CharSequence): String = string.toString().replace('_', '\n')

  fun parseMatching(matching: String): BitSet {
    val set = BitSet()
    matching.filterNot { it == '.' }.forEachIndexed { i, c -> if (c != ' ') set.set(i) }
    return set
  }

  //
  // Misc
  //

  fun getLineCount(document: Document): Int {
    return Math.max(1, document.lineCount)
  }

  infix fun Int.until(a: Int): IntRange = this..a - 1

  //
  // AutoTests
  //

  fun doAutoTest(seed: Long, runs: Int, test: (DebugData) -> Unit) {
    RNG.setSeed(seed)

    var lastSeed: Long = -1
    val debugData = DebugData()

    for (i in 1..runs) {
      if (i % 1000 == 0) println(i)

      try {
        lastSeed = getCurrentSeed()

        test(debugData)
        debugData.reset()
      } catch (e: Throwable) {
        println("Seed: " + seed)
        println("Runs: " + runs)
        println("I: " + i)
        println("Current seed: " + lastSeed)
        debugData.dump()
        throw e
      }
    }
  }

  fun generateText(maxLength: Int, charCount: Int, predefinedChars: Map<Int, Char>): String {
    val length = RNG.nextInt(maxLength + 1)
    val builder = StringBuilder(length)

    for (i in 1..length) {
      val rnd = RNG.nextInt(charCount)
      val char = predefinedChars[rnd] ?: (rnd + 97).toChar()
      builder.append(char)
    }
    return builder.toString()
  }

  fun generateText(maxLength: Int): String {
    return generateText(maxLength, DEFAULT_CHAR_COUNT, DEFAULT_CHAR_TABLE)
  }

  fun getCurrentSeed(): Long {
    if (gotSeedException) return -1
    try {
      val seedField = RNG.javaClass.getDeclaredField("seed")
      seedField.isAccessible = true
      val seedFieldValue = seedField.get(RNG) as AtomicLong
      return seedFieldValue.get() xor 0x5DEECE66DL
    } catch (e: Exception) {
      gotSeedException = true
      System.err.println("Can't get random seed: " + e.message)
      return -1
    }
  }

  private fun stripNewline(text: CharSequence): CharSequence? {
    return when (StringUtil.endsWithChar(text, '\n') ) {
      true -> CharSequenceSubSequence(text, 0, text.length - 1)
      false -> null
    }
  }

  class DebugData() {
    private val data: MutableList<Pair<String, Any>> = ArrayList()

    fun put(key: String, value: Any) {
      data.add(Pair(key, value))
    }

    fun reset() {
      data.clear()
    }

    fun dump() {
      data.forEach { println(it.first + ": " + it.second) }
    }
  }

  //
  // Helpers
  //

  open class Trio<T : Any>(val data1: T, val data2: T, val data3: T) {
    companion object {
      fun <V : Any> from(f: (ThreeSide) -> V): Trio<V> = Trio(f(ThreeSide.LEFT), f(ThreeSide.BASE), f(ThreeSide.RIGHT))
    }

    fun <V : Any> map(f: (T) -> V): Trio<V> = Trio(f(data1), f(data2), f(data3))

    fun <V : Any> map(f: (T, ThreeSide) -> V): Trio<V> = Trio(f(data1, ThreeSide.LEFT), f(data2, ThreeSide.BASE), f(data3, ThreeSide.RIGHT))

    fun forEach(f: (T, ThreeSide) -> Unit): Unit {
      f(data1, ThreeSide.LEFT)
      f(data2, ThreeSide.BASE)
      f(data3, ThreeSide.RIGHT)
    }

    operator fun invoke(side: ThreeSide): T = side.select(data1, data2, data3) as T

    override fun toString(): String {
      return "($data1, $data2, $data3)"
    }

    override fun equals(other: Any?): Boolean {
      return other is Trio<*> && other.data1 == data1 && other.data2 == data2 && other.data3 == data3
    }

    override fun hashCode(): Int {
      return data1.hashCode() * 37 * 37 + data2.hashCode() * 37 + data3.hashCode()
    }
  }
}