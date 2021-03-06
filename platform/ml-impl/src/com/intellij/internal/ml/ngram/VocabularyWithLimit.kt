// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml.ngram

import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel
import com.intellij.completion.ngram.slp.translating.Vocabulary
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.TestOnly
import java.io.Externalizable
import java.io.IOException
import java.io.ObjectInput
import java.io.ObjectOutput
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

class VocabularyWithLimit(var maxVocabularySize: Int,
                          nGramOrder: Int, maxSequenceSize: Int,
                          sequenceInitialSize: Int = SEQUENCE_INITIAL_SIZE) : Vocabulary(), Externalizable {

  companion object {
    internal val LOG: Logger = Logger.getInstance(VocabularyWithLimit::class.java)

    private const val SEQUENCE_INITIAL_SIZE: Int = 100
  }

  private val counter: AtomicInteger = AtomicInteger(1)

  val recent: NGramRecentTokens = NGramRecentTokens(maxSequenceSize)
  val recentSequence: NGramRecentTokensSequence = NGramRecentTokensSequence(maxSequenceSize, nGramOrder, sequenceInitialSize)

  /**
   * If token is unknown, it will be treated as "<unknownCharacter>"
   */
  fun toExistingIndices(token: List<String>): List<Int> {
    return token.mapNotNull { toExistingIndex(it) }
  }

  private fun toExistingIndex(token: String): Int? {
    return wordIndices[token] ?: wordIndices[unknownCharacter]
  }

  /**
   * If token is unknown, vocabulary will learn it.
   *
   * If after learning new token vocabulary size exceeds maxSize threshold, the oldest token will be forgotten.
   */
  fun toIndicesWithLimit(token: List<String>, model: NGramModel): List<Int> {
    val indices = token.map { toIndexWithLimit(it, model) }

    recentSequence.addWithLimit(model, indices.last())
    updateRecentTokens(token)
    return indices
  }

  private fun toIndexWithLimit(token: String, model: NGramModel): Int {
    var index: Int? = wordIndices[token]
    if (index == null) {
      index = counter.getAndIncrement()
      wordIndices[token] = index

      if (recent.size() >= maxVocabularySize) {
        val (toRemove, latestAppearance) = trimRecentTokensSize()
        recentSequence.forgetUntil(model, if (recent.size() > 0) recent.lastIndex() - latestAppearance else 0)
        for (tokenToRemove in toRemove) {
          wordIndices.remove(tokenToRemove)
        }
      }
    }
    return index
  }

  private fun trimRecentTokensSize(): Pair<ArrayList<String>, Int> {
    val toRemove: ArrayList<String> = arrayListOf()
    var latestAppearance = 0
    while (recent.size() >= maxVocabularySize) {
      val (token, idx) = recent.removeEldest()
      latestAppearance = max(latestAppearance, idx)
      toRemove.add(token)
    }
    return toRemove to latestAppearance
  }

  private fun updateRecentTokens(tokens: List<String>) {
    if (LOG.isDebugEnabled) {
      assertUpdateIsIncremental(tokens)
    }

    if (tokens.isNotEmpty()) {
      recent.update(tokens.last())
    }
  }

  private fun assertUpdateIsIncremental(tokens: List<String>) {
    for (i in 0..tokens.size - 2) {
      assert(recent.contains(tokens[i])) {
        "Cannot find previous token in recent: ${tokens[i]} in $tokens"
      }
    }
  }

  @Throws(IOException::class)
  override fun writeExternal(out: ObjectOutput) {
    out.writeInt(maxVocabularySize)
    out.writeInt(counter.get())
    recent.writeExternal(out)
    recentSequence.writeExternal(out)

    out.writeInt(wordIndices.size)
    for ((token, code) in wordIndices) {
      out.writeObject(token)
      out.writeInt(code)
    }
  }

  @Throws(IOException::class)
  override fun readExternal(ins: ObjectInput) {
    maxVocabularySize = ins.readInt()
    counter.set(ins.readInt())
    recent.readExternal(ins)
    recentSequence.readExternal(ins)

    val wordsSize = ins.readInt()
    for (i in 0 until wordsSize) {
      val token = ins.readObject() as String
      val code = ins.readInt()
      wordIndices[token] = code
    }
  }
}

/**
 * Stores recent tokens (recent) with an id of it last appearance
 *
 * Last token appearance is used to find a minimum sequence which have to be forgotten together with the token.
 */
class NGramRecentTokens(private val maxSequenceSize: Int) : Externalizable {
  private var maxTokenIndex: Int = Int.MAX_VALUE - 1

  private val nextTokenIndex: AtomicInteger = AtomicInteger(1)
  private val recent: LinkedHashMap<String, Int> = LinkedHashMap()

  @Synchronized
  fun update(token: String) {
    if (recent.containsKey(token)) {
      recent.remove(token)
    }
    recent[token] = nextTokenIndex.getAndIncrement()

    if (nextTokenIndex.get() > maxTokenIndex) {
      // shift token index to avoid token index overflow
      resetTokenIndex()
    }
  }

  private fun resetTokenIndex() {
    val first = nextTokenIndex.get() - maxSequenceSize
    var newLast = 0
    for ((key, value) in recent) {
      val newIdx = max(value - first + 1, 0)
      recent[key] = newIdx
      newLast = max(newLast, newIdx)
    }
    nextTokenIndex.set(newLast + 1)
  }

  @Synchronized
  fun removeEldest(): Pair<String, Int> {
    val (token, id) = recent.iterator().next()
    recent.remove(token)
    return token to id
  }

  @Synchronized
  fun contains(token: String): Boolean = recent.contains(token)

  @Synchronized
  fun lastIndex(): Int = nextTokenIndex.get() - 1

  @Synchronized
  fun size(): Int = recent.size

  @Synchronized
  @Throws(IOException::class)
  override fun writeExternal(out: ObjectOutput) {
    out.writeInt(nextTokenIndex.get())
    out.writeInt(recent.size)
    for (entry in recent) {
      out.writeObject(entry.key)
      out.writeInt(entry.value)
    }
  }

  @Synchronized
  @Throws(IOException::class)
  override fun readExternal(ins: ObjectInput) {
    nextTokenIndex.set(ins.readInt())

    val recentSize = ins.readInt()
    for (i in 0 until recentSize) {
      recent[ins.readObject() as String] = ins.readInt()
    }
  }

  @TestOnly
  fun setMaxTokenIndex(newMax: Int) {
    maxTokenIndex = newMax
  }

  @TestOnly
  fun getRecentTokens(): List<Pair<String, Int>> {
    val recentTokens = arrayListOf<Pair<String, Int>>()
    for ((key, value) in recent) {
      recentTokens.add(key to value)
    }
    return recentTokens
  }
}