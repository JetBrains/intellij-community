// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml.ngram

import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path

object NGramModelSerializer {

  @Throws(IOException::class)
  fun saveNGrams(path: Path, runner: NGramIncrementalModelRunner) {
    ObjectOutputStream(Files.newOutputStream(path)).use { oos ->
      oos.writeDouble(runner.lambda)

      val vocabulary = runner.vocabulary as VocabularyWithLimit
      oos.writeInt(vocabulary.maxVocabularySize)
      oos.writeInt(vocabulary.recentSequence.maxSequenceLength)
      vocabulary.writeExternal(oos)

      val counter = (runner.model as NGramModel).counter as ArrayTrieCounter
      counter.writeExternal(oos)

      oos.writeInt(runner.prevTokens.size)
      for (file in runner.prevTokens) {
        oos.writeObject(file)
      }
    }
  }


  @Throws(IOException::class)
  fun loadNGrams(path: Path?, nGramLength: Int): NGramIncrementalModelRunner {
    if (path != null && Files.exists(path)) {
      return ObjectInputStream(Files.newInputStream(path)).use { ois ->
        val lambda = ois.readDouble()

        val maxVocabularySize = ois.readInt()
        val maxSequenceSize = ois.readInt()
        val vocabulary = VocabularyWithLimit(maxVocabularySize, nGramLength, maxSequenceSize)
        vocabulary.readExternal(ois)

        val counter = ArrayTrieCounter()
        counter.readExternal(ois)
        val runner = NGramIncrementalModelRunner.createModelRunner(nGramLength, lambda, counter, vocabulary)
        val prevFilesSize = ois.readInt()
        for (i in 0 until prevFilesSize) {
          runner.prevTokens.add(ois.readObject() as String)
        }
        return@use runner
      }
    }
    return NGramIncrementalModelRunner.createNewModelRunner(nGramLength)
  }
}