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

import com.intellij.diff.comparison.CancellationChecker
import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.text.CharSequenceSubSequence
import junit.framework.ComparisonFailure
import junit.framework.TestCase
import org.junit.Assert
import java.util.*
import java.util.concurrent.atomic.AtomicLong

abstract class DiffTestCase : TestCase() {
  private val DEFAULT_CHAR_COUNT = 12
  private val DEFAULT_CHAR_TABLE: List<String> = listOf("\n", "\n", "\t", " ", " ", ".", "<", "!")

  val RNG: Random = Random()
  private var gotSeedException = false

  val CANCELLATION: CancellationChecker = CancellationChecker.EMPTY
  val INDICATOR: ProgressIndicator = DumbProgressIndicator.INSTANCE
  val MANAGER: ComparisonManagerImpl = ComparisonManagerImpl()

  val chSmile = "\uD83D\uDE02"
  val chGun = "\uD83D\uDD2B"
  val chMan = "\uD83E\uDDD2"
  val chFace = "\uD83E\uDD2B"

  override fun setUp() {
    super.setUp()
    DiffIterableUtil.setVerifyEnabled(true)
  }

  override fun tearDown() {
    DiffIterableUtil.setVerifyEnabled(false)
    super.tearDown()
  }

  fun getTestName() = UsefulTestCase.getTestName(name, true)

  //
  // Misc
  //

  fun getLineCount(document: Document): Int {
    return DiffUtil.getLineCount(document)
  }

  fun createFilePath(path: String) = LocalFilePath(path, path.endsWith('/') || path.endsWith('\\'))

  //
  // AutoTests
  //

  fun doAutoTest(seed: Long, runs: Int, test: (DebugData) -> Unit) {
    RNG.setSeed(seed)

    var lastSeed: Long = -1
    val debugData = DebugData()

    for (i in 1..runs) {
      if (i % 1000 == 0 && !UsefulTestCase.IS_UNDER_TEAMCITY) println("Completed $i runs")

      try {
        lastSeed = getCurrentSeed()

        test(debugData)
        debugData.reset()
      }
      catch (e: Throwable) {
        println("Seed: " + seed)
        println("Runs: " + runs)
        println("I: " + i)
        println("Current seed: " + lastSeed)
        debugData.dump()
        throw e
      }
    }
  }

  fun generateText(maxLength: Int, charCount: Int, predefinedChars: List<String>): String {
    val length = RNG.nextInt(maxLength + 1)
    val builder = StringBuilder(length)

    for (i in 1..length) {
      val rnd = RNG.nextInt(charCount)
      val char = predefinedChars.getOrElse(rnd) { (rnd + 97).toChar() }
      builder.append(char)
    }
    return builder.toString()
  }

  fun generateText(maxLength: Int, useHighSurrogates: Boolean = false): String {
    var count = DEFAULT_CHAR_COUNT
    var table = DEFAULT_CHAR_TABLE
    if (useHighSurrogates) {
      count += 4
      table += listOf(chSmile, chFace, chGun, chMan)
    }
    return generateText(maxLength, count, table)
  }

  fun getCurrentSeed(): Long {
    if (gotSeedException) return -1
    try {
      val seedField = RNG.javaClass.getDeclaredField("seed")
      seedField.isAccessible = true
      val seedFieldValue = seedField.get(RNG) as AtomicLong
      return seedFieldValue.get() xor 0x5DEECE66DL
    }
    catch (e: Exception) {
      gotSeedException = true
      System.err.println("Can't get random seed: " + e.message)
      return -1
    }
  }

  class DebugData {
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

  open class Trio<out T>(val data1: T, val data2: T, val data3: T) {
    companion object {
      fun <V> from(f: (ThreeSide) -> V): Trio<V> = Trio(f(ThreeSide.LEFT), f(ThreeSide.BASE), f(ThreeSide.RIGHT))
    }

    fun <V> map(f: (T) -> V): Trio<V> = Trio(f(data1), f(data2), f(data3))

    fun <V> map(f: (T, ThreeSide) -> V): Trio<V> = Trio(f(data1, ThreeSide.LEFT), f(data2, ThreeSide.BASE), f(data3, ThreeSide.RIGHT))

    fun forEach(f: (T, ThreeSide) -> Unit) {
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
      var h = 0
      if (data1 != null) h = h * 31 + data1.hashCode()
      if (data2 != null) h = h * 31 + data2.hashCode()
      if (data3 != null) h = h * 31 + data3.hashCode()
      return h
    }
  }

  companion object {
    //
    // Assertions
    //

    fun assertTrue(actual: Boolean, message: String = "") {
      assertTrue(message, actual)
    }

    fun assertFalse(actual: Boolean, message: String = "") {
      assertFalse(message, actual)
    }

    fun assertEquals(expected: Any?, actual: Any?, message: String = "") {
      assertEquals(message, expected, actual)
    }

    fun assertEquals(expected: CharSequence?, actual: CharSequence?, message: String = "") {
      if (!StringUtil.equals(expected, actual)) throw ComparisonFailure(message, expected?.toString(), actual?.toString())
    }

    fun assertEqualsCharSequences(chunk1: CharSequence, chunk2: CharSequence, ignoreSpaces: Boolean, skipLastNewline: Boolean) {
      if (skipLastNewline && !ignoreSpaces) {
        assertTrue(StringUtil.equals(chunk1, chunk2) ||
                   StringUtil.equals(stripNewline(chunk1), chunk2) ||
                   StringUtil.equals(chunk1, stripNewline(chunk2)))
      }
      else {
        assertTrue(isEqualsCharSequences(chunk1, chunk2, ignoreSpaces))
      }
    }

    fun assertNotEqualsCharSequences(chunk1: CharSequence, chunk2: CharSequence, ignoreSpaces: Boolean, skipLastNewline: Boolean) {
      if (skipLastNewline && !ignoreSpaces) {
        assertTrue(!StringUtil.equals(chunk1, chunk2) ||
                   !StringUtil.equals(stripNewline(chunk1), chunk2) ||
                   !StringUtil.equals(chunk1, stripNewline(chunk2)))
      }
      else {
        assertFalse(isEqualsCharSequences(chunk1, chunk2, ignoreSpaces))
      }
    }

    fun isEqualsCharSequences(chunk1: CharSequence, chunk2: CharSequence, ignoreSpaces: Boolean): Boolean {
      if (ignoreSpaces) {
        return StringUtil.equalsIgnoreWhitespaces(chunk1, chunk2)
      }
      else {
        return StringUtil.equals(chunk1, chunk2)
      }
    }

    fun assertOrderedEquals(expected: Collection<*>, actual: Collection<*>, message: String = "") {
      UsefulTestCase.assertOrderedEquals(message, actual, expected)
    }

    fun assertSetsEquals(expected: BitSet, actual: BitSet, message: String = "") {
      val sb = StringBuilder(message)
      sb.append(": \"")
      for (i in 0..actual.length()) {
        sb.append(if (actual[i]) '-' else ' ')
      }
      sb.append('"')
      val fullMessage = sb.toString()

      Assert.assertEquals(fullMessage, expected, actual)
    }

    //
    // Parsing
    //

    fun textToReadableFormat(text: CharSequence?): String {
      if (text == null) return "null"
      return "\"" + text.toString().replace('\n', '*').replace('\t', '+') + "\""
    }

    fun parseSource(string: CharSequence): String = string.toString().replace('_', '\n')

    fun parseMatching(matching: String, text: Document): BitSet {
      val pattern = matching.filterNot { it == '.' }
      assertEquals(pattern.length, text.charsSequence.length)

      val set = BitSet()
      pattern.forEachIndexed { i, c -> if (c != ' ') set.set(i) }
      return set
    }

    fun parseLineMatching(matching: String, document: Document): BitSet {
      return parseLineMatching(matching, document.charsSequence)
    }

    fun parseLineMatching(matching: String, text: CharSequence): BitSet {
      assertEquals(matching.length, text.length)

      val lines1 = matching.split('_', '*')
      val lines2 = text.split('\n')
      assertEquals(lines1.size, lines2.size)
      for (i in 0..lines1.size - 1) {
        assertEquals(lines1[i].length, lines2[i].length, "line $i")
      }


      val set = BitSet()

      var index = 0
      var lineNumber = 0
      while (index < matching.length) {
        var end = matching.indexOfAny(listOf("_", "*"), index) + 1
        if (end == 0) end = matching.length

        val line = matching.subSequence(index, end)
        if (line.find { it != ' ' && it != '_' } != null) {
          assert(!line.contains(' '))
          set.set(lineNumber)
        }
        lineNumber++
        index = end
      }

      return set
    }


    private fun stripNewline(text: CharSequence): CharSequence? {
      return when (StringUtil.endsWithChar(text, '\n')) {
        true -> CharSequenceSubSequence(text, 0, text.length - 1)
        false -> null
      }
    }
  }
}