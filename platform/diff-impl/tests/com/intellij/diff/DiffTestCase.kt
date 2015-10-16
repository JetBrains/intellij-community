/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import java.util.*
import java.util.concurrent.atomic.AtomicLong

public abstract class DiffTestCase : UsefulTestCase() {
  companion object {
    private val REGISTRY = Registry.get("diff.verify.iterable")

    private val DEFAULT_CHAR_COUNT = 12
    private val DEFAULT_CHAR_TABLE: Map<Int, Char> = {
      val map = HashMap<Int, Char>()
      listOf('\n', '\n', '\t', ' ', ' ', '.', '<', '!').forEachIndexed { i, c -> map.put(i, c) }
      map
    }()
  }

  public val RNG: Random = Random()
  private var gotSeedException = false

  private var oldRegistryValue: Boolean = false

  public val INDICATOR: ProgressIndicator = DumbProgressIndicator.INSTANCE
  public val MANAGER: ComparisonManagerImpl = ComparisonManagerImpl()


  override fun setUp() {
    super.setUp()
    oldRegistryValue = REGISTRY.asBoolean()
    REGISTRY.setValue(true)
  }

  override fun tearDown() {
    REGISTRY.setValue(oldRegistryValue)
    super.tearDown()
  }


  public fun assertEqualsCharSequences(chunk1: CharSequence, chunk2: CharSequence, ignoreSpaces: Boolean, skipLastNewline: Boolean) {
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

  public fun textToReadableFormat(text: CharSequence?): String {
    if (text == null) return "null"
    return "'" + text.toString().replace('\n', '*').replace('\t', '+') + "'"
  }

  public fun parseSource(string: CharSequence): String = string.toString().replace('_', '\n')

  public fun parseMatching(before: String, after: String): Couple<BitSet> {
    return Couple.of(parseMatching(before), parseMatching(after))
  }

  public fun parseMatching(matching: String): BitSet {
    val set = BitSet()
    matching.filterNot { it == '.' }.forEachIndexed { i, c -> if (c != ' ') set.set(i) }
    return set
  }

  //
  // Misc
  //

  public fun getLineCount(document: Document): Int {
    return Math.max(1, document.getLineCount())
  }

  public fun Int.until(a: Int): IntRange = this..a - 1

  //
  // AutoTests
  //

  public fun doAutoTest(seed: Long, runs: Int, test: (DebugData) -> Unit) {
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

  public fun generateText(maxLength: Int, charCount: Int, predefinedChars: Map<Int, Char>): String {
    val length = RNG.nextInt(maxLength + 1)
    val builder = StringBuilder(length)

    for (i in 1..length) {
      val rnd = RNG.nextInt(charCount)
      val char = predefinedChars.get(rnd) ?: (rnd + 97).toChar()
      builder.append(char)
    }
    return builder.toString()
  }

  public fun generateText(maxLength: Int): String {
    return generateText(maxLength, DEFAULT_CHAR_COUNT, DEFAULT_CHAR_TABLE)
  }

  public fun getCurrentSeed(): Long {
    if (gotSeedException) return -1
    try {
      val seedField = RNG.javaClass.getDeclaredField("seed")
      seedField.setAccessible(true)
      val seedFieldValue = seedField.get(RNG) as AtomicLong
      return seedFieldValue.get() xor 0x5DEECE66DL
    } catch (e: Exception) {
      gotSeedException = true
      System.err.println("Can't get random seed: " + e.getMessage())
      return -1
    }
  }

  private fun stripNewline(text: CharSequence): CharSequence? {
    return when (StringUtil.endsWithChar(text, '\n') ) {
      true -> CharSequenceSubSequence(text, 0, text.length() - 1)
      false -> null
    }
  }

  public class DebugData() {
    private val data: MutableList<Pair<String, Any>> = ArrayList<Pair<String, Any>>()

    public fun put(key: String, value: Any) {
      data.add(Pair(key, value))
    }

    public fun reset() {
      data.clear()
    }

    public fun dump() {
      data.forEach { println(it.first + ": " + it.second) }
    }
  }

  //
  // Helpers
  //

  public open class Trio<T: Any>(val data1: T, val data2: T, val data3: T) {
    companion object {
      public fun <V: Any> from(f: (ThreeSide) -> V): Trio<V> = Trio(f(ThreeSide.LEFT), f(ThreeSide.BASE), f(ThreeSide.RIGHT))
    }

    public fun <V: Any> map(f: (T) -> V): Trio<V> = Trio(f(data1), f(data2), f(data3))

    public fun <V: Any> map(f: (T, ThreeSide) -> V): Trio<V> = Trio(f(data1, ThreeSide.LEFT), f(data2, ThreeSide.BASE), f(data3, ThreeSide.RIGHT))

    public fun forEach(f: (T, ThreeSide) -> Unit): Unit {
      f(data1, ThreeSide.LEFT)
      f(data2, ThreeSide.BASE)
      f(data3, ThreeSide.RIGHT)
    }

    public fun invoke(side: ThreeSide): T = side.select(data1, data2, data3) as T

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